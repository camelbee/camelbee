/*
 * Copyright 2023 Rahmi Ege Karaosmanoglu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camelbee.masking;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Redacts sensitive values out of traced headers and bodies, at the moment they are captured.
 *
 * <p><strong>Capture, not render.</strong> The traced {@code Message} is served over HTTP and
 * written to the structured log, so a value that reaches it is already disclosed. Masking therefore
 * happens inside {@code ExchangeUtils}, which is the only place headers and bodies are read, rather
 * than anywhere further down.
 *
 * <p><strong>What this is and is not.</strong> Header masking is exact: the key is known, so a
 * configured key is always redacted. Body masking is <em>best effort</em> pattern matching over
 * JSON, XML and form-encoded shapes - it cannot know the format for certain, and it cannot redact a
 * field nobody configured. Treat it as defence in depth. The only guarantee available is to stop
 * capturing bodies at all, which {@code camelbee.tracer-body-enabled=false} does.
 */
public final class Masker {

  /** What a redacted value is replaced with. Deliberately not the original length. */
  public static final String MASK = "***";

  /**
   * Keys masked unless the application configures its own list. Chosen to be the things whose
   * disclosure is immediately damaging rather than an exhaustive catalogue - no default list can be
   * exhaustive, which is why the list is configurable.
   */
  public static final List<String> DEFAULT_KEYS = List.of(
      "password", "passwd", "secret", "token", "authorization", "auth",
      "apikey", "accesskey", "privatekey", "credential",
      "creditcard", "cardnumber", "cardno", "cvv", "cvc",
      "iban", "ssn", "pin", "otp");

  /** Matching ignores case and these separators, so "X-Api-Key" matches a configured "apikey". */
  private static final Pattern SEPARATORS = Pattern.compile("[-_.\\s]");

  private final boolean enabled;
  private final Set<String> normalisedKeys;
  private final Pattern jsonPattern;
  private final Pattern xmlPattern;
  private final Pattern formPattern;

  /**
   * Constructor.
   *
   * @param enabled whether to mask at all.
   * @param keys    the key names to redact; blank entries are ignored.
   */
  public Masker(boolean enabled, List<String> keys) {
    this.enabled = enabled;
    this.normalisedKeys = keys.stream()
        .map(Masker::normalise)
        .filter(key -> !key.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));

    if (!enabled || normalisedKeys.isEmpty()) {
      this.jsonPattern = null;
      this.xmlPattern = null;
      this.formPattern = null;
      return;
    }

    /*
     One alternation for all keys rather than a pattern per key: bodies can be large and this walks
     them once. \w* on either side so a configured "password" also catches "userPassword" and
     "password_confirmation", matching how header keys are compared.
     */
    final String keyAlternation = normalisedKeys.stream()
        .map(Pattern::quote)
        .collect(Collectors.joining("|"));
    final String keyExpression = "\\w*(?:" + keyAlternation + ")\\w*";

    // "key" : "value"  |  "key" : 1234  - the value group is replaced, the key left intact
    this.jsonPattern = Pattern.compile(
        "(\"" + keyExpression + "\"\\s*:\\s*)(\"(?:\\\\.|[^\"\\\\])*\"|-?\\d+(?:\\.\\d+)?|true|false|null)",
        Pattern.CASE_INSENSITIVE);

    // <key ...>value</key>, including namespaced elements
    this.xmlPattern = Pattern.compile(
        "(<\\s*(?:\\w+:)?" + keyExpression + "\\b[^>]*>)([^<]*)(</\\s*(?:\\w+:)?" + keyExpression + "\\s*>)",
        Pattern.CASE_INSENSITIVE);

    // key=value in a form body or query string, terminated by & or whitespace
    this.formPattern = Pattern.compile(
        "(\\b" + keyExpression + "=)([^&\\s]*)",
        Pattern.CASE_INSENSITIVE);
  }

  /** A masker that redacts {@link #DEFAULT_KEYS}. Used until configuration is applied. */
  public static Masker withDefaults() {
    return new Masker(true, DEFAULT_KEYS);
  }

  /** A masker that redacts nothing. */
  public static Masker disabled() {
    return new Masker(false, List.of());
  }

  /**
   * Parses a comma-separated key list, falling back to {@link #DEFAULT_KEYS} when blank.
   *
   * @param configured the raw property value, possibly null or blank.
   * @return the keys to mask.
   */
  public static List<String> parseKeys(String configured) {
    if (configured == null || configured.isBlank()) {
      return DEFAULT_KEYS;
    }
    return Arrays.stream(configured.split(","))
        .map(String::trim)
        .filter(key -> !key.isEmpty())
        .toList();
  }

  private static String normalise(String value) {
    return SEPARATORS.matcher(value).replaceAll("").toLowerCase();
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Whether a header with this name should have its value redacted.
   *
   * <p>Exact in the sense that matters: the key is known here, so no guessing about format is
   * involved. Compared with separators removed and case ignored, so {@code X-Api-Key},
   * {@code api_key} and {@code apikey} are all caught by one configured entry.
   *
   * @param headerName the header key, possibly null.
   * @return true when the value must not be recorded.
   */
  public boolean isSensitiveKey(String headerName) {
    if (!enabled || headerName == null) {
      return false;
    }
    final String candidate = normalise(headerName);
    return normalisedKeys.stream().anyMatch(candidate::contains);
  }

  /**
   * Redacts configured keys out of a body, whatever shape it appears to be in.
   *
   * <p>Best effort by nature - see the class javadoc. A body that is neither JSON, XML nor
   * form-encoded is returned unchanged, because there is nothing reliable to key off.
   *
   * @param body the captured body, possibly null.
   * @return the body with configured values replaced by {@link #MASK}.
   */
  public String maskBody(String body) {
    if (!enabled || body == null || body.isEmpty() || jsonPattern == null) {
      return body;
    }

    String masked = jsonPattern.matcher(body).replaceAll(match -> Matcher.quoteReplacement(
        match.group(1) + (match.group(2).startsWith("\"") ? "\"" + MASK + "\"" : MASK)));

    masked = xmlPattern.matcher(masked)
        .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + MASK + match.group(3)));

    masked = formPattern.matcher(masked)
        .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + MASK));

    return masked;
  }
}
