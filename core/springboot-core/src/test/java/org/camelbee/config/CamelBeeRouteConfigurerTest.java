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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringBootTest;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.tracers.ExchangeCompletedEventTracer;
import org.camelbee.tracers.ExchangeCreatedEventTracer;
import org.camelbee.tracers.ExchangeSendingEventTracer;
import org.camelbee.tracers.ExchangeSentEventTracer;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

@CamelSpringBootTest
@SpringBootApplication
@Import({TracerService.class,
    MessageService.class,
    LoggingService.class,
    ExchangeCreatedEventTracer.class,
    ExchangeSendingEventTracer.class,
    ExchangeSentEventTracer.class,
    ExchangeCompletedEventTracer.class,
    RouteContextService.class
})
@Configuration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CamelBeeRouteConfigurerTest {

  @Autowired
  ProducerTemplate producerTemplate;

  @EndpointInject("mock:test")
  MockEndpoint mockEndpoint;

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

          from("direct:test").to("mock:test");
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

}
