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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Redaction is the one feature here where a silent failure is worse than no feature at all: a mask
 * that quietly misses gives false confidence about what a traced body contains. So these tests are
 * written to catch a miss, not to confirm a hit - most of them assert the secret is ABSENT from the
 * output rather than that some expected string is present.
 */
class MaskerTest {

  private static final Masker DEFAULTS = Masker.withDefaults();

  @Nested
  @DisplayName("header keys")
  class HeaderKeys {

    @Test
    @DisplayName("redacts the obvious ones")
    void redactsKnownKeys() {
      assertThat(DEFAULTS.isSensitiveKey("password")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("Authorization")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("token")).isTrue();
    }

    @Test
    @DisplayName("ignores case and separators, so X-Api-Key matches a configured apikey")
    void ignoresCaseAndSeparators() {
      assertThat(DEFAULTS.isSensitiveKey("X-Api-Key")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("api_key")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("APIKEY")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("Credit-Card")).isTrue();
    }

    @Test
    @DisplayName("matches keys that merely contain a configured one")
    void matchesSubstrings() {
      assertThat(DEFAULTS.isSensitiveKey("userPassword")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("oldPasswordConfirm")).isTrue();
    }

    @Test
    @DisplayName("leaves ordinary headers alone")
    void leavesOrdinaryHeadersAlone() {
      assertThat(DEFAULTS.isSensitiveKey("Content-Type")).isFalse();
      assertThat(DEFAULTS.isSensitiveKey("CamelHttpMethod")).isFalse();
      assertThat(DEFAULTS.isSensitiveKey("breadcrumbId")).isFalse();
    }

    @Test
    @DisplayName("null and disabled are handled without throwing")
    void nullAndDisabled() {
      assertThat(DEFAULTS.isSensitiveKey(null)).isFalse();
      assertThat(Masker.disabled().isSensitiveKey("password")).isFalse();
    }
  }

  @Nested
  @DisplayName("json bodies")
  class JsonBodies {

    @Test
    @DisplayName("redacts a string value without touching the key")
    void redactsStringValue() {
      String masked = DEFAULTS.maskBody("{\"user\":\"ege\",\"password\":\"hunter2\"}");

      assertThat(masked).doesNotContain("hunter2");
      assertThat(masked).contains("\"password\"");
      assertThat(masked).contains("\"user\":\"ege\"");
    }

    @Test
    @DisplayName("redacts numeric and boolean values too")
    void redactsNonStringValues() {
      String masked = DEFAULTS.maskBody("{\"cardNumber\":4111111111111111,\"pin\":1234}");

      assertThat(masked).doesNotContain("4111111111111111");
      assertThat(masked).doesNotContain("1234");
    }

    @Test
    @DisplayName("survives whitespace and pretty printing")
    void survivesFormatting() {
      String masked = DEFAULTS.maskBody("{\n  \"password\" : \"hunter2\"\n}");

      assertThat(masked).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("redacts every occurrence, not just the first")
    void redactsEveryOccurrence() {
      String masked = DEFAULTS.maskBody(
          "[{\"password\":\"one\"},{\"password\":\"two\"},{\"token\":\"three\"}]");

      assertThat(masked).doesNotContain("one").doesNotContain("two").doesNotContain("three");
    }

    @Test
    @DisplayName("a value containing an escaped quote does not break out of the match")
    void handlesEscapedQuotes() {
      String masked = DEFAULTS.maskBody("{\"password\":\"hun\\\"ter2\",\"user\":\"ege\"}");

      assertThat(masked).doesNotContain("hun\\\"ter2");
      assertThat(masked).contains("ege");
    }

    @Test
    @DisplayName("leaves a body with nothing sensitive byte-identical")
    void leavesCleanBodiesAlone() {
      String body = "{\"name\":\"Coltrane\",\"instrument\":\"Sax\"}";

      assertThat(DEFAULTS.maskBody(body)).isEqualTo(body);
    }
  }

  @Nested
  @DisplayName("other shapes")
  class OtherShapes {

    @Test
    @DisplayName("redacts xml elements, including namespaced ones")
    void redactsXml() {
      String masked = DEFAULTS.maskBody("<user><name>ege</name><password>hunter2</password></user>");
      assertThat(masked).doesNotContain("hunter2").contains("ege");

      String ns = DEFAULTS.maskBody("<ns:credential>abc</ns:credential>");
      assertThat(ns).doesNotContain("abc");
    }

    @Test
    @DisplayName("redacting an xml attribute does not swallow the rest of the document")
    void boundsAttributeValues() {
      // an attribute value has no '&' or whitespace before the end of the body, so an unbounded
      // value pattern consumed everything after it: '<user password=***' and the element, its text
      // and its closing tag were simply gone from the trace.
      String masked = DEFAULTS.maskBody("<user password=\"hunter2\">ege</user>");

      assertThat(masked).doesNotContain("hunter2");
      assertThat(masked).isEqualTo("<user password=\"***\">ege</user>");
    }

    @Test
    @DisplayName("redacts form-encoded and query-string values")
    void redactsFormEncoded() {
      String masked = DEFAULTS.maskBody("user=ege&password=hunter2&remember=true");

      assertThat(masked).doesNotContain("hunter2");
      assertThat(masked).contains("user=ege");
      assertThat(masked).contains("remember=true");
    }

    @Test
    @DisplayName("plain text with no recognisable shape is returned unchanged")
    void plainTextUnchanged() {
      // documents the limit honestly: there is nothing to key off, so nothing is claimed
      String body = "the password is hunter2";

      assertThat(DEFAULTS.maskBody(body)).isEqualTo(body);
    }

    @Test
    @DisplayName("null and empty bodies are handled")
    void nullAndEmpty() {
      assertThat(DEFAULTS.maskBody(null)).isNull();
      assertThat(DEFAULTS.maskBody("")).isEmpty();
    }
  }

  @Nested
  @DisplayName("key matching")
  class KeyMatching {

    @Test
    @DisplayName("a configured key matches a whole word, not a substring of a longer one")
    void matchesWholeWordsOnly() {
      // 'auth' inside 'author', 'secret' inside 'secretary', 'pin' inside 'shipping'. These are
      // ordinary business fields: redacting them protects nothing and destroys exactly the data the
      // trace exists to show, which is a worse failure here than a miss would be elsewhere.
      assertThat(DEFAULTS.isSensitiveKey("Author")).isFalse();
      assertThat(DEFAULTS.isSensitiveKey("secretary")).isFalse();
      assertThat(DEFAULTS.isSensitiveKey("X-Shipping-Id")).isFalse();

      assertThat(DEFAULTS.maskBody("{\"author\":\"Coltrane\"}")).contains("Coltrane");
      assertThat(DEFAULTS.maskBody("{\"shippingAddress\":\"Main St 5\"}")).contains("Main St 5");
      assertThat(DEFAULTS.maskBody("<shippingCost>12.50</shippingCost>")).contains("12.50");
      assertThat(DEFAULTS.maskBody("author=Coltrane&shipping=express"))
          .contains("author=Coltrane").contains("shipping=express");
    }

    @Test
    @DisplayName("still matches a configured key that is one word of a compound key")
    void matchesCompoundKeys() {
      assertThat(DEFAULTS.isSensitiveKey("userPassword")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("password_confirmation")).isTrue();
      assertThat(DEFAULTS.maskBody("{\"userPassword\":\"hunter2\"}")).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("a body key obeys the same separator rules as a header key")
    void bodyKeysHonourSeparators() {
      // these leaked: the body patterns searched for the literal 'apikey', so the snake_case and
      // kebab-case spellings went out untouched while the header path redacted the very same name.
      // snake_case is the majority convention in JSON APIs, so this was not an edge case.
      assertThat(DEFAULTS.maskBody("{\"api_key\":\"abc\"}")).doesNotContain("abc");
      assertThat(DEFAULTS.maskBody("{\"access-key\":\"abc\"}")).doesNotContain("abc");
      assertThat(DEFAULTS.maskBody("{\"credit_card\":\"4111\"}")).doesNotContain("4111");
      assertThat(DEFAULTS.maskBody("<credit-card>4111</credit-card>")).doesNotContain("4111");
      assertThat(DEFAULTS.maskBody("api_key=abc&access-key=def"))
          .doesNotContain("abc").doesNotContain("def");
    }

    @Test
    @DisplayName("an acronym run splits where the next word starts")
    void splitsAcronyms() {
      assertThat(DEFAULTS.isSensitiveKey("APIKey")).isTrue();
      assertThat(DEFAULTS.isSensitiveKey("SSNValue")).isTrue();
    }
  }

  @Nested
  @DisplayName("line-oriented bodies")
  class LineOrientedBodies {

    @Test
    @DisplayName("redacts a raw http header block captured as a body")
    void redactsHttpHeaderBlock() {
      // the case that justifies handling 'key: value' at all - a proxied or dumped request carries
      // the single highest-value secret in the default list as plain text
      String masked = DEFAULTS.maskBody("GET /orders HTTP/1.1\nHost: api.example.com\n"
          + "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9\nAccept: application/json");

      assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
      assertThat(masked).contains("Host: api.example.com");
      assertThat(masked).contains("Accept: application/json");
    }

    @Test
    @DisplayName("redacts an indented yaml value")
    void redactsYaml() {
      String masked = DEFAULTS.maskBody("datasource:\n  username: ege\n  password: hunter2\n");

      assertThat(masked).doesNotContain("hunter2");
      assertThat(masked).contains("username: ege");
    }

    @Test
    @DisplayName("leaves a sentence that merely contains a colon alone")
    void leavesProseAlone() {
      // why the pattern is anchored to the start of a line rather than matching any colon: an
      // unanchored rule redacts the tail of ordinary prose, which is why payload tracers usually
      // decline to handle this shape at all
      String body = "please rotate the password: it expired last week";

      assertThat(DEFAULTS.maskBody(body)).isEqualTo(body);
    }

    @Test
    @DisplayName("a url is not mistaken for a key: value pair")
    void leavesUrlsAlone() {
      String body = "see http://host:8080/path for details";

      assertThat(DEFAULTS.maskBody(body)).isEqualTo(body);
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    @Test
    @DisplayName("a custom key list replaces the defaults entirely")
    void customKeysReplaceDefaults() {
      Masker custom = new Masker(true, List.of("nationalId"));

      assertThat(custom.isSensitiveKey("nationalId")).isTrue();
      // the caller asked for this list, so 'password' is genuinely not masked - documented, not a bug
      assertThat(custom.isSensitiveKey("password")).isFalse();
    }

    @Test
    @DisplayName("a blank property falls back to the defaults rather than masking nothing")
    void blankFallsBackToDefaults() {
      assertThat(Masker.parseKeys(null)).isEqualTo(Masker.DEFAULT_KEYS);
      assertThat(Masker.parseKeys("   ")).isEqualTo(Masker.DEFAULT_KEYS);
    }

    @Test
    @DisplayName("parses a comma-separated list, trimming as it goes")
    void parsesList() {
      assertThat(Masker.parseKeys(" password , nationalId ,,secret "))
          .containsExactly("password", "nationalId", "secret");
    }

    @Test
    @DisplayName("disabled masks nothing at all")
    void disabledMasksNothing() {
      String body = "{\"password\":\"hunter2\"}";

      assertThat(Masker.disabled().maskBody(body)).isEqualTo(body);
    }

    @Test
    @DisplayName("regex metacharacters in a configured key cannot break the pattern")
    void quotesConfiguredKeys() {
      Masker custom = new Masker(true, List.of("a.b(c"));

      // must not throw, and must not match everything
      assertThat(custom.maskBody("{\"user\":\"ege\"}")).contains("ege");
    }
  }
}
