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

import java.util.Optional;
import org.apache.camel.CamelContext;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.tracers.ExchangeCompletedEventTracer;
import org.camelbee.tracers.ExchangeCreatedEventTracer;
import org.camelbee.tracers.ExchangeSendingEventTracer;
import org.camelbee.tracers.ExchangeSentEventTracer;
import org.camelbee.tracers.TracerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Makes the framework-neutral engine in {@code camelbee-core} available as Spring beans.
 *
 * <p>The engine classes carry no Spring annotations, so that one implementation can serve Quarkus,
 * Spring Boot and plain {@code camel-main}. That means the DI graph has to be declared somewhere,
 * and this is the Spring Boot half of it: configuration is read here and passed to plain
 * constructors.
 *
 * <p>Defaults are repeated here rather than left to the constructors deliberately - they are part of
 * the published configuration contract and must stay identical across the three runtimes. The
 * matching Quarkus class declares the same values.
 *
 * <p><b>This class must stay component-scannable.</b> The documented setup is
 * {@code @ComponentScan(basePackages = {"org.camelbee", ...})}, and before the extraction the engine
 * classes carried their own stereotypes and were picked up by exactly that scan. They no longer do,
 * so this class is what the scan has to find instead - were it invisible, every existing Spring Boot
 * application would start failing to autowire {@code TracerService}.
 *
 * <p>{@link CamelBeeTracingConfiguration} imports it for the other case: a sliced route test that
 * lists its configuration classes explicitly and never scans at all.
 * {@code CamelBeeTracingConfigurationTest} guards both halves.
 */
@Configuration
public class CamelBeeCoreBeans {

  @Bean
  LoggingService loggingService() {
    return new LoggingService();
  }

  @Bean
  MessageService messageService(
      @Value("${camelbee.tracer-max-messages-count:1000}") long maxTracedMessageCount) {
    return new MessageService(maxTracedMessageCount);
  }

  /**
   * Builds the topology service.
   *
   * <p>Placeholder resolution goes through Spring's {@code Environment}, which is what this core
   * used before the engine was extracted - deliberately preserved rather than switched to Camel's
   * own {@code PropertiesComponent}, so the extraction changes no behaviour.
   *
   * @param camelContext the context whose routes are published.
   * @param environment  the Spring environment.
   * @return the topology service.
   */
  @Bean
  RouteContextService routeContextService(CamelContext camelContext, Environment environment) {
    return new RouteContextService(camelContext,
        key -> Optional.ofNullable(environment.getProperty(key)));
  }

  @Bean
  ExchangeCreatedEventTracer exchangeCreatedEventTracer() {
    return new ExchangeCreatedEventTracer();
  }

  @Bean
  ExchangeSendingEventTracer exchangeSendingEventTracer(MessageService messageService,
      RouteContextService routeContextService) {
    return new ExchangeSendingEventTracer(messageService, routeContextService);
  }

  @Bean
  ExchangeSentEventTracer exchangeSentEventTracer() {
    return new ExchangeSentEventTracer();
  }

  @Bean
  ExchangeCompletedEventTracer exchangeCompletedEventTracer() {
    return new ExchangeCompletedEventTracer();
  }

  /**
   * Builds the tracer service.
   *
   * @param loggingEnabled  whether traced messages are written to the application log.
   * @param tracerEnabled   whether tracing may be armed at all.
   * @param tracerIdleTime  how long tracing stays armed without UI activity, in milliseconds.
   * @param createdTracer   the created-event tracer.
   * @param sendingTracer   the sending-event tracer.
   * @param sentTracer      the sent-event tracer.
   * @param completedTracer the completed-event tracer.
   * @param messageService  the message store.
   * @param loggingService  the structured logger.
   * @return the tracer service.
   */
  @Bean
  @SuppressWarnings("java:S107")
  TracerService tracerService(
      @Value("${camelbee.logging-enabled:false}") boolean loggingEnabled,
      @Value("${camelbee.tracer-enabled:false}") boolean tracerEnabled,
      @Value("${camelbee.tracer-max-idle-time:300000}") long tracerIdleTime,
      ExchangeCreatedEventTracer createdTracer,
      ExchangeSendingEventTracer sendingTracer,
      ExchangeSentEventTracer sentTracer,
      ExchangeCompletedEventTracer completedTracer,
      MessageService messageService,
      LoggingService loggingService) {
    return new TracerService(loggingEnabled, tracerEnabled, tracerIdleTime, createdTracer,
        sendingTracer, sentTracer, completedTracer, messageService, loggingService);
  }
}
