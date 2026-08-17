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

package org.camelbee.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.camelbee.masking.Masker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The assertions here are deliberately of two kinds, because they guard two different failures.
 *
 * <p>The obvious one is disclosure: the secret must not appear in the output. The less obvious one -
 * and the reason this class exists at all rather than a direct call to Camel's sanitizer - is
 * <em>content loss</em>. Camel's value pattern ends only at {@code &} or end-of-string, so on the
 * bracketed strings the route model produces it swallows delimiters and, in the RecipientList case,
 * entire sibling endpoints. Every test below therefore also asserts what SURVIVED.
 */
class UriSanitizerTest {

  private final Masker masker = Masker.withDefaults();

  @Test
  @DisplayName("redacts a password in a plain endpoint URI")
  void redactsPasswordInPlainUri() {
    String sanitized = UriSanitizer.sanitize("http://api.internal?authMethod=Basic&authPassword=hunter2", masker);

    assertThat(sanitized).doesNotContain("hunter2");
    assertThat(sanitized).contains("authMethod=Basic");
  }

  @Test
  @DisplayName("keeps the wrapper intact - the UI parses the trailing bracket")
  void keepsWrapperIntact() {
    String sanitized = UriSanitizer.sanitize("To[http://api.internal?authPassword=hunter2]", masker);

    assertThat(sanitized).doesNotContain("hunter2");
    assertThat(sanitized).startsWith("To[").endsWith("]");
  }

  @Test
  @DisplayName("a RecipientList keeps every recipient - the regression this class exists for")
  void recipientListKeepsAllRecipients() {
    String sanitized = UriSanitizer.sanitize(
        "RecipientList[simple{http://a?password=hunter2,http://b?foo=1}]", masker);

    // Calling URISupport.sanitizeUri directly here deletes ",http://b?foo=1}]" outright.
    assertThat(sanitized).doesNotContain("hunter2");
    assertThat(sanitized).contains("http://b?foo=1");
    assertThat(sanitized).endsWith("}]");
  }

  @Test
  @DisplayName("nested wrappers survive")
  void nestedWrappersSurvive() {
    String sanitized = UriSanitizer.sanitize("DynamicTo[toD[http://h?password=hunter2]]", masker);

    assertThat(sanitized).doesNotContain("hunter2");
    assertThat(sanitized).startsWith("DynamicTo[toD[").endsWith("]]");
  }

  @Test
  @DisplayName("redacts the password half of URI user-info, keeping the user")
  void redactsUserInfoPassword() {
    String sanitized = UriSanitizer.sanitize("To[http://admin:pa55w0rd@api.internal/path]", masker);

    assertThat(sanitized).doesNotContain("pa55w0rd");
    assertThat(sanitized).contains("admin").contains("api.internal/path");
  }

  @Test
  @DisplayName("leaves a URI with no credentials completely untouched")
  void leavesCleanUriUntouched() {
    String clean = "From[file://inputdir?delay=1000&noop=true]";

    assertThat(UriSanitizer.sanitize(clean, masker)).isEqualTo(clean);
  }

  @Test
  @DisplayName("redacts a key that only the application configured")
  void redactsConfiguredKey() {
    Masker custom = new Masker(true, List.of("customerRef"));

    String sanitized = UriSanitizer.sanitize("To[http://h?customerRef=ACME-1&keep=yes]", custom);

    assertThat(sanitized).doesNotContain("ACME-1");
    assertThat(sanitized).contains("keep=yes").endsWith("]");
  }

  @Test
  @DisplayName("still applies Camel's own keyword list when the masker is disabled")
  void appliesCamelKeywordsWhenMaskerDisabled() {
    // camelbee.masking-enabled=false switches off CamelBee's redaction of bodies and headers. A
    // credential in a URI is a different disclosure, and Camel's own list still covers it.
    String sanitized = UriSanitizer.sanitize("To[http://h?password=hunter2]", Masker.disabled());

    assertThat(sanitized).doesNotContain("hunter2");
  }

  @Test
  @DisplayName("tolerates a null masker")
  void toleratesNullMasker() {
    assertThat(UriSanitizer.sanitize("To[http://h?password=hunter2]", null)).doesNotContain("hunter2");
  }

  @Test
  @DisplayName("null and empty pass through")
  void nullAndEmptyPassThrough() {
    assertThat(UriSanitizer.sanitize(null, masker)).isNull();
    assertThat(UriSanitizer.sanitize("", masker)).isEmpty();
  }

  @Test
  @DisplayName("is stable - sanitizing twice changes nothing")
  void isIdempotent() {
    // The topology and the traced message are sanitized independently and then compared by the UI,
    // so the operation has to be a function of the value alone.
    String once = UriSanitizer.sanitize("To[http://h?password=x&user=bob]", masker);

    assertThat(UriSanitizer.sanitize(once, masker)).isEqualTo(once);
  }

  @Test
  @DisplayName("the same URI sanitizes identically wrapped and bare - edge matching depends on it")
  void wrappedAndBareAgree() {
    String bare = UriSanitizer.sanitize("http://h?password=x&q=1", masker);
    String wrapped = UriSanitizer.sanitize("To[http://h?password=x&q=1]", masker);

    assertThat(wrapped).isEqualTo("To[" + bare + "]");
  }

  @Test
  @DisplayName("a secret in a RAW token is redacted whole")
  void redactsRawToken() {
    String sanitized = UriSanitizer.sanitize("To[http://h?password=RAW(se&cret!)]", masker);

    assertThat(sanitized).doesNotContain("se&cret!");
    assertThat(sanitized).endsWith("]");
  }
}
