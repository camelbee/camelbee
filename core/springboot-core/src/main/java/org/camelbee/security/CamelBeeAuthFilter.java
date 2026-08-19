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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the CamelBee endpoints on Spring Boot.
 *
 * <p>A servlet filter rather than Spring Security: it is the one place both the REST controllers and
 * the UI's static resources under {@code static/camelbee} pass through, and it adds no dependency.
 * An application that already uses Spring Security can leave {@code camelbee.auth-enabled=false} and
 * put its own {@code SecurityFilterChain} in front of {@code /camelbee/**} instead - the core README
 * describes that.
 */
@Component
public class CamelBeeAuthFilter extends OncePerRequestFilter {

  private final AuthService authService;

  public CamelBeeAuthFilter(AuthService authService) {
    this.authService = authService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    if (!authService.isEnabled() || !AuthPaths.isProtected(request.getRequestURI())) {
      chain.doFilter(request, response);
      return;
    }

    final String token = AuthPaths.bearerToken(request.getHeader(AuthPaths.AUTHORIZATION_HEADER));
    final var refreshed = authService.verifyAndRefresh(token);

    if (refreshed.isEmpty()) {
      // Written here rather than delegated, so nothing downstream can produce a body for an
      // unauthenticated caller.
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"unauthorized\"}");
      return;
    }

    response.setHeader(AuthPaths.REFRESHED_TOKEN_HEADER, refreshed.get());
    chain.doFilter(request, response);
  }
}
