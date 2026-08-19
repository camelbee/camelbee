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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.camelbee.tracers.NodeIdInterceptStrategy;
import org.camelbee.tracers.PollInterceptStrategy;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link CamelBee#attach}, standalone's equivalent of the framework cores'
 * {@code CamelBeeRouteConfigurer}/{@code CamelBeeEventNotifierConfigurer} - the one part of the
 * wiring the rest of this module's unit tests cannot cover, because they add the tracer pieces to a
 * hand-built context themselves rather than going through the real entry point. Neither
 * {@code CamelBeeConfigTest} (which only resolves the config flags) nor
 * {@code CamelBeeHttpEndpointsTest}/{@code CamelBeeHttpEndpointsRegisterTest} (which construct
 * {@code CamelBeeHttpEndpoints} directly) exercise {@code attach()} itself.
 */
class CamelBeeTest {

  private static Properties properties(String key, String value) {
    Properties properties = new Properties();
    properties.setProperty(key, value);
    return properties;
  }

  @Test
  void attachRegistersBothInterceptStrategiesByDefault() throws Exception {
    try (CamelContext context = new DefaultCamelContext()) {
      CamelBee.attach(context);

      assertThat(context.getCamelContextExtension().getInterceptStrategies())
          .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(NodeIdInterceptStrategy.class))
          .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(PollInterceptStrategy.class));
    }
  }

  @Test
  void attachSkipsBothInterceptStrategiesWhenRouteConfigurerIsDisabled() throws Exception {
    try (CamelContext context = new DefaultCamelContext()) {
      context.getPropertiesComponent()
          .setInitialProperties(properties("camelbee.route-configurer-enabled", "false"));

      CamelBee.attach(context);

      assertThat(context.getCamelContextExtension().getInterceptStrategies())
          .noneMatch(NodeIdInterceptStrategy.class::isInstance)
          .noneMatch(PollInterceptStrategy.class::isInstance);
    }
  }

  @Test
  void attachRegistersTheEventNotifierByDefault() throws Exception {
    try (CamelContext context = new DefaultCamelContext()) {
      CamelBee.attach(context);

      assertThat(context.getManagementStrategy().getEventNotifiers())
          .anySatisfy(notifier -> assertThat(notifier).isInstanceOf(CamelBeeEventNotifier.class));
    }
  }

  @Test
  void attachSkipsTheEventNotifierWhenDisabled() throws Exception {
    try (CamelContext context = new DefaultCamelContext()) {
      context.getPropertiesComponent().setInitialProperties(properties("camelbee.notifier-enabled", "false"));

      CamelBee.attach(context);

      assertThat(context.getManagementStrategy().getEventNotifiers())
          .noneMatch(CamelBeeEventNotifier.class::isInstance);
    }
  }

  /**
   * The tests above prove attach() wires the pieces in without exploding. This one proves it has the
   * intended effect end to end: an enrich performs a send inside an EIP, which Camel reports no
   * history node id for - a node id on that hop can only have come from {@link NodeIdInterceptStrategy},
   * wired here through the real {@code attach()} entry point rather than constructed directly the way
   * {@code NodeIdInterceptStrategyTest} does.
   *
   * <p>{@code camelbee.logging-enabled=true} rather than activating tracing at runtime: attach()
   * builds its own {@code TracerService} internally and does not expose it, so there is no handle to
   * call {@code activateTracing(true)} on from outside - logging-enabled alone is also enough to make
   * {@code TracerService.isActive()} true, which is what NodeIdInterceptStrategy checks before
   * stamping (see this session's isActive-guard optimization).
   */
  @Test
  void wiredNodeIdInterceptStrategyStampsANodeIdThroughAnEip() throws Exception {
    try (CamelContext context = new DefaultCamelContext()) {
      context.getPropertiesComponent().setInitialProperties(properties("camelbee.logging-enabled", "true"));
      context.setMessageHistory(false);

      CamelBee.attach(context);

      List<String> seenNodeIds = new ArrayList<>();
      context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {

        @Override
        public void notify(CamelEvent event) {
          if (event instanceof CamelEvent.ExchangeSendingEvent sending
              && "direct://enrichTarget".equals(sending.getEndpoint().getEndpointUri())) {
            seenNodeIds.add(sending.getExchange().getProperty(CamelBeeConstants.CAMELBEE_NODE_ID, String.class));
          }
        }
      });

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          from("direct:start").routeId("startRoute")
              .enrich("direct:enrichTarget")
              .to("mock:test");

          from("direct:enrichTarget").routeId("enrichTargetRoute").to("mock:enriched");
        }
      });

      context.start();
      context.createProducerTemplate().sendBody("direct:start", "hello");

      assertThat(seenNodeIds).hasSize(1);
      assertThat(seenNodeIds.get(0)).startsWith("enrich");
    }
  }
}
