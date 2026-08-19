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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.Optional;
import org.apache.camel.CamelContext;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.security.AuthService;
import org.camelbee.tracers.ExchangeCompletedEventTracer;
import org.camelbee.tracers.ExchangeCreatedEventTracer;
import org.camelbee.tracers.ExchangeSendingEventTracer;
import org.camelbee.tracers.ExchangeSentEventTracer;
import org.camelbee.tracers.TracerService;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Makes the framework-neutral engine in {@code camelbee-core} available as CDI beans.
 *
 * <p>The engine classes carry no CDI annotations, so that one implementation can serve Quarkus,
 * Spring Boot and plain {@code camel-main}. That means the DI graph has to be declared somewhere,
 * and this is the Quarkus half of it: configuration is read here, through MicroProfile Config, and
 * passed to plain constructors.
 *
 * <p>Defaults are repeated here rather than left to the constructors deliberately - they are part of
 * the published configuration contract and must stay identical across the three runtimes. The
 * matching Spring Boot class declares the same values.
 */
@ApplicationScoped
public class CamelBeeCoreProducers {

  @Produces
  @ApplicationScoped
  LoggingService loggingService() {
    return new LoggingService();
  }

  @Produces
  @ApplicationScoped
  MessageService messageService(
      @ConfigProperty(name = "camelbee.tracer-max-messages-count", defaultValue = "1000") long maxTracedMessageCount) {
    return new MessageService(maxTracedMessageCount);
  }

  /**
   * Builds the topology service.
   *
   * <p>Placeholder resolution goes through MicroProfile {@code Config}, which is what this core used
   * before the engine was extracted - deliberately preserved rather than switched to Camel's own
   * {@code PropertiesComponent}, so the extraction changes no behaviour.
   *
   * @param camelContext the context whose routes are published.
   * @param config       the MicroProfile configuration.
   * @return the topology service.
   */
  @Produces
  @ApplicationScoped
  RouteContextService routeContextService(CamelContext camelContext, Config config) {
    return new RouteContextService(camelContext,
        key -> config.getOptionalValue(key, String.class));
  }

  @Produces
  @ApplicationScoped
  ExchangeCreatedEventTracer exchangeCreatedEventTracer() {
    return new ExchangeCreatedEventTracer();
  }

  @Produces
  @ApplicationScoped
  ExchangeSendingEventTracer exchangeSendingEventTracer(MessageService messageService,
      RouteContextService routeContextService) {
    return new ExchangeSendingEventTracer(messageService, routeContextService);
  }

  @Produces
  @ApplicationScoped
  ExchangeSentEventTracer exchangeSentEventTracer() {
    return new ExchangeSentEventTracer();
  }

  @Produces
  @ApplicationScoped
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
  @Produces
  @ApplicationScoped
  @SuppressWarnings("java:S107")
  TracerService tracerService(
      @ConfigProperty(name = "camelbee.logging-enabled", defaultValue = "false") boolean loggingEnabled,
      @ConfigProperty(name = "camelbee.tracer-enabled", defaultValue = "false") boolean tracerEnabled,
      @ConfigProperty(name = "camelbee.tracer-max-idle-time", defaultValue = "300000") long tracerIdleTime,
      ExchangeCreatedEventTracer createdTracer,
      ExchangeSendingEventTracer sendingTracer,
      ExchangeSentEventTracer sentTracer,
      ExchangeCompletedEventTracer completedTracer,
      MessageService messageService,
      LoggingService loggingService) {
    return new TracerService(loggingEnabled, tracerEnabled, tracerIdleTime, createdTracer,
        sendingTracer, sentTracer, completedTracer, messageService, loggingService);
  }

  /**
   * Builds the authentication gate.
   *
   * <p>Enabled by default. With no password configured, {@link AuthService} generates one and logs
   * it rather than falling back to a fixed default - a documented default credential protects
   * nobody while looking as though it does.
   *
   * @param enabled  whether the endpoints require a token.
   * @param username the login name.
   * @param password the password; empty means "generate one and log it".
   * @param timeout  the idle window in milliseconds.
   * @return the auth service.
   */
  @Produces
  @ApplicationScoped
  AuthService authService(
      @ConfigProperty(name = "camelbee.auth-enabled", defaultValue = "true") boolean enabled,
      @ConfigProperty(name = "camelbee.username", defaultValue = "camelbee") String username,
      @ConfigProperty(name = "camelbee.password") Optional<String> password,
      @ConfigProperty(name = "camelbee.session-timeout", defaultValue = "120000") long timeout) {
    // Optional rather than a blank defaultValue: Quarkus rejects an empty default for a String.
    return enabled
        ? new AuthService(true, username, password.orElse(null), timeout)
        : AuthService.disabled();
  }
}
