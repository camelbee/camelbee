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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The guard, in isolation. Mirrors {@code quarkus-core}'s test of the same name.
 *
 * <p>Every case asserts whether the chain was continued, because that is the only thing that decides
 * whether an unauthenticated caller reaches the data.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CamelBeeAuthFilterTest {

  @Mock
  private HttpServletRequest request;
  @Mock
  private FilterChain chain;

  private MockHttpServletResponse response;
  private AuthService authService;
  private CamelBeeAuthFilter filter;

  @BeforeEach
  void setUp() {
    authService = new AuthService(true, "camelbee", "s3cret", 120_000);
    filter = new CamelBeeAuthFilter(authService);
    response = new MockHttpServletResponse();
  }

  @Test
  @DisplayName("a protected path without a token is rejected")
  void rejectsProtectedPathWithoutToken() throws Exception {
    when(request.getRequestURI()).thenReturn("/camelbee/routes");

    filter.doFilterInternal(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  @DisplayName("the tracer endpoints are protected too - the API is not read-only")
  void rejectsTracerWithoutToken() throws Exception {
    when(request.getRequestURI()).thenReturn("/camelbee/tracer/status");

    filter.doFilterInternal(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  @DisplayName("a valid token passes and gets a refreshed one back")
  void acceptsValidToken() throws Exception {
    when(request.getRequestURI()).thenReturn("/camelbee/routes");
    when(request.getHeader("Authorization")).thenReturn("Bearer " + authService.issueToken());

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getHeader("X-CamelBee-Token")).isNotBlank();
  }

  @Test
  @DisplayName("a tampered token is rejected")
  void rejectsTamperedToken() throws Exception {
    when(request.getRequestURI()).thenReturn("/camelbee/messages");
    when(request.getHeader("Authorization")).thenReturn("Bearer " + authService.issueToken() + "x");

    filter.doFilterInternal(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  @DisplayName("the login endpoint and the UI shell stay reachable")
  void leavesPublicPathsAlone() throws Exception {
    // Both must be public, for opposite reasons: a caller has no token yet, and the browser has to
    // load the application before it can show a login form.
    for (String path : new String[]{"/camelbee/auth/login", "/camelbee/auth/status",
        "/camelbee/index.html", "/camelbee/"}) {
      when(request.getRequestURI()).thenReturn(path);

      filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
    }

    verify(chain, org.mockito.Mockito.times(4)).doFilter(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("with authentication off the filter is transparent")
  void disabledFilterIsTransparent() throws Exception {
    CamelBeeAuthFilter open = new CamelBeeAuthFilter(AuthService.disabled());
    when(request.getRequestURI()).thenReturn("/camelbee/routes");

    open.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("the rejection body says nothing useful to an attacker")
  void rejectionBodyIsOpaque() throws Exception {
    when(request.getRequestURI()).thenReturn("/camelbee/routes");
    filter.doFilterInternal(request, response, chain);

    assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"unauthorized\"}");
  }
}
