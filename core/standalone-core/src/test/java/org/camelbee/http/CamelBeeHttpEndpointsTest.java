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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import java.time.Instant;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.exchange.MessageListInfo;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the CamelBee HTTP handlers. The handler methods are invoked directly against a
 * mocked Vert.x {@link RoutingContext}, so the contract (topology, messages, tracer status) can be
 * verified without standing up a real management server.
 */
@ExtendWith(MockitoExtension.class)
class CamelBeeHttpEndpointsTest {

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

  private CamelBeeHttpEndpoints endpoints;

  @BeforeEach
  void setUp() {
    endpoints = new CamelBeeHttpEndpoints(camelContext, tracerService, messageService,
        routeContextService);
  }

  /** Stub the response chain so {@code response().putHeader(...).end(...)} works on the mock. */
  private void stubResponseChain() {
    when(routingContext.response()).thenReturn(response);
    when(response.putHeader(anyString(), anyString())).thenReturn(response);
  }

  private MessageListWithInfo emptyMessages() {
    return new MessageListWithInfo(List.of(),
        new MessageListInfo(0, 0, 0, Instant.now(), Instant.now()));
  }

  @Test
  void getRoutesShouldWriteTopologyAndSystemInfoAsJson() {
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR, "Test Vendor");
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VERSION, "21.0.1");
    List<CamelRoute> routes = List.of(
        new CamelRoute("route1", "direct:start1", null, false, "direct:err1"));
    when(routeContextService.getCamelRoutes()).thenReturn(routes);
    when(camelContext.getName()).thenReturn("TestContext");
    when(camelContext.getVersion()).thenReturn("4.20.0");
    stubResponseChain();

    endpoints.getRoutes(routingContext);

    verify(response).putHeader("content-type", "application/json");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).end(body.capture());
    String json = body.getValue();
    assertTrue(json.contains("TestContext"), json);
    assertTrue(json.contains("route1"), json);
    assertTrue(json.contains("4.20.0"), json);
    assertTrue(json.contains("Test Vendor - 21.0.1"), json);
  }

  /**
   * The single {@code getRoutes} test above already covers {@code outputs=null}; this one proves
   * the opposite edge - a nested output (an EIP like {@code choice} carrying its own child outputs)
   * survives Jackson serialization, not just the flat top-level fields.
   */
  @Test
  void getRoutesShouldSerializeNestedOutputs() {
    List<CamelRouteOutput> nestedOutputs = List.of(
        new CamelRouteOutput("nested1", "Nested Output 1", ",", "log", null));
    List<CamelRouteOutput> outputs = List.of(
        new CamelRouteOutput("output1", "First Output", "|", "direct", nestedOutputs));
    List<CamelRoute> routes = List.of(
        new CamelRoute("route1", "direct:start1", outputs, false, "direct:err1"));
    when(routeContextService.getCamelRoutes()).thenReturn(routes);
    when(camelContext.getName()).thenReturn("TestContext");
    when(camelContext.getVersion()).thenReturn("4.20.0");
    stubResponseChain();

    endpoints.getRoutes(routingContext);

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).end(body.capture());
    String json = body.getValue();
    assertTrue(json.contains("output1"), json);
    assertTrue(json.contains("nested1"), json);
    assertTrue(json.contains("Nested Output 1"), json);
  }

  @Test
  void getMessagesShouldKeepTracingActiveAndUseDefaultParams() {
    when(routingContext.request()).thenReturn(request);
    when(request.getParam("index")).thenReturn(null);
    when(request.getParam("addVersion")).thenReturn(null);
    when(request.getParam("resetVersion")).thenReturn(null);
    when(messageService.getMessagesFrom(0, 0L, 0L)).thenReturn(emptyMessages());
    stubResponseChain();

    endpoints.getMessages(routingContext);

    verify(tracerService).keepTracingActive();
    verify(messageService).getMessagesFrom(0, 0L, 0L);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(response).end(body.capture());
    assertNotNull(body.getValue());
  }

  @Test
  void getMessagesShouldParseQueryParams() {
    when(routingContext.request()).thenReturn(request);
    when(request.getParam("index")).thenReturn("5");
    when(request.getParam("addVersion")).thenReturn("10");
    when(request.getParam("resetVersion")).thenReturn("20");
    when(messageService.getMessagesFrom(5, 10L, 20L)).thenReturn(emptyMessages());
    stubResponseChain();

    endpoints.getMessages(routingContext);

    verify(messageService).getMessagesFrom(5, 10L, 20L);
  }

  @Test
  void deleteMessagesShouldResetAndRespondDeleted() {
    stubResponseChain();

    endpoints.deleteMessages(routingContext);

    verify(messageService).reset();
    verify(response).end("deleted.");
  }

  @Test
  void tracerStatusActiveShouldActivateTracing() {
    when(routingContext.body()).thenReturn(requestBody);
    when(requestBody.asString()).thenReturn("ACTIVE");
    stubResponseChain();

    endpoints.tracerStatus(routingContext);

    verify(tracerService).activateTracing(true);
    verify(tracerService).keepTracingActive();
  }

  @Test
  void tracerStatusInactiveShouldDeactivateTracing() {
    when(routingContext.body()).thenReturn(requestBody);
    when(requestBody.asString()).thenReturn("INACTIVE");
    stubResponseChain();

    endpoints.tracerStatus(routingContext);

    verify(tracerService).activateTracing(false);
    verify(tracerService, never()).keepTracingActive();
  }

  @Test
  void tracerStatusWithNoBodyShouldNotChangeTracing() {
    when(routingContext.body()).thenReturn(requestBody);
    when(requestBody.asString()).thenReturn(null);
    stubResponseChain();

    endpoints.tracerStatus(routingContext);

    verify(tracerService, never()).activateTracing(anyBoolean());
    verify(tracerService, never()).keepTracingActive();
  }

  @Test
  void intParamShouldFallBackToDefaultOnInvalidValue() {
    when(routingContext.request()).thenReturn(request);
    when(request.getParam("index")).thenReturn("not-a-number");

    assertEquals(7, CamelBeeHttpEndpoints.intParam(routingContext, "index", 7));
  }

  @Test
  void longParamShouldReturnParsedValue() {
    when(routingContext.request()).thenReturn(request);
    when(request.getParam("v")).thenReturn("42");

    assertEquals(42L, CamelBeeHttpEndpoints.longParam(routingContext, "v", 0L));
  }

  @Test
  void registerShouldNotThrowWhenManagementServerAbsent() {
    when(camelContext.hasService(ManagementHttpServer.class)).thenReturn(null);

    assertDoesNotThrow(() -> endpoints.register(camelContext));
  }

  /**
   * The UI is a BrowserRouter with basename "/camelbee", so its pages are real paths that no file
   * backs. Without a fallback, reloading or bookmarking anything but the root 404s even though the
   * same page reached by clicking works - which is how this was found.
   */
  @Test
  void spaFallbackShouldServeIndexForAUiRoute() {
    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/camelbee/settings");
    when(request.getHeader("Accept")).thenReturn("text/html,application/xhtml+xml");

    endpoints.spaFallback(routingContext);

    verify(routingContext).reroute("/camelbee/index.html");
    verify(routingContext, never()).next();
  }

  @Test
  void spaFallbackShouldLeaveAMissingAssetAs404() {
    // returning HTML for a missing .js turns a broken asset into a parse error somewhere else.
    // No Accept stub: the path rule rejects a file request before the header is ever consulted,
    // which is deliberate - a browser navigating and an <script src> both send text/html-ish
    // Accept values, so the extension is what has to decide here.
    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/camelbee/assets/main.js");

    endpoints.spaFallback(routingContext);

    verify(routingContext, never()).reroute(anyString());
    verify(routingContext).next();
  }

  @Test
  void spaFallbackShouldIgnoreNonNavigationRequests() {
    // an XHR that 404s must stay a 404; the caller is code, and it cannot use a page of HTML
    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/camelbee/whatever");
    when(request.getHeader("Accept")).thenReturn("application/json");

    endpoints.spaFallback(routingContext);

    verify(routingContext, never()).reroute(anyString());
    verify(routingContext).next();
  }

  @Test
  void spaFallbackShouldIgnoreARequestWithNoAcceptHeader() {
    when(routingContext.request()).thenReturn(request);
    when(request.path()).thenReturn("/camelbee/settings");
    when(request.getHeader("Accept")).thenReturn(null);

    endpoints.spaFallback(routingContext);

    verify(routingContext, never()).reroute(anyString());
    verify(routingContext).next();
  }
}
