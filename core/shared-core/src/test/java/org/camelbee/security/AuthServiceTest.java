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

package org.camelbee.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The assertions that matter here are the negative ones.
 *
 * <p>A gate that lets the right person in is easy to write and easy to get accidentally right. The
 * failures worth testing are the ones that let the wrong person in - a tampered token, an expired
 * one, a token signed with a different password, a token whose claims were edited to extend it.
 * Each of those has a test below, and each would be a full authentication bypass.
 */
class AuthServiceTest {

  private static final long TIMEOUT = 120_000;

  private AuthService service() {
    return new AuthService(true, "camelbee", "s3cret", TIMEOUT);
  }

  @Test
  @DisplayName("accepts the configured credentials")
  void acceptsConfiguredCredentials() {
    assertThat(service().authenticate("camelbee", "s3cret")).isTrue();
  }

  @Test
  @DisplayName("rejects a wrong password, a wrong user, and nulls")
  void rejectsBadCredentials() {
    AuthService auth = service();

    assertThat(auth.authenticate("camelbee", "wrong")).isFalse();
    assertThat(auth.authenticate("someone", "s3cret")).isFalse();
    assertThat(auth.authenticate(null, null)).isFalse();
    assertThat(auth.authenticate("camelbee", "")).isFalse();
  }

  @Test
  @DisplayName("a freshly issued token verifies")
  void freshTokenVerifies() {
    AuthService auth = service();

    assertThat(auth.verifyAndRefresh(auth.issueToken())).isPresent();
  }

  @Test
  @DisplayName("rejects a missing or malformed token")
  void rejectsMalformedToken() {
    AuthService auth = service();

    assertThat(auth.verifyAndRefresh(null)).isEmpty();
    assertThat(auth.verifyAndRefresh("")).isEmpty();
    assertThat(auth.verifyAndRefresh("not-a-token")).isEmpty();
    assertThat(auth.verifyAndRefresh("a.b")).isEmpty();
    assertThat(auth.verifyAndRefresh("a.b.c.d")).isEmpty();
  }

  @Test
  @DisplayName("rejects a token whose signature has been altered")
  void rejectsTamperedSignature() {
    AuthService auth = service();
    String token = auth.issueToken();
    String[] parts = token.split("\\.");

    String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "A";

    assertThat(auth.verifyAndRefresh(tampered)).isEmpty();
  }

  @Test
  @DisplayName("rejects a token whose expiry was edited to extend it - the obvious forgery")
  void rejectsExtendedExpiry() {
    AuthService auth = service();
    long now = 1_000_000L;
    String token = auth.issueToken(now);
    String[] parts = token.split("\\.");

    // Re-encode a far-future expiry, keeping the original signature.
    String farFuture = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(String.valueOf(now + 999_999_999L).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String forged = farFuture + "." + parts[1] + "." + parts[2];

    assertThat(auth.verifyAndRefresh(forged, now)).isEmpty();
  }

  @Test
  @DisplayName("rejects a token issued under a different password")
  void rejectsTokenFromAnotherPassword() {
    String token = new AuthService(true, "camelbee", "old-password", TIMEOUT).issueToken();

    // Rotating the password must invalidate everything outstanding.
    assertThat(new AuthService(true, "camelbee", "new-password", TIMEOUT).verifyAndRefresh(token))
        .isEmpty();
  }

  @Test
  @DisplayName("a token expires once the idle window passes")
  void expiresAfterIdleWindow() {
    AuthService auth = service();
    long now = 1_000_000L;
    String token = auth.issueToken(now);

    assertThat(auth.verifyAndRefresh(token, now + TIMEOUT - 1)).isPresent();
    assertThat(auth.verifyAndRefresh(token, now + TIMEOUT)).isEmpty();
  }

  @Test
  @DisplayName("staying active keeps refreshing the window - this is what makes it an IDLE timeout")
  void refreshExtendsTheWindow() {
    AuthService auth = service();
    long now = 1_000_000L;

    String token = auth.issueToken(now);
    // Poll just before each expiry, well past the original window.
    for (int i = 1; i <= 10; i++) {
      long at = now + (TIMEOUT - 1) * i;
      Optional<String> refreshed = auth.verifyAndRefresh(token, at);
      assertThat(refreshed).as("refresh %d should still be valid", i).isPresent();
      token = refreshed.orElseThrow();
    }
  }

  @Test
  @DisplayName("but not past the absolute lifetime, however active the caller is")
  void refreshStopsAtTheAbsoluteDeadline() {
    AuthService auth = service();
    long now = 1_000_000L;
    String token = auth.issueToken(now);

    // Nine hours of continuous activity, in windows short enough never to idle out.
    long at = now;
    Optional<String> current = Optional.of(token);
    while (current.isPresent() && at < now + 9L * 60 * 60 * 1000) {
      at += TIMEOUT - 1;
      current = auth.verifyAndRefresh(current.orElseThrow(), at);
    }

    assertThat(current)
        .as("a tab left open indefinitely must not hold a valid credential for ever")
        .isEmpty();
  }

  @Test
  @DisplayName("a blank password is generated rather than left blank or defaulted")
  void blankPasswordIsGenerated() {
    AuthService auth = new AuthService(true, "camelbee", "  ", TIMEOUT);

    // The generated value is unknown to the caller by design, so assert the consequences: the
    // obvious guesses fail, and a token can still be issued.
    assertThat(auth.authenticate("camelbee", "")).isFalse();
    assertThat(auth.authenticate("camelbee", "camelbee")).isFalse();
    assertThat(auth.authenticate("camelbee", "  ")).isFalse();
    assertThat(auth.verifyAndRefresh(auth.issueToken())).isPresent();
  }

  @Test
  @DisplayName("two instances with generated passwords do not accept each other's tokens")
  void generatedPasswordsAreNotShared() {
    // The replica caveat, pinned: this is why a multi-instance deployment must configure one.
    AuthService first = new AuthService(true, "camelbee", null, TIMEOUT);
    AuthService second = new AuthService(true, "camelbee", null, TIMEOUT);

    assertThat(second.verifyAndRefresh(first.issueToken())).isEmpty();
  }

  @Test
  @DisplayName("two instances sharing a configured password DO accept each other's tokens")
  void configuredPasswordWorksAcrossReplicas() {
    // The reason the signing key is derived from the password: replicas agree with no extra config,
    // which is what makes this work behind a load balancer.
    AuthService podA = new AuthService(true, "camelbee", "shared", TIMEOUT);
    AuthService podB = new AuthService(true, "camelbee", "shared", TIMEOUT);

    assertThat(podB.verifyAndRefresh(podA.issueToken())).isPresent();
  }

  @Test
  @DisplayName("disabled lets everything through")
  void disabledAllowsEverything() {
    AuthService auth = AuthService.disabled();

    assertThat(auth.isEnabled()).isFalse();
    assertThat(auth.authenticate("anyone", "anything")).isTrue();
    assertThat(auth.verifyAndRefresh(null)).isPresent();
  }
}
