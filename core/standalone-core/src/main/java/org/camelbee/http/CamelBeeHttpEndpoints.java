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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.tracers.TracerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposes the CamelBee UI and REST API as plain HTTP handlers on the camel-main management server's
 * Vert.x router, rather than as Camel routes.
 *
 * <p>This is the standalone equivalent of the JAX-RS / Spring-MVC controllers in the framework
 * cores: because the endpoints are HTTP handlers and not Camel routes, they never appear in the
 * route topology returned by {@code GET /camelbee/routes}, and they never create Camel exchanges, so
 * they produce no noise in the message tracer. Hosting them on the management server also keeps the
 * debug surface on its own port, isolated from whatever HTTP stack the application uses.
 *
 * <p>The contract served is identical to the other runtimes:
 * <ul>
 * <li>GET /camelbee/routes - route topology (CamelBeeContext)</li>
 * <li>GET /camelbee/messages - traced messages from an index (MessageListWithInfo)</li>
 * <li>DELETE /camelbee/messages - clear traced messages</li>
 * <li>POST /camelbee/tracer/status - ACTIVE/INACTIVE to toggle tracing</li>
 * <li>GET /camelbee[/...] - the embedded single-page UI</li>
 * </ul>
 */
public class CamelBeeHttpEndpoints {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamelBeeHttpEndpoints.class);

  private static final String BASE_PATH = "/camelbee";

  /** Classpath root the UI is bundled under (see the standalone-core pom resources copy). */
  private static final String UI_CLASSPATH_ROOT = "camelbee";

  private final CamelContext camelContext;
  private final TracerService tracerService;
  private final MessageService messageService;
  private final RouteContextService routeContextService;
  private final ObjectMapper objectMapper;

  /**
   * Constructor.
   *
   * @param camelContext        the CamelContext to introspect for the topology and runtime info.
   * @param tracerService       the tracer service.
   * @param messageService      the message service.
   * @param routeContextService the route context service.
   */
  public CamelBeeHttpEndpoints(CamelContext camelContext, TracerService tracerService,
      MessageService messageService, RouteContextService routeContextService) {
    this.camelContext = camelContext;
    this.tracerService = tracerService;
    this.messageService = messageService;
    this.routeContextService = routeContextService;
    // serialize java.time.Instant (MessageListInfo) as ISO strings, matching the REST cores
    this.objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Registers the CamelBee API and UI on the management server's router. Must be called once the
   * CamelContext is fully started, since the management router is only created when that server
   * starts. No-op (with a warning) if the management server is not available.
   *
   * @param context the started CamelContext.
   */
  public void register(CamelContext context) {
    ManagementHttpServer management = context.hasService(ManagementHttpServer.class);
    if (management == null) {
      LOGGER.warn("CamelBee: management HTTP server not found; UI and API not exposed. "
          + "Enable it via camel.management.enabled=true (the standalone starter does this).");
      return;
    }

    VertxPlatformHttpRouter router = management.getRouter();
    if (router == null) {
      LOGGER.warn("CamelBee: management HTTP server router not available; UI and API not exposed.");
      return;
    }

    registerApi(router);
    registerUi(router);

    LOGGER.info("CamelBee UI and API available on the management server (port {}) at {}.",
        management.getPort(), BASE_PATH);
  }

  private void registerApi(VertxPlatformHttpRouter router) {
    // Permissive CORS so the UI dev server (a different origin during development) can call the API.
    // In a packaged build the UI and API are same-origin on the management port, so this is a no-op.
    CorsHandler cors = CorsHandler.create()
        .addRelativeOrigin(".*")
        .allowedMethod(HttpMethod.GET)
        .allowedMethod(HttpMethod.POST)
        .allowedMethod(HttpMethod.DELETE)
        .allowedMethod(HttpMethod.OPTIONS)
        .allowedHeader("Content-Type");
    router.route(BASE_PATH + "/*").handler(cors);

    router.get(BASE_PATH + "/routes").handler(this::getRoutes);
    router.get(BASE_PATH + "/messages").handler(this::getMessages);
    router.delete(BASE_PATH + "/messages").handler(this::deleteMessages);
    router.post(BASE_PATH + "/tracer/status")
        .handler(BodyHandler.create())
        .handler(this::tracerStatus);
  }

  private void registerUi(VertxPlatformHttpRouter router) {
    StaticHandler ui = StaticHandler.create(UI_CLASSPATH_ROOT)
        .setIndexPage("index.html")
        .setDefaultContentEncoding("UTF-8");
    // Redirect only the bare context path to the trailing-slash form (the canonical UI URL). The
    // "/camelbee" route also matches "/camelbee/", so guard against redirecting that to itself.
    router.get(BASE_PATH).handler(rc -> {
      if (BASE_PATH.equals(rc.request().path())) {
        rc.redirect(BASE_PATH + "/");
      } else {
        rc.next();
      }
    });
    // Registered after the API routes, so /camelbee/routes etc. take precedence over static serving.
    router.route(BASE_PATH + "/*").handler(ui);
  }

  // The handler methods below are package-private (not private) so they can be unit-tested directly
  // against a mocked RoutingContext, without standing up a real Vert.x server.

  void getRoutes(RoutingContext rc) {
    List<CamelRoute> routes = routeContextService.getCamelRoutes();

    String name = camelContext.getName();

    String jvm = "%s - %s".formatted(System.getProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR),
        System.getProperty(CamelBeeConstants.SYSTEM_JVM_VERSION));

    String camelVersion = camelContext.getVersion();

    String framework = "%s - Camel %s".formatted(CamelBeeConstants.FRAMEWORK, camelVersion);

    String jvmInputParameters = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .collect(Collectors.joining(", "));

    String garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans().stream()
        .map(GarbageCollectorMXBean::getName)
        .collect(Collectors.joining(", "));

    writeJson(rc, new CamelBeeContext(routes, name, jvm, jvmInputParameters, garbageCollectors,
        framework, camelVersion));
  }

  void getMessages(RoutingContext rc) {
    int fromIndex = intParam(rc, "index", 0);
    long addVersion = longParam(rc, "addVersion", 0L);
    long resetVersion = longParam(rc, "resetVersion", 0L);

    tracerService.keepTracingActive();

    writeJson(rc, messageService.getMessagesFrom(fromIndex, addVersion, resetVersion));
  }

  void deleteMessages(RoutingContext rc) {
    messageService.reset();
    rc.response().putHeader("content-type", "text/plain").end("deleted.");
  }

  void tracerStatus(RoutingContext rc) {
    String raw = rc.body() != null ? rc.body().asString() : null;
    String status = raw == null ? "" : raw.replaceAll("[^A-Za-z]", "").toUpperCase();

    if ("ACTIVE".equals(status)) {
      tracerService.activateTracing(true);
      tracerService.keepTracingActive();
    } else if ("INACTIVE".equals(status)) {
      tracerService.activateTracing(false);
    }

    rc.response().putHeader("content-type", "text/plain")
        .end("tracing status updated as:" + status);
  }

  private void writeJson(RoutingContext rc, Object body) {
    try {
      rc.response().putHeader("content-type", "application/json")
          .end(objectMapper.writeValueAsString(body));
    } catch (Exception e) {
      LOGGER.warn("CamelBee: failed to serialize response body", e);
      rc.response().setStatusCode(500).end("serialization error");
    }
  }

  static int intParam(RoutingContext rc, String name, int defaultValue) {
    String value = rc.request().getParam(name);
    try {
      return value == null ? defaultValue : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  static long longParam(RoutingContext rc, String name, long defaultValue) {
    String value = rc.request().getParam(name);
    try {
      return value == null ? defaultValue : Long.parseLong(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
