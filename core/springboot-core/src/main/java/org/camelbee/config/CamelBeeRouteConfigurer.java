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

package org.camelbee.config;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.camelbee.logging.CamelBeeUnitOfWork;
import org.camelbee.tracers.NodeIdInterceptStrategy;
import org.camelbee.tracers.PollInterceptStrategy;
import org.camelbee.tracers.TracerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The route configurer which sets all listeners, interceptors and the MDCUnitOfWork.
 */
@Component
public class CamelBeeRouteConfigurer {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(CamelBeeRouteConfigurer.class);

  @Value("${camelbee.route-configurer-enabled:true}")
  private boolean routeConfigurerEnabled;

  /**
   * Needed by the poll tracing strategy, which records hops Camel emits no events for.
   *
   * <p>Required: a CamelBee application always has the tracer graph. Sliced test contexts that
   * import this configurer on its own - {@code @SpringBootTest(classes = ...)}, which component
   * scans nothing - have to import {@link CamelBeeTracingConfiguration} alongside it.
   */
  @Autowired
  private TracerService tracerService;

  /**
   * Configures a route for a CamelBee enabled Camel application.
   *
   * @param routeBuilder The routebuilder to be configured.
   */
  public void configureRoute(RouteBuilder routeBuilder) {

    if (routeConfigurerEnabled) {
      routeBuilder.getContext().setStreamCaching(true);
      routeBuilder.getContext().setUseMDCLogging(true);
      routeBuilder.getContext().getCamelContextExtension().addContextPlugin(UnitOfWorkFactory.class, CamelBeeUnitOfWork::new);
      addNodeIdInterceptStrategy(routeBuilder);
      addPollInterceptStrategy(routeBuilder);
    } else {
      LOGGER.debug("CamelBee route configuration disabled via camelbee.route-configurer-enabled=false");
    }
  }

  /**
   * Registers the node-id intercept strategy once per CamelContext.
   *
   * <p>It has to be added before the routes are reified - intercept strategies are consulted while
   * each processor is built - which is why it lives here rather than alongside the event notifier,
   * which is registered on startup after the routes already exist. This method is called by every
   * route builder, so it guards against registering more than one.
   *
   * @param routeBuilder the route builder being configured.
   */
  private void addNodeIdInterceptStrategy(RouteBuilder routeBuilder) {
    boolean alreadyRegistered = routeBuilder.getContext().getCamelContextExtension()
        .getInterceptStrategies().stream()
        .anyMatch(NodeIdInterceptStrategy.class::isInstance);

    if (!alreadyRegistered) {
      routeBuilder.getContext().getCamelContextExtension()
          .addInterceptStrategy(new NodeIdInterceptStrategy());
    }
  }

  /**
   * Registers the poll tracing strategy once per CamelContext.
   *
   * <p>{@code poll()} and {@code pollEnrich()} emit no Camel events, so the hop is reconstructed from
   * the node itself. Like every intercept strategy it has to be added before the routes are reified.
   *
   * @param routeBuilder the route builder being configured.
   */
  private void addPollInterceptStrategy(RouteBuilder routeBuilder) {
    boolean alreadyRegistered = routeBuilder.getContext().getCamelContextExtension()
        .getInterceptStrategies().stream()
        .anyMatch(PollInterceptStrategy.class::isInstance);

    if (!alreadyRegistered) {
      routeBuilder.getContext().getCamelContextExtension()
          .addInterceptStrategy(new PollInterceptStrategy(tracerService));
    }
  }

}
