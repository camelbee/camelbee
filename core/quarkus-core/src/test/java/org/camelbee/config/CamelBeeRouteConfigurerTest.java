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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.tracers.ExchangeCompletedEventTracer;
import org.camelbee.tracers.ExchangeCreatedEventTracer;
import org.camelbee.tracers.ExchangeSendingEventTracer;
import org.camelbee.tracers.ExchangeSentEventTracer;
import org.camelbee.tracers.NodeIdInterceptStrategy;
import org.camelbee.tracers.PollInterceptStrategy;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CamelBeeRouteConfigurerTest extends CamelQuarkusTestSupport {

  @Inject
  ProducerTemplate producerTemplate;

  @EndpointInject("mock:test")
  MockEndpoint mockEndpoint;

  @Inject
  CamelBeeRouteConfigurer camelBeeRouteConfigurer;

  @Inject
  TracerService tracerService;

  @Inject
  MessageService messageService;

  @Inject
  ExchangeCreatedEventTracer exchangeCreatedEventTracer;

  @Inject
  ExchangeSendingEventTracer exchangeSendingEventTracer;

  @Inject
  ExchangeSentEventTracer exchangeSentEventTracer;

  @Inject
  ExchangeCompletedEventTracer exchangeCompletedEventTracer;

  @Inject
  RouteContextService routeContextService;

  @Override
  protected RouteBuilder createRouteBuilder() {
    return new RouteBuilder() {

      @Override
      public void configure() throws Exception {
        camelBeeRouteConfigurer.configureRoute(this);

        from("direct:test")
            // an enrich, because Camel supplies no history node id for a send performed inside an
            // EIP - a node id on that hop can only have come from NodeIdInterceptStrategy
            .enrich("direct:enrichTarget")
            .to("mock:test");

        from("direct:enrichTarget").to("mock:enriched");
      }
    };
  }

  @Test
  void shouldAutowiredProducerTemplate() {
    assertNotNull(producerTemplate);
  }

  @Test
  void shouldInjectEndpoint() throws InterruptedException {
    mockEndpoint.setExpectedMessageCount(1);
    producerTemplate.sendBody("direct:test", "testMessage");
    mockEndpoint.assertIsSatisfied();
  }

  /**
   * The other tests here prove the configurer wires up without exploding. This one proves it has the
   * intended effect in a real CDI container: both intercept strategies are registered by
   * {@code configureRoute} and their hops reach the message service.
   *
   * <p>Registration is runtime specific - standalone does it in {@code CamelBee.attach()}, these two
   * in the configurer - and it is the one part of the tracing chain the shared unit tests cannot
   * cover, because they add the strategies to a hand-built context themselves.
   */
  @Test
  void shouldTraceEipAndPollHopsThroughTheConfiguredRoute() throws InterruptedException {
    tracerService.activateTracing(true);
    messageService.reset();

    producerTemplate.sendBody("direct:test", "testMessage");

    List<Message> traced = messageService.getMessageList();

    assertThat(traced)
        .as("enrich hop names the node that performed it")
        .anySatisfy(message -> {
          assertThat(message.getEndpoint()).isEqualTo("direct://enrichTarget");
          assertThat(message.getEndpointId()).startsWith("enrich");
        });

    // PollInterceptStrategy cannot be exercised here without a pollable component on the test
    // classpath, so assert directly that configureRoute registered it. Its behaviour is covered by
    // PollTracingTest.
    assertThat(context().getCamelContextExtension().getInterceptStrategies())
        .as("both strategies are registered by configureRoute")
        .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(NodeIdInterceptStrategy.class))
        .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(PollInterceptStrategy.class));
  }
}
