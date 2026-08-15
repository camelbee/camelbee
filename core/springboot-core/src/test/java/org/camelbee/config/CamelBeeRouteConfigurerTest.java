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

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringBootTest;
import org.camelbee.debugger.controller.ContextController;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.tracers.ExchangeCompletedEventTracer;
import org.camelbee.tracers.ExchangeCreatedEventTracer;
import org.camelbee.tracers.ExchangeSendingEventTracer;
import org.camelbee.tracers.ExchangeSentEventTracer;
import org.camelbee.tracers.NodeIdInterceptStrategy;
import org.camelbee.tracers.PollInterceptStrategy;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@CamelSpringBootTest
@TestPropertySource(properties = "camelbee.tracer-enabled=true")
@SpringBootApplication
@Import({TracerService.class,
    MessageService.class,
    LoggingService.class,
    ExchangeCreatedEventTracer.class,
    ExchangeSendingEventTracer.class,
    ExchangeSentEventTracer.class,
    ExchangeCompletedEventTracer.class,
    RouteContextService.class,
    // Conditional beans still honour their own @ConditionalOnProperty when imported this way - see
    // shouldNotRegisterContextControllerWhenContextEnabledIsUnset.
    ContextController.class
})
@Configuration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CamelBeeRouteConfigurerTest {

  @Autowired
  ProducerTemplate producerTemplate;

  @Autowired
  CamelContext camelContext;

  @Autowired
  TracerService tracerService;

  @Autowired
  MessageService messageService;

  @EndpointInject("mock:test")
  MockEndpoint mockEndpoint;

  /**
   * required = false rather than a plain {@code @Autowired ContextController} so a missing bean is
   * an assertion, not a context startup failure - see
   * {@link #shouldNotRegisterContextControllerWhenContextEnabledIsUnset}.
   */
  @Autowired(required = false)
  ContextController contextController;

  @Configuration
  static class TestConfig {

    private final CamelBeeRouteConfigurer camelBeeRouteConfigurer;

    public TestConfig(CamelBeeRouteConfigurer camelBeeRouteConfigurer) {
      this.camelBeeRouteConfigurer = camelBeeRouteConfigurer;
    }

    @Bean
    RoutesBuilder route() {
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
   * intended effect in a real Spring context: both intercept strategies are registered by
   * {@code configureRoute} and the node id reaches the message service.
   *
   * <p>Registration is runtime specific - standalone does it in {@code CamelBee.attach()}, these two
   * in the configurer - and it is the one part of the tracing chain the shared unit tests cannot
   * cover, because they add the strategies to a hand-built context themselves.
   */
  @Test
  void shouldTraceEipHopsThroughTheConfiguredRoute() {
    tracerService.activateTracing(true);
    messageService.reset();

    producerTemplate.sendBody("direct:test", "testMessage");

    assertThat(messageService.getMessageList())
        .as("enrich hop names the node that performed it")
        .anySatisfy(message -> {
          assertThat(message.getEndpoint()).isEqualTo("direct://enrichTarget");
          assertThat(message.getEndpointId()).startsWith("enrich");
        });

    // PollInterceptStrategy cannot be exercised here without a pollable component on the test
    // classpath, so assert directly that configureRoute registered it. Its behaviour is covered by
    // PollTracingTest.
    assertThat(camelContext.getCamelContextExtension().getInterceptStrategies())
        .as("both strategies are registered by configureRoute")
        .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(NodeIdInterceptStrategy.class))
        .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(PollInterceptStrategy.class));
  }

  /**
   * {@code camelbee.context-enabled} is never set here - only {@code tracer-enabled} is, via
   * {@code @TestPropertySource} above - so this proves its actual default under a real Spring
   * context: fails closed, same as {@code camelbee.tracer-enabled} - a consumer who forgets to set
   * it gets no CamelBee endpoints at all rather than an unintentionally exposed one.
   * {@code @ConditionalOnProperty}'s own default ({@code matchIfMissing = false}) is what makes this
   * true; a future edit accidentally adding {@code matchIfMissing = true} to
   * {@link ContextController} would silently flip this for every consumer that doesn't set the
   * property, and this is the one test that would catch it.
   */
  @Test
  void shouldNotRegisterContextControllerWhenContextEnabledIsUnset() {
    assertThat(contextController).isNull();
  }

}
