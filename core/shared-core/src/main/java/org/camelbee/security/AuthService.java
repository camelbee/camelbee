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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guards the CamelBee UI and REST API behind a single configured credential.
 *
 * <p><strong>Why a signed token rather than a session map.</strong> CamelBee's reason to exist is
 * reaching applications you cannot attach a terminal to, and those usually run more than one
 * replica. A {@code Map} of sessions lives in one JVM, so a session created on pod A is unknown to
 * pod B and the next poll - two seconds later - logs the user out at random. A token that carries
 * its own signed expiry needs no server state at all: any replica can verify it, nothing has to be
 * swept or bounded, and there is no external store to deploy.
 *
 * <p>The signing key is derived from the configured password, so every replica derives the same key
 * without a second property to configure. Changing the password invalidates outstanding tokens,
 * which is the behaviour you want anyway.
 *
 * <p><strong>What this is not.</strong> One shared credential is a gate, not an identity: there is
 * no per-user audit and no revocation before expiry. That is the right weight for a debugging tool,
 * and applications needing real SSO should leave this off and put the host framework's own security
 * in front of {@code /camelbee} instead - the core READMEs describe how.
 *
 * <p><strong>Credentials travel in the clear without TLS.</strong> The login request carries the
 * password and every later request carries a bearer token; both are readable by anything on the
 * path unless the endpoint is served over HTTPS.
 */
public class AuthService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /** Separates the payload from its signature. Not valid base64url, so it cannot occur in either. */
  private static final String TOKEN_SEPARATOR = ".";

  /**
   * Bounds how long a token can be refreshed for, however active the user is. The idle timeout stops
   * an abandoned tab; this stops a tab left open for a fortnight from holding a valid credential.
   */
  private static final long ABSOLUTE_LIFETIME_MILLIS = 8L * 60 * 60 * 1000;

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final boolean enabled;
  private final String username;
  private final String password;
  private final long sessionTimeoutMillis;
  private final byte[] signingKey;

  /**
   * Constructor.
   *
   * @param enabled              whether the endpoints require authentication at all.
   * @param username             the login name; not a secret.
   * @param password             the password. Blank generates one and logs it, so that enabling
   *                             authentication never means shipping a known credential.
   * @param sessionTimeoutMillis how long a token stays valid without further requests.
   */
  public AuthService(boolean enabled, String username, String password, long sessionTimeoutMillis) {
    this.enabled = enabled;
    this.username = username == null || username.isBlank() ? "camelbee" : username;
    this.sessionTimeoutMillis = sessionTimeoutMillis;

    if (!enabled) {
      LOGGER.warn("CamelBee authentication is DISABLED (camelbee.auth-enabled=false). The UI and "
          + "REST API are reachable by anything that can reach this port, and the API is not "
          + "read-only - a caller can enable tracing and read message bodies.");
      this.password = "";
      this.signingKey = new byte[0];
      return;
    }

    /*
     A generated password rather than a default one. A published default - camelbee/camelbee in a
     README - is worse than no authentication at all: it protects nobody, because the value is in
     the documentation, while looking like it does.
     */
    if (password == null || password.isBlank()) {
      this.password = UUID.randomUUID().toString();
      LOGGER.warn("CamelBee UI is protected. Generated password for user '{}': {}",
          this.username, this.password);
      LOGGER.warn("Set camelbee.password (or CAMELBEE_PASSWORD) to use your own. Note that each "
          + "replica generates its own, so a multi-instance deployment must configure one.");
    } else {
      this.password = password;
    }

    this.signingKey = deriveKey(this.password);
  }

  /** A service that lets everything through, for {@code camelbee.auth-enabled=false}. */
  public static AuthService disabled() {
    return new AuthService(false, null, null, 0);
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Checks a login.
   *
   * @param candidateUser     the supplied user name.
   * @param candidatePassword the supplied password.
   * @return true when both match.
   */
  public boolean authenticate(String candidateUser, String candidatePassword) {
    if (!enabled) {
      return true;
    }
    if (candidateUser == null || candidatePassword == null) {
      return false;
    }
    // Constant-time on both, so neither the name nor the password leaks through response timing.
    return constantTimeEquals(username, candidateUser)
        && constantTimeEquals(password, candidatePassword);
  }

  /**
   * Issues a token valid for one idle window.
   *
   * @return the token to hand back to the caller.
   */
  public String issueToken() {
    return issueToken(System.currentTimeMillis());
  }

  String issueToken(long now) {
    return sign(now + sessionTimeoutMillis, now + ABSOLUTE_LIFETIME_MILLIS);
  }

  /**
   * Verifies a token and, if it is still valid, returns a refreshed one.
   *
   * <p>The refresh is what turns a fixed expiry into an idle timeout: an active caller keeps getting
   * a new window, and one that stops calling expires. The UI polls every couple of seconds while the
   * debugger is open, so "active" needs no separate definition.
   *
   * @param token the bearer token, possibly null.
   * @return the refreshed token, or empty when the token is missing, tampered with or expired.
   */
  public Optional<String> verifyAndRefresh(String token) {
    return verifyAndRefresh(token, System.currentTimeMillis());
  }

  Optional<String> verifyAndRefresh(String token, long now) {
    if (!enabled) {
      return Optional.of("");
    }
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }

    final String[] parts = token.split("\\" + TOKEN_SEPARATOR);
    if (parts.length != 3) {
      return Optional.empty();
    }

    final long expiresAt;
    final long absoluteDeadline;
    try {
      expiresAt = Long.parseLong(new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8));
      absoluteDeadline = Long.parseLong(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }

    // Signature first: an unsigned token's claims mean nothing, so they must not be acted on - not
    // even to decide whether it has expired.
    if (!constantTimeEquals(sign(expiresAt, absoluteDeadline), token)) {
      return Optional.empty();
    }
    if (now >= expiresAt || now >= absoluteDeadline) {
      return Optional.empty();
    }

    // Refreshed, but never past the absolute deadline the original token fixed.
    return Optional.of(sign(Math.min(now + sessionTimeoutMillis, absoluteDeadline), absoluteDeadline));
  }

  private String sign(long expiresAt, long absoluteDeadline) {
    final String payload = ENCODER.encodeToString(String.valueOf(expiresAt).getBytes(StandardCharsets.UTF_8))
        + TOKEN_SEPARATOR
        + ENCODER.encodeToString(String.valueOf(absoluteDeadline).getBytes(StandardCharsets.UTF_8));
    return payload + TOKEN_SEPARATOR + ENCODER.encodeToString(hmac(payload));
  }

  private byte[] hmac(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      // Both failures are impossible on a working JRE: HmacSHA256 is required by the platform and
      // the key is never empty when enabled. Refusing to issue is the only safe response.
      throw new IllegalStateException("Cannot sign CamelBee session token", e);
    }
  }

  /**
   * Derives the signing key from the password, so replicas agree without another property.
   *
   * <p>Salted so the key is not simply the digest of the password: it is never transmitted, but a
   * bare SHA-256 of a password is a value worth not producing.
   */
  private static byte[] deriveKey(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update("camelbee-session-v1".getBytes(StandardCharsets.UTF_8));
      return digest.digest(password.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        actual.getBytes(StandardCharsets.UTF_8));
  }
}
