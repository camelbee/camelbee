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

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringBootTest;
import org.camelbee.tracers.NodeIdInterceptStrategy;
import org.camelbee.tracers.PollInterceptStrategy;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Reproduces the context a generated route unit test builds, and pins both halves of the contract.
 *
 * <p>The class list below is exactly what the archetype generates - a route, the configurer and the
 * tracing configuration - and nothing is component scanned, which is the point: the configurer's
 * required {@link TracerService} has to come from {@link CamelBeeTracingConfiguration} alone. Before
 * that class existed, this context failed to start.
 */
@CamelSpringBootTest
@EnableAutoConfiguration
@SpringBootTest(classes = {
    CamelBeeTracingConfigurationTest.SlicedRoute.class,
    CamelBeeRouteConfigurer.class,
    CamelBeeTracingConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CamelBeeTracingConfigurationTest {

  @Autowired
  ProducerTemplate producerTemplate;

  @Autowired
  CamelContext camelContext;

  @Autowired
  ApplicationContext applicationContext;

  @EndpointInject("mock:test")
  MockEndpoint mockEndpoint;

  /**
   * Stands in for a generated route: a {@link RouteBuilder} that takes the configurer through its
   * constructor and calls {@code configureRoute} on itself.
   */
  static class SlicedRoute extends RouteBuilder {

    private final CamelBeeRouteConfigurer camelBeeRouteConfigurer;

    SlicedRoute(CamelBeeRouteConfigurer camelBeeRouteConfigurer) {
      this.camelBeeRouteConfigurer = camelBeeRouteConfigurer;
    }

    @Override
    public void configure() {
      camelBeeRouteConfigurer.configureRoute(this);

      from("direct:test").to("mock:test");
    }
  }

  @Test
  void shouldConfigureRouteInASlicedContext() throws InterruptedException {
    mockEndpoint.setExpectedMessageCount(1);
    producerTemplate.sendBody("direct:test", "testMessage");
    mockEndpoint.assertIsSatisfied();
  }

  @Test
  void shouldRegisterBothInterceptStrategies() {
    assertThat(camelContext.getCamelContextExtension().getInterceptStrategies())
        .as("the tracing configuration supplies what poll tracing needs, so both are registered")
        .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(NodeIdInterceptStrategy.class))
        .anySatisfy(strategy -> assertThat(strategy).isInstanceOf(PollInterceptStrategy.class));
  }

  @Test
  void shouldContributeExactlyOneOfEachTracingBean() {
    assertThat(applicationContext.getBeanNamesForType(TracerService.class))
        .as("a second definition would make every autowire by type ambiguous")
        .hasSize(1);
  }

  /**
   * The other half of the contract: an application scanning {@code org.camelbee} must not pick this
   * class up, or it would register the imported beans a second time - under their fully qualified
   * names, since that is how imported classes are named - and every autowire by type would become
   * ambiguous. Asserted against the scanner itself rather than a second context, because it is the
   * scanner's include filter that decides, and over the whole {@code org.camelbee} tree, which is
   * what a generated application's {@code @ComponentScan} covers.
   */
  @Test
  void shouldNotBeComponentScannable() {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);

    assertThat(scanner.findCandidateComponents("org.camelbee"))
        .as("an application's scan of org.camelbee must not find the tracing configuration")
        .noneSatisfy(candidate -> assertThat(candidate.getBeanClassName())
            .isEqualTo(CamelBeeTracingConfiguration.class.getName()))
        .as("nor the engine classes themselves - they moved to camelbee-core and carry no Spring "
            + "annotations, so the scan cannot find them and CamelBeeCoreBeans contributes them")
        .noneSatisfy(candidate -> assertThat(candidate.getBeanClassName())
            .isEqualTo(TracerService.class.getName()))
        .as("but it MUST find CamelBeeCoreBeans: the documented setup is a scan of org.camelbee, "
            + "and that scan is now the only thing standing between an application and a missing "
            + "TracerService bean")
        .anySatisfy(candidate -> assertThat(candidate.getBeanClassName())
            .isEqualTo(CamelBeeCoreBeans.class.getName()))
        .as("and the configurer, or this test proves nothing")
        .anySatisfy(candidate -> assertThat(candidate.getBeanClassName())
            .isEqualTo(CamelBeeRouteConfigurer.class.getName()));
  }

}
