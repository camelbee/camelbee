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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.CamelContext;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.security.AuthService;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The guard, at the HTTP layer.
 *
 * <p>{@code AuthServiceTest} proves the token logic. What it cannot prove is that the guard is
 * actually wired in front of the handlers - and that is the failure that matters, because a filter
 * that verifies correctly but is never reached, or that calls {@code next()} anyway, is an
 * authentication bypass that every other test would still pass. So each case here asserts on
 * {@code ctx.next()}: whether the request was allowed to continue at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CamelBeeHttpEndpointsAuthTest {

  private static final String PASSWORD = "s3cret";

  @Mock
  private CamelContext camelContext;
  @Mock
  private TracerService tracerService;
  @Mock
  private MessageService messageService;
  @Mock
  private RouteContextService routeContextService;
  @Mock
  private RoutingContext routingContext;
  @Mock
  private HttpServerResponse response;
  @Mock
  private HttpServerRequest request;
  @Mock
  private RequestBody requestBody;

  private AuthService authService;
  private CamelBeeHttpEndpoints endpoints;

  @BeforeEach
  void setUp() {
    authService = new AuthService(true, "camelbee", PASSWORD, 120_000);
    endpoints = new CamelBeeHttpEndpoints(camelContext, tracerService, messageService,
        routeContextService, authService, null);

    when(routingContext.response()).thenReturn(response);
    when(routingContext.request()).thenReturn(request);
    when(response.putHeader(anyString(), anyString())).thenReturn(response);
    when(response.setStatusCode(anyInt())).thenReturn(response);
  }

  @Test
  @DisplayName("no Authorization header is rejected, and the request does not continue")
  void rejectsMissingHeader() {
    when(request.getHeader("Authorization")).thenReturn(null);

    endpoints.requireToken(routingContext);

    verify(response).setStatusCode(401);
    verify(routingContext, never()).next();
  }

  @Test
  @DisplayName("a non-Bearer header is rejected")
  void rejectsNonBearerHeader() {
    when(request.getHeader("Authorization")).thenReturn("Basic Y2FtZWxiZWU6czNjcmV0");

    endpoints.requireToken(routingContext);

    verify(response).setStatusCode(401);
    verify(routingContext, never()).next();
  }

  @Test
  @DisplayName("a tampered token is rejected")
  void rejectsTamperedToken() {
    when(request.getHeader("Authorization")).thenReturn("Bearer " + authService.issueToken() + "x");

    endpoints.requireToken(routingContext);

    verify(response).setStatusCode(401);
    verify(routingContext, never()).next();
  }

  @Test
  @DisplayName("a valid token continues, and carries a refreshed token back")
  void acceptsValidTokenAndRefreshes() {
    when(request.getHeader("Authorization")).thenReturn("Bearer " + authService.issueToken());

    endpoints.requireToken(routingContext);

    verify(routingContext).next();
    verify(response, never()).setStatusCode(401);
    verify(response).putHeader(eq("X-CamelBee-Token"), anyString());
  }

  @Test
  @DisplayName("with authentication off the guard is transparent")
  void disabledGuardLetsEverythingThrough() {
    CamelBeeHttpEndpoints open = new CamelBeeHttpEndpoints(camelContext, tracerService,
        messageService, routeContextService, AuthService.disabled(), null);

    open.requireToken(routingContext);

    verify(routingContext).next();
    verify(response, never()).setStatusCode(401);
  }

  @Test
  @DisplayName("login with the right credentials returns a token that the guard then accepts")
  void loginIssuesAUsableToken() {
    when(routingContext.body()).thenReturn(requestBody);
    when(requestBody.asString()).thenReturn("{\"username\":\"camelbee\",\"password\":\"" + PASSWORD + "\"}");

    endpoints.login(routingContext);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).end(body.capture());
    String token = body.getValue().replaceAll(".*\"token\":\"([^\"]*)\".*", "$1");

    assertThat(token).isNotEmpty();
    assertThat(authService.verifyAndRefresh(token))
        .as("the token handed to the browser has to be one the guard accepts")
        .isPresent();
  }

  @Test
  @DisplayName("login with a wrong password is refused")
  void loginRejectsWrongPassword() {
    when(routingContext.body()).thenReturn(requestBody);
    when(requestBody.asString()).thenReturn("{\"username\":\"camelbee\",\"password\":\"wrong\"}");

    endpoints.login(routingContext);

    verify(response).setStatusCode(401);
  }

  @Test
  @DisplayName("a malformed login body is a failed login, not a 500")
  void loginRejectsMalformedBody() {
    when(routingContext.body()).thenReturn(requestBody);
    when(requestBody.asString()).thenReturn("not json at all");

    endpoints.login(routingContext);

    verify(response).setStatusCode(401);
  }

  @Test
  @DisplayName("auth status is readable without a token - the UI asks before it has one")
  void authStatusIsPublic() {
    endpoints.authStatus(routingContext);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).end(body.capture());
    assertThat(body.getValue()).contains("\"authEnabled\":true");
  }
}
