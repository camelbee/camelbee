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

package org.camelbee.http;

import java.util.List;

/**
 * Which request paths belong to the embedded UI's client-side router, shared by all runtimes so they
 * cannot drift.
 *
 * <p>The UI is a single-page application: {@code /camelbee/settings} exists only in the browser's
 * router, and the server has no file there. Navigating inside the UI never asks for it, but a reload,
 * a bookmark or a shared link does - and without the fallback this class describes, the runtime
 * answers 404 for a page the user is looking straight at.
 *
 * <p>The rule is deliberately narrow, because the alternative - "serve index.html for anything under
 * {@code /camelbee} that 404s" - would turn a mistyped asset URL and a removed REST endpoint into a
 * page of HTML, which is far harder to diagnose than a 404:
 *
 * <ul>
 * <li>the REST API is excluded by prefix, so a wrong API path still fails as one;</li>
 * <li>anything whose last segment contains a dot is treated as a file request and left to the static
 * handler, so a missing {@code .js} bundle still 404s instead of silently returning HTML that the
 * browser then refuses to execute;</li>
 * <li>and only a navigation is rewritten - a request that says it accepts HTML - so a {@code fetch()}
 * to a path that no longer exists still gets a 404 rather than a page.</li>
 * </ul>
 */
public final class UiPaths {

  /** Where the bundled UI is served from. */
  public static final String UI_PREFIX = "/camelbee";

  /** The single page every client-side route resolves to. */
  public static final String INDEX = "/camelbee/index.html";

  /**
   * The REST API, which shares the {@code /camelbee} prefix with the UI and must never be rewritten.
   *
   * <p>Listed rather than derived, for the same reason {@code AuthPaths} lists its own: a new
   * endpoint is excluded only when someone adds it here deliberately.
   */
  private static final List<String> API_PREFIXES = List.of(
      "/camelbee/routes",
      "/camelbee/messages",
      "/camelbee/tracer",
      "/camelbee/auth");

  private UiPaths() {
  }

  /**
   * Whether a request should be answered with the UI's {@code index.html}.
   *
   * @param path the request path, possibly null.
   * @return true when the path is a client-side route of the embedded UI.
   */
  public static boolean isClientRoute(String path) {
    if (path == null || !path.equals(UI_PREFIX) && !path.startsWith(UI_PREFIX + "/")) {
      return false;
    }
    if (API_PREFIXES.stream().anyMatch(path::startsWith)) {
      return false;
    }
    return !lastSegment(path).contains(".");
  }

  /**
   * Whether a request is a browser navigation rather than an asset or API call.
   *
   * @param acceptHeader the request's Accept header, possibly null.
   * @return true when the caller asked for HTML.
   */
  public static boolean wantsHtml(String acceptHeader) {
    return acceptHeader != null && acceptHeader.contains("text/html");
  }

  private static String lastSegment(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }
}
