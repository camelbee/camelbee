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

import com.fasterxml.jackson.databind.JsonNode;
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
import org.camelbee.security.AuthService;
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
 * <li>POST /camelbee/tracer/filter - raw text every recorded message must contain, empty to clear</li>
 * <li>GET /camelbee[/...] - the embedded single-page UI</li>
 * </ul>
 */
public class CamelBeeHttpEndpoints {

  /** Reported to the UI. Runtime-specific, so it stays here rather than in the shared engine. */
  private static final String FRAMEWORK = "Standalone";

  private static final Logger LOGGER = LoggerFactory.getLogger(CamelBeeHttpEndpoints.class);

  private static final String BASE_PATH = "/camelbee";

  /** Classpath root the UI is bundled under (see the standalone-core pom resources copy). */
  private static final String UI_CLASSPATH_ROOT = "camelbee";

  private static final String AUTHORIZATION_HEADER = "Authorization";

  private static final String BEARER_PREFIX = "Bearer ";

  /** Carries the rolling token back, so an active caller's idle window keeps moving. */
  private static final String REFRESHED_TOKEN_HEADER = "X-CamelBee-Token";

  private final CamelContext camelContext;
  private final TracerService tracerService;
  private final MessageService messageService;
  private final RouteContextService routeContextService;
  private final AuthService authService;
  private final String corsAllowedOrigin;
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
    this(camelContext, tracerService, messageService, routeContextService, AuthService.disabled(), null);
  }

  /**
   * Constructor.
   *
   * @param camelContext        the context.
   * @param tracerService       the tracer.
   * @param messageService      the message store.
   * @param routeContextService the topology service.
   * @param authService         guards every endpoint except login and the UI shell.
   * @param corsAllowedOrigin   a single allowed origin for the UI dev server, or null for none.
   */
  @SuppressWarnings("java:S107")
  public CamelBeeHttpEndpoints(CamelContext camelContext, TracerService tracerService,
      MessageService messageService, RouteContextService routeContextService,
      AuthService authService, String corsAllowedOrigin) {
    this.camelContext = camelContext;
    this.tracerService = tracerService;
    this.messageService = messageService;
    this.routeContextService = routeContextService;
    this.authService = authService;
    this.corsAllowedOrigin = corsAllowedOrigin;
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
    /*
     CORS is closed unless an origin is configured. It used to allow every origin so the UI dev
     server could call the API, but that is a browser-enforced control: with it wide open, ANY page
     a developer visited could read this application's topology and traced messages, and POST to
     tracer/status to start capture. In a packaged build the UI and API are same-origin, so nothing
     needs it - camelbee.cors-allowed-origin exists for the dev-server case only.
     */
    if (corsAllowedOrigin != null && !corsAllowedOrigin.isBlank()) {
      LOGGER.warn("CamelBee CORS is open to origin '{}'. Intended for the UI dev server; "
          + "do not set this in a deployed application.", corsAllowedOrigin);
      CorsHandler cors = CorsHandler.create()
          .addOrigin(corsAllowedOrigin)
          .allowedMethod(HttpMethod.GET)
          .allowedMethod(HttpMethod.POST)
          .allowedMethod(HttpMethod.DELETE)
          .allowedMethod(HttpMethod.OPTIONS)
          .allowedHeader("Content-Type")
          .allowedHeader(AUTHORIZATION_HEADER)
          .exposedHeader(REFRESHED_TOKEN_HEADER)
          .allowCredentials(true);
      router.route(BASE_PATH + "/*").handler(cors);
    }

    // Public: the caller has no token yet, and the UI shell has to be loadable to show a login form.
    router.post(BASE_PATH + "/auth/login")
        .handler(BodyHandler.create())
        .handler(this::login);
    router.get(BASE_PATH + "/auth/status").handler(this::authStatus);

    /*
     Everything below is guarded. Registered before the handlers it protects, because Vert.x runs
     route handlers in registration order - a guard added afterwards would run after the data had
     already been written.
     */
    router.route(BASE_PATH + "/routes").handler(this::requireToken);
    router.route(BASE_PATH + "/messages").handler(this::requireToken);
    router.route(BASE_PATH + "/tracer/*").handler(this::requireToken);

    router.get(BASE_PATH + "/routes").handler(this::getRoutes);
    router.get(BASE_PATH + "/messages").handler(this::getMessages);
    router.delete(BASE_PATH + "/messages").handler(this::deleteMessages);
    router.post(BASE_PATH + "/tracer/status")
        .handler(BodyHandler.create())
        .handler(this::tracerStatus);
    router.post(BASE_PATH + "/tracer/filter")
        .handler(BodyHandler.create())
        .handler(this::tracerFilter);
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

    /*
     Single-page-app fallback. The UI is a BrowserRouter with basename "/camelbee", so its routes are
     real paths - /camelbee/settings, /camelbee/metrics. StaticHandler has no file for those, so a
     reload or a bookmarked link 404s even though the same page reached by clicking works fine.
     Serve index.html instead and let the router take it from there.

     Only for navigations: a request that accepts HTML and has no file extension. A missing .js or
     .png must stay a 404 rather than silently returning a page of HTML, which turns a broken asset
     into a confusing parse error.
    */
    router.get(BASE_PATH + "/*").handler(this::spaFallback);
  }

  /**
   * Serves {@code index.html} for a UI route that has no file behind it, so the router can take over.
   *
   * <p>Only for navigations: a request that accepts HTML and whose last path segment has no
   * extension. A missing {@code .js} or {@code .png} must stay a 404 rather than silently returning
   * a page of HTML, which turns a broken asset into a confusing parse error somewhere else.
   *
   * @param rc the routing context.
   */
  void spaFallback(RoutingContext rc) {
    // The rule itself lives in UiPaths, shared with the Quarkus filter and the Spring Boot
    // controller that do the same job on those runtimes - three copies of it would drift.
    if (UiPaths.isClientRoute(rc.request().path())
        && UiPaths.wantsHtml(rc.request().getHeader("Accept"))) {
      rc.reroute(UiPaths.INDEX);
    } else {
      rc.next();
    }
  }

  // The handler methods below are package-private (not private) so they can be unit-tested directly
  // against a mocked RoutingContext, without standing up a real Vert.x server.

  void getRoutes(RoutingContext rc) {
    List<CamelRoute> routes = routeContextService.getCamelRoutes();

    String name = camelContext.getName();

    String jvm = "%s - %s".formatted(System.getProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR),
        System.getProperty(CamelBeeConstants.SYSTEM_JVM_VERSION));

    String camelVersion = camelContext.getVersion();

    String framework = "%s - Camel %s".formatted(FRAMEWORK, camelVersion);

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

  /**
   * Sets the substring a message must contain to be recorded at all. An empty body clears it.
   *
   * <p>Taken as raw text rather than JSON: the filter is an arbitrary payload fragment - an order
   * id, a customer reference - and quoting rules would only get in the way.
   */
  void tracerFilter(RoutingContext rc) {
    String filter = rc.body() != null ? rc.body().asString() : null;
    messageService.setCaptureFilter(filter);

    rc.response().putHeader("content-type", "text/plain")
        .end(messageService.getCaptureFilter() == null
            ? "capture filter cleared."
            : "capture filter set.");
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

  /**
   * Rejects a request that carries no valid token, and refreshes the token when it does.
   *
   * <p>Ends the request rather than delegating, so no handler further down the chain can write data
   * to an unauthenticated caller.
   *
   * @param ctx the routing context.
   */
  void requireToken(RoutingContext ctx) {
    if (!authService.isEnabled()) {
      ctx.next();
      return;
    }

    final String header = ctx.request().getHeader(AUTHORIZATION_HEADER);
    final String token = header != null && header.startsWith(BEARER_PREFIX)
        ? header.substring(BEARER_PREFIX.length())
        : null;

    authService.verifyAndRefresh(token).ifPresentOrElse(
        refreshed -> {
          ctx.response().putHeader(REFRESHED_TOKEN_HEADER, refreshed);
          ctx.next();
        },
        () -> ctx.response().setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"unauthorized\"}"));
  }

  /**
   * Exchanges credentials for a token.
   *
   * @param ctx the routing context.
   */
  void login(RoutingContext ctx) {
    if (!authService.isEnabled()) {
      ctx.response().putHeader("Content-Type", "application/json").end("{\"token\":\"\"}");
      return;
    }

    String user = null;
    String pass = null;
    try {
      JsonNode body = objectMapper.readTree(ctx.body().asString());
      user = body.path("username").asText(null);
      pass = body.path("password").asText(null);
    } catch (Exception e) {
      // A malformed body is a failed login, not a server error - and it must not say which.
      LOGGER.debug("CamelBee login: unreadable request body", e);
    }

    if (!authService.authenticate(user, pass)) {
      // Deliberately identical for an unknown user and a wrong password, and no timing difference:
      // AuthService compares both in constant time.
      ctx.response().setStatusCode(401)
          .putHeader("Content-Type", "application/json")
          .end("{\"error\":\"invalid credentials\"}");
      return;
    }

    ctx.response().putHeader("Content-Type", "application/json")
        .end("{\"token\":\"" + authService.issueToken() + "\"}");
  }

  /**
   * Tells the UI whether it needs to show a login form at all.
   *
   * <p>Public by necessity - the UI has to ask this before it has a token. It discloses only whether
   * authentication is switched on, which an unauthenticated caller learns anyway from the first 401.
   *
   * @param ctx the routing context.
   */
  void authStatus(RoutingContext ctx) {
    ctx.response().putHeader("Content-Type", "application/json")
        .end("{\"authEnabled\":" + authService.isEnabled() + "}");
  }
}
