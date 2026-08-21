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

import java.util.ArrayList;
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
 * <p><strong>One decision, four shapes.</strong> Finding a candidate key and deciding whether that
 * key is sensitive are separate jobs here, and only the first varies by format. The patterns below
 * locate <em>any</em> {@code key: value}-ish construct in JSON, XML, form encoding or a line-oriented
 * body; {@link #matchesConfiguredKey} then makes the single, shared decision - the same one
 * {@link #isSensitiveKey} makes for a header. Folding the key list into the patterns instead is what
 * previously let the two paths disagree: a header {@code api_key} was redacted while the identical
 * key in a JSON body was not, because the pattern searched for the literal {@code apikey}.
 *
 * <p><strong>What this is and is not.</strong> Header masking is exact: the key is known, so a
 * configured key is always redacted. Body masking is <em>best effort</em> pattern matching - it
 * cannot know the format for certain, and it cannot redact a field nobody configured. A body that
 * matches none of the four shapes is returned unchanged, and a sensitive key whose value is a nested
 * object or array is not descended into. Treat it as defence in depth. The only guarantee available
 * is to stop capturing bodies at all, which {@code camelbee.tracer-body-enabled=false} does.
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

  /** Separators removed when a configured key is normalised, so "api-key" and "apikey" are one key. */
  private static final Pattern SEPARATORS = Pattern.compile("[-_.\\s]");

  /**
   * Where one word of a key ends and the next begins: at a separator, at a camelCase hump, or at the
   * tail of an acronym ({@code APIKey} -> {@code API}, {@code Key}).
   *
   * <p>Splitting here rather than matching substrings is what stops {@code auth} redacting
   * {@code author}, {@code secret} redacting {@code secretary} and {@code pin} redacting
   * {@code shippingAddress} - all of which are ordinary business fields whose disclosure is not the
   * point, and whose removal from a trace destroys exactly the data this tool exists to show.
   */
  private static final Pattern TOKEN_BOUNDARY = Pattern.compile(
      "[-_.\\s]+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

  /** {@link #DEFAULT_KEYS} normalised once, so an instance can tell whether it was customised. */
  private static final Set<String> NORMALISED_DEFAULT_KEYS = DEFAULT_KEYS.stream()
      .map(Masker::normalise)
      .collect(Collectors.toCollection(LinkedHashSet::new));

  /**
   * A key as it appears in a document: word characters plus the separators a key may legitimately
   * contain. Deliberately matches ANY key - which one is sensitive is decided afterwards, in
   * {@link #matchesConfiguredKey}, where the full normalisation rules are available.
   */
  private static final String KEY = "[\\w.\\-]+";

  /** Nothing that could still be part of the key may follow it. */
  private static final String KEY_END = "(?![\\w.\\-])";

  /**
   * {@code scheme://user:password@host} - the password half of URI user-info. Group 2 is replaced.
   * Not covered by the key patterns below, because there is no key: the position carries the
   * meaning. Camel's own sanitizer handles this too, but only when it recognises the string as a
   * URI, so it is repeated here for the configured-key path.
   */
  private static final Pattern URI_USER_INFO = Pattern.compile("(://[^/:@\\s]+:)([^@/\\s]+)(@)");

  /** {@code "key" : "value"} | {@code "key" : 1234} - group 2 is the key, group 3 the value. */
  private static final Pattern JSON_FIELD = Pattern.compile(
      "(\"(" + KEY + ")\"\\s*:\\s*)"
          + "(\"(?:\\\\.|[^\"\\\\])*\"|-?\\d+(?:\\.\\d+)?|true|false|null)",
      Pattern.CASE_INSENSITIVE);

  /**
   * {@code <key ...>value</key>}, including namespaced elements. The closing tag is a backreference
   * to the opening one, so {@code <a>x</b>} is not mistaken for a field.
   */
  private static final Pattern XML_ELEMENT = Pattern.compile(
      "(<\\s*(?:" + KEY + ":)?(" + KEY + ")" + KEY_END + "[^>]*>)([^<]*)"
          + "(</\\s*(?:" + KEY + ":)?\\2\\s*>)",
      Pattern.CASE_INSENSITIVE);

  /**
   * {@code key=value} in a form body or query string, and {@code key="value"} in an XML or HTML
   * attribute.
   *
   * <p>The quoted alternatives are not decoration. A bare {@code [^&\s]*} value ends only at an
   * {@code &} or whitespace, and an attribute has neither before the end of the document - so
   * {@code <user password="x">ege</user>} redacted to {@code <user password=***}, taking the rest of
   * the body with it. That is content loss, not cosmetics; the same unbounded-value trap
   * {@code UriSanitizer} documents for URIs. Matching the closing quote bounds the value, and
   * {@link #maskValue} puts the quotes back so the document still parses.
   */
  private static final Pattern FORM_FIELD = Pattern.compile(
      "((?<![\\w.\\-])(" + KEY + ")=)(\"[^\"]*\"|'[^']*'|[^&\\s\"'<>]*)",
      Pattern.CASE_INSENSITIVE);

  /**
   * Same shape as {@link #FORM_FIELD}, but the value also stops at the characters that bound a URI
   * inside a route model's {@code toString()} - {@code ] } ,} - so masking a secret cannot swallow
   * the rest of a RecipientList or the closing bracket the UI parses. Kept separate rather than
   * tightening that one: a form BODY may legitimately contain those characters inside a value.
   */
  private static final Pattern URI_PARAMETER = Pattern.compile(
      "((?<![\\w.\\-])(" + KEY + ")=)([^&\\s\\]},]*)", Pattern.CASE_INSENSITIVE);

  /**
   * {@code key: value} occupying a whole line - an HTTP header block captured as a body, or YAML.
   *
   * <p>Deliberately anchored to the start of a line and bounded by its end, rather than matching a
   * colon anywhere. Free-floating {@code key\s*:\s*value} would redact the tail of ordinary prose
   * ("...rotate the password: it is stale") and is the reason payload tracers usually decline to
   * handle this shape at all. Anchoring keeps the two cases that genuinely occur - the highest-value
   * one being a raw {@code Authorization: Bearer ...} header inside a proxied or dumped body - while
   * matching nothing that reads as a sentence.
   *
   * <p>A quoted key cannot match, since {@code "} is not a key character, so pretty-printed JSON is
   * left entirely to {@link #JSON_FIELD}.
   */
  private static final Pattern LINE_FIELD = Pattern.compile(
      "(?m)^([ \\t]*(" + KEY + ")[ \\t]*:[ \\t]*)([^\\r\\n]*[^\\s])");

  private final boolean enabled;
  private final Set<String> normalisedKeys;
  private final boolean customKeys;

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
    this.customKeys = !this.normalisedKeys.equals(NORMALISED_DEFAULT_KEYS);
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

  /** Splits a key into its lower-cased words. See {@link #TOKEN_BOUNDARY}. */
  private static List<String> tokenise(String key) {
    final List<String> words = new ArrayList<>(4);
    for (String word : TOKEN_BOUNDARY.split(key)) {
      if (!word.isEmpty()) {
        words.add(word.toLowerCase());
      }
    }
    return words;
  }

  /**
   * Replaces a value, keeping any quotes that delimited it.
   *
   * <p>A redacted body is still read by a person, and often still parsed - dropping the quotes off a
   * JSON or attribute value turns valid output into a broken document for no gain.
   */
  private static String maskValue(String value) {
    if (value.length() >= 2) {
      final char quote = value.charAt(0);
      if ((quote == '"' || quote == '\'') && value.charAt(value.length() - 1) == quote) {
        return quote + MASK + quote;
      }
    }
    return MASK;
  }

  /**
   * The single rule for whether a key names something that must not be recorded.
   *
   * <p>The key is split into words and every run of ADJACENT words is compared against the
   * configured list. Comparing whole words is what keeps {@code author} and {@code secretary} out of
   * it; joining adjacent ones is what lets a single configured {@code apikey} still match
   * {@code X-Api-Key}, {@code api_key} and {@code apiKey}, which arrive as two words and have to be
   * rejoined to be recognised. A configured key therefore matches a compound field
   * ({@code userPassword}, {@code password_confirmation}) but not a longer unrelated word that
   * merely contains its letters.
   *
   * @param key the key to judge, possibly null.
   * @return true when the value belonging to this key must be replaced.
   */
  private boolean matchesConfiguredKey(String key) {
    if (key == null || key.isEmpty()) {
      return false;
    }

    final List<String> words = tokenise(key);

    for (int from = 0; from < words.size(); from++) {
      final StringBuilder run = new StringBuilder();
      for (int to = from; to < words.size(); to++) {
        run.append(words.get(to));
        if (normalisedKeys.contains(run.toString())) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Whether the application replaced {@link #DEFAULT_KEYS} with its own list.
   *
   * <p>Used by {@code UriSanitizer} to decide whether these keys apply to endpoint URIs as well as
   * to bodies and headers. The defaults are deliberately broad for a header - {@code auth} has to
   * catch a bare {@code Auth} - but that breadth is wrong for a URI, where it also matches ordinary
   * configuration such as {@code authMethod=Basic} and hides how an endpoint is set up. Camel's own
   * keyword list is curated for URI parameters and already covers the genuine secrets there, so the
   * defaults are not layered on top of it. A list the application configured explicitly is a
   * different matter: it was asked for, so it applies everywhere.
   *
   * @return true when the configured keys differ from the built-in defaults.
   */
  public boolean hasCustomKeys() {
    return customKeys;
  }

  /**
   * Whether a header with this name should have its value redacted.
   *
   * <p>Exact in the sense that matters: the key is known here, so no guessing about format is
   * involved. See {@link #matchesConfiguredKey} for the comparison rules - one configured
   * {@code apikey} covers {@code X-Api-Key}, {@code api_key} and {@code apiKey}.
   *
   * @param headerName the header key, possibly null.
   * @return true when the value must not be recorded.
   */
  public boolean isSensitiveKey(String headerName) {
    if (!enabled || headerName == null) {
      return false;
    }
    return matchesConfiguredKey(headerName);
  }

  /**
   * Redacts configured keys out of a body, whatever shape it appears to be in.
   *
   * <p>Best effort by nature - see the class javadoc. A body that is none of the four recognised
   * shapes is returned unchanged, because there is nothing reliable to key off.
   *
   * @param body the captured body, possibly null.
   * @return the body with configured values replaced by {@link #MASK}.
   */
  public String maskBody(String body) {
    if (!enabled || body == null || body.isEmpty() || normalisedKeys.isEmpty()) {
      return body;
    }

    String masked = replaceValue(JSON_FIELD, body,
        match -> match.group(1) + maskValue(match.group(3)));
    // element text is never quoted, but the closing tag has to be carried across
    masked = replaceValue(XML_ELEMENT, masked, match -> match.group(1) + MASK + match.group(4));
    masked = replaceValue(FORM_FIELD, masked, match -> match.group(1) + maskValue(match.group(3)));
    masked = replaceValue(LINE_FIELD, masked, match -> match.group(1) + maskValue(match.group(3)));

    return masked;
  }

  /**
   * Redacts configured keys out of an endpoint URI, plus the password half of any user-info.
   *
   * <p>Applied by {@code UriSanitizer} on top of Camel's own keyword list, so that a key the
   * application configured through {@code camelbee.masked-keys} is redacted in a URI as well as in a
   * body or a header. Unlike {@link #maskBody} the value ends at {@code ] } ,} as well as at
   * {@code &} and whitespace, because a URI here is usually embedded in a route model's
   * {@code toString()} and must not consume the surrounding structure.
   *
   * @param uri the URI, or a string containing one, possibly null.
   * @return the URI with configured parameter values and user-info passwords replaced.
   */
  public String maskUri(String uri) {
    if (!enabled || uri == null || uri.isEmpty()) {
      return uri;
    }

    // User-info is masked whenever masking is on at all: the password sits in a fixed position and
    // carries no key that could have been left out of the configured list.
    String masked = URI_USER_INFO.matcher(uri)
        .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + MASK + match.group(3)));

    if (!normalisedKeys.isEmpty()) {
      masked = replaceValue(URI_PARAMETER, masked, match -> match.group(1) + MASK);
    }

    return masked;
  }

  /**
   * Applies one shape's pattern, redacting only the matches whose key group is configured.
   *
   * <p>Every pattern here puts the key in group 2, so a match that is not sensitive is put back
   * verbatim and the surrounding document is untouched.
   */
  private String replaceValue(
      Pattern pattern, String input, java.util.function.Function<MatchResultView, String> redacted) {
    return pattern.matcher(input).replaceAll(match -> Matcher.quoteReplacement(
        matchesConfiguredKey(match.group(2))
            ? redacted.apply(match::group)
            : match.group()));
  }

  /** Just enough of a match to build a replacement from, so the lambdas above stay readable. */
  @FunctionalInterface
  private interface MatchResultView {

    String group(int index);
  }
}
