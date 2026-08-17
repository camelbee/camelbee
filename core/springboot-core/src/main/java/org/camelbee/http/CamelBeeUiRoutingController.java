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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the UI's {@code index.html} for the client-side routes, so a reload does not 404.
 *
 * <p>Spring Boot serves {@code static/camelbee} as static files. {@code /camelbee/settings} is not
 * one - it exists only in the browser's router - so pressing F5 there, or opening a shared link to
 * it, used to answer 404 for a page that was on screen a moment earlier.
 *
 * <p>A {@code forward:} rather than reading the file: the forward re-enters the servlet container at
 * {@code /camelbee/index.html}, so the resource handler answers it with the correct content type and
 * caching headers, and nothing here has to know where the bundle lives.
 *
 * <p><strong>The mapping is deliberately one segment wide and dot-free.</strong> A controller mapping
 * outranks the static resource handler, so a blanket {@code /camelbee/**} would capture
 * {@code /camelbee/assets/index-abc.js} and break the very bundle it is meant to serve. The UI's
 * routes are single segments below the base path ({@code /debugger}, {@code /metrics},
 * {@code /settings}), and the REST endpoints that share the shape - {@code /camelbee/routes},
 * {@code /camelbee/messages} - keep their own literal mappings, which Spring prefers over a
 * pattern with a variable. {@link UiPaths#isClientRoute} is the belt to that braces: it rejects an
 * API path even if this method is reached, which happens when a controller is switched off with
 * {@code camelbee.context-enabled}.
 *
 * <p>A {@code @Controller} rather than a {@code @RestController}, because the returned string is a
 * view instruction, not a response body.
 */
@Controller
public class CamelBeeUiRoutingController {

  /**
   * Forwards a client-side route to the single page, and 404s anything else exactly as before.
   *
   * @param request the current request, read only for its path.
   * @return the forward instruction.
   */
  @GetMapping({"/camelbee", "/camelbee/", "/camelbee/{segment:[^.]*}"})
  public String forwardClientRoute(HttpServletRequest request) {
    // path within the application, so an app deployed under a servlet context path still matches
    String path = request.getRequestURI().substring(request.getContextPath().length());

    if (!UiPaths.isClientRoute(path) || !UiPaths.wantsHtml(request.getHeader("Accept"))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    return "forward:" + UiPaths.INDEX;
  }
}
