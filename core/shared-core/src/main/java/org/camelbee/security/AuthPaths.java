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

import java.util.List;

/**
 * Which request paths the guard applies to, shared by all three runtimes so they cannot drift.
 *
 * <p>Two things must stay reachable without a token, for opposite reasons. The login endpoint,
 * because a caller has no token yet. And the UI shell - {@code index.html} and its assets - because
 * a browser has to load the application before it can show a login form. Serving the shell discloses
 * only that CamelBee is installed; every byte of data sits behind the paths listed here.
 */
public final class AuthPaths {

  public static final String AUTHORIZATION_HEADER = "Authorization";

  public static final String BEARER_PREFIX = "Bearer ";

  /** Carries the rolling token back, so an active caller's idle window keeps moving. */
  public static final String REFRESHED_TOKEN_HEADER = "X-CamelBee-Token";

  /**
   * The data endpoints. Everything here needs a token; anything not here does not.
   *
   * <p>Listed as prefixes rather than derived from the request by stripping a base path, so that a
   * new endpoint is protected only when someone adds it here deliberately - the opposite default
   * would silently expose a path that no longer matched a pattern.
   */
  private static final List<String> PROTECTED_PREFIXES = List.of(
      "/camelbee/routes",
      "/camelbee/messages",
      "/camelbee/tracer");

  private AuthPaths() {
  }

  /**
   * Whether a request path requires a token.
   *
   * @param path the request path, possibly null.
   * @return true when the path is one of the data endpoints.
   */
  public static boolean isProtected(String path) {
    if (path == null) {
      return false;
    }
    return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
  }

  /**
   * Extracts the token from an Authorization header.
   *
   * @param header the header value, possibly null.
   * @return the token, or null when the header is missing or not a bearer token.
   */
  public static String bearerToken(String header) {
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return header.substring(BEARER_PREFIX.length());
  }
}
