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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The guard, in isolation.
 *
 * <p>Every case asserts on whether {@code ctx.next()} was called, because that is the only thing
 * that decides whether an unauthenticated caller reaches the data. A filter that verifies a token
 * correctly and then calls {@code next()} regardless is a complete bypass that no other test would
 * notice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CamelBeeAuthFilterTest {

  @Mock
  private RoutingContext ctx;
  @Mock
  private HttpServerRequest request;
  @Mock
  private HttpServerResponse response;

  private CamelBeeAuthFilter filter;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(true, "camelbee", "s3cret", 120_000);
    filter = new CamelBeeAuthFilter();
    filter.authService = authService;

    when(ctx.request()).thenReturn(request);
    when(ctx.response()).thenReturn(response);
    when(response.setStatusCode(anyInt())).thenReturn(response);
    when(response.putHeader(anyString(), anyString())).thenReturn(response);
  }

  @Test
  @DisplayName("a protected path without a token is rejected")
  void rejectsProtectedPathWithoutToken() {
    when(request.path()).thenReturn("/camelbee/routes");
    when(request.getHeader("Authorization")).thenReturn(null);

    filter.filter(ctx);

    verify(response).setStatusCode(401);
    verify(ctx, never()).next();
  }

  @Test
  @DisplayName("the tracer endpoints are protected too - the API is not read-only")
  void rejectsTracerWithoutToken() {
    when(request.path()).thenReturn("/camelbee/tracer/status");
    when(request.getHeader("Authorization")).thenReturn(null);

    filter.filter(ctx);

    verify(response).setStatusCode(401);
    verify(ctx, never()).next();
  }

  @Test
  @DisplayName("a valid token passes and gets a refreshed one back")
  void acceptsValidToken() {
    when(request.path()).thenReturn("/camelbee/routes");
    when(request.getHeader("Authorization")).thenReturn("Bearer " + authService.issueToken());

    filter.filter(ctx);

    verify(ctx).next();
    verify(response).putHeader(eq("X-CamelBee-Token"), anyString());
  }

  @Test
  @DisplayName("a tampered token is rejected")
  void rejectsTamperedToken() {
    when(request.path()).thenReturn("/camelbee/messages");
    when(request.getHeader("Authorization")).thenReturn("Bearer " + authService.issueToken() + "x");

    filter.filter(ctx);

    verify(response).setStatusCode(401);
    verify(ctx, never()).next();
  }

  @Test
  @DisplayName("the login endpoint and the UI shell stay reachable")
  void leavesPublicPathsAlone() {
    // Both must be public, for opposite reasons: a caller has no token yet, and the browser has to
    // load the application before it can show a login form.
    for (String path : new String[]{"/camelbee/auth/login", "/camelbee/auth/status",
        "/camelbee/index.html", "/camelbee/"}) {
      when(request.path()).thenReturn(path);

      filter.filter(ctx);
    }

    verify(ctx, org.mockito.Mockito.times(4)).next();
    verify(response, never()).setStatusCode(401);
  }

  @Test
  @DisplayName("with authentication off the filter is transparent")
  void disabledFilterIsTransparent() {
    filter.authService = AuthService.disabled();
    when(request.path()).thenReturn("/camelbee/routes");

    filter.filter(ctx);

    verify(ctx).next();
    verify(response, never()).setStatusCode(401);
  }
}
