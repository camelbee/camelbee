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

package org.camelbee;

import org.apache.camel.CamelContext;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.Main;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.camelbee.config.CamelBeeConfig;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.CamelBeeUnitOfWork;
import org.camelbee.logging.LoggingService;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.camelbee.tracers.ExchangeCompletedEventTracer;
import org.camelbee.tracers.ExchangeCreatedEventTracer;
import org.camelbee.tracers.ExchangeSendingEventTracer;
import org.camelbee.tracers.ExchangeSentEventTracer;
import org.camelbee.tracers.TracerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point that wires CamelBee into a standalone Camel application (no Spring/Quarkus).
 *
 * <p>This performs by hand what the framework cores do via dependency injection: it instantiates
 * the tracing engine, registers the {@link CamelBeeEventNotifier} on the management strategy, and
 * exposes the UI + REST API as HTTP handlers on the camel-main management server (see
 * {@link org.camelbee.http.CamelBeeHttpEndpoints}). It honours the same {@code camelbee.*}
 * flags as the other runtimes via {@link CamelBeeConfig}.
 *
 * <p>Usage with {@code camel-main}:
 * <pre>{@code
 * Main main = new Main();
 * main.configure().addRoutesBuilder(new MyRoutes());
 * CamelBee.register(main); // attaches at the right point in the lifecycle
 * main.run(args);
 * }</pre>
 *
 * <p>Or against a CamelContext you bootstrap yourself, before starting it:
 * <pre>{@code
 * CamelBee.attach(camelContext);
 * camelContext.start();
 * }</pre>
 */
public final class CamelBee {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamelBee.class);

  private CamelBee() {
  }

  /**
   * Registers CamelBee with a {@code camel-main} application. The wiring is applied after the
   * main is configured but before the context starts.
   *
   * @param main the camel-main instance.
   */
  public static void register(Main main) {
    // CamelBee serves its UI and REST API on the camel-main management server, on its own port,
    // so they are never registered as Camel routes (no route-topology or message-tracer pollution)
    // and stay isolated from whatever HTTP stack the application itself uses. Health lives here too.
    // These are defaults: anything set in application.properties (camel.management.*) overrides them.
    main.configure().httpManagementServer()
        .withEnabled(true)
        .withPort(8081)
        .withHealthCheckEnabled(true);

    main.addMainListener(new MainListenerSupport() {

      @Override
      public void afterConfigure(BaseMainSupport baseMain) {
        try {
          attach(baseMain.getCamelContext());
        } catch (Exception e) {
          throw new IllegalStateException("Failed to attach CamelBee", e);
        }
      }
    });
  }

  /**
   * Wires CamelBee into the given CamelContext. Must be called before the context is started.
   *
   * @param camelContext the target CamelContext.
   * @throws Exception if the endpoint routes cannot be added.
   */
  public static void attach(CamelContext camelContext) throws Exception {

    final CamelBeeConfig config = CamelBeeConfig.from(camelContext);

    if (config.isRouteConfigurerEnabled()) {
      camelContext.setStreamCaching(true);
      camelContext.setUseMDCLogging(true);
      camelContext.getCamelContextExtension().addContextPlugin(UnitOfWorkFactory.class, CamelBeeUnitOfWork::new);
    } else {
      LOGGER.debug("CamelBee route configuration disabled via camelbee.route-configurer-enabled=false");
    }

    final MessageService messageService = new MessageService(config.getTracerMaxMessagesCount());
    final RouteContextService routeContextService = new RouteContextService(camelContext);
    final LoggingService loggingService = new LoggingService();

    final ExchangeCreatedEventTracer createdTracer = new ExchangeCreatedEventTracer(messageService);
    final ExchangeSendingEventTracer sendingTracer = new ExchangeSendingEventTracer(messageService, routeContextService);
    final ExchangeSentEventTracer sentTracer = new ExchangeSentEventTracer(messageService);
    final ExchangeCompletedEventTracer completedTracer = new ExchangeCompletedEventTracer(messageService);

    final TracerService tracerService = new TracerService(config.isLoggingEnabled(), config.isTracerEnabled(),
        config.getTracerMaxIdleTime(), createdTracer, sendingTracer, sentTracer, completedTracer, messageService,
        loggingService);

    if (config.isNotifierEnabled()) {
      camelContext.getManagementStrategy().addEventNotifier(new CamelBeeEventNotifier(tracerService));
    } else {
      LOGGER.debug("CamelBee event notifier disabled via camelbee.notifier-enabled=false");
    }

    if (config.isContextEnabled()) {
      // Expose the UI + REST API as HTTP handlers on the management server's Vert.x router (not as
      // Camel routes). The router only exists once the management server has started, so register
      // when the context is fully started. Guarded so plain-core users without platform-http-main
      // are not forced onto the Vert.x classpath (mirrors the metrics guard below).
      if (isPresent("org.apache.camel.component.platform.http.main.ManagementHttpServer")) {
        final org.camelbee.http.CamelBeeHttpEndpoints endpoints = new org.camelbee.http.CamelBeeHttpEndpoints(
            camelContext, tracerService, messageService, routeContextService);
        camelContext.addStartupListener((context, alreadyStarted) -> endpoints.register(context));
      } else {
        LOGGER.warn("CamelBee endpoints not exposed: camel-platform-http-main is not on the classpath. "
            + "Add the camelbee-standalone-starter (or camel-platform-http-main) to expose the "
            + "CamelBee UI and REST API.");
      }
    } else {
      LOGGER.debug("CamelBee endpoints disabled via camelbee.context-enabled=false");
    }

    // metrics are optional: only installed when enabled and both micrometer and the management
    // server (which serves the scrape endpoint) are on the classpath. The CamelBeeMetrics class,
    // which references both, is only referenced/loaded inside this guard.
    if (config.isMetricsEnabled()
        && isPresent("io.micrometer.prometheusmetrics.PrometheusMeterRegistry")
        && isPresent("org.apache.camel.component.platform.http.main.ManagementHttpServer")) {
      org.camelbee.metrics.CamelBeeMetrics.install(camelContext);
    } else {
      LOGGER.debug("CamelBee metrics not installed (camelbee.metrics-enabled=false, or micrometer "
          + "or platform-http-main absent)");
    }
  }

  private static boolean isPresent(String className) {
    try {
      Class.forName(className, false, CamelBee.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
