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

package org.camelbee.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional Prometheus metrics support for the standalone runtime.
 *
 * <p>This class references micrometer directly, so it is only loaded when micrometer +
 * micrometer-registry-prometheus are on the classpath (guarded by {@code CamelBee}, which also
 * requires the management server). It binds a {@link PrometheusMeterRegistry} for camel-micrometer
 * to use, records per-route metrics via {@link MicrometerRoutePolicyFactory}, and exposes the
 * Prometheus scrape as a Vert.x handler on the camel-main management server at
 * {@code /observe/metrics} (alongside the built-in {@code /observe/health}).
 *
 * <p>Serving the scrape as an HTTP handler rather than a {@code platform-http:} Camel route keeps it
 * off the route topology and on the management port, consistent with the rest of CamelBee.
 */
public final class CamelBeeMetrics {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamelBeeMetrics.class);

  private CamelBeeMetrics() {
  }

  /**
   * Installs metrics into the given CamelContext. Must be called before the context is started.
   *
   * @param camelContext the target CamelContext.
   * @throws Exception if the startup listener cannot be added.
   */
  public static void install(CamelContext camelContext) throws Exception {

    final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    // bind under the conventional name so camel-micrometer uses this registry
    camelContext.getRegistry().bind(MicrometerConstants.METRICS_REGISTRY_NAME, MeterRegistry.class, registry);

    // record per-route exchange metrics
    camelContext.addRoutePolicyFactory(new MicrometerRoutePolicyFactory());

    // Expose the Prometheus scrape as a Vert.x handler on the management server (not a Camel route),
    // so it stays off the route topology and on the management port. The management router only
    // exists once that server has started, hence registering from a startup listener.
    camelContext.addStartupListener((context, alreadyStarted) -> registerScrapeEndpoint(context, registry));
  }

  private static void registerScrapeEndpoint(CamelContext context, PrometheusMeterRegistry registry) {
    ManagementHttpServer management = context.hasService(ManagementHttpServer.class);
    if (management == null || management.getRouter() == null) {
      LOGGER.warn("CamelBee: management HTTP server not available; Prometheus metrics not exposed.");
      return;
    }

    VertxPlatformHttpRouter router = management.getRouter();
    router.get("/observe/metrics").handler(rc -> rc.response()
        .putHeader("content-type", "text/plain; version=0.0.4")
        .end(registry.scrape()));

    LOGGER.info("CamelBee Prometheus metrics available on the management server (port {}) at "
        + "/observe/metrics.", management.getPort());
  }
}
