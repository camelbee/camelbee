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

package org.camelbee.tracers;

import static org.camelbee.constants.CamelBeeConstants.DIRECT;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.logging.LoggingService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CamelBee Tracer Service collects request/responses of Camel Components.
 */
@ApplicationScoped
@RegisterForReflection(fields = false)
public class TracerService {

  private boolean loggingEnabled;
  private boolean tracerEnabled;
  private final long tracerIdleTime;
  private final ExchangeCreatedEventTracer exchangeCreatedEventTracer;
  private final ExchangeSendingEventTracer exchangeSendingEventTracer;
  private final ExchangeSentEventTracer exchangeSentEventTracer;
  private final ExchangeCompletedEventTracer exchangeCompletedEventTracer;

  private final MessageService messageService;
  private final LoggingService loggingService;

  private AtomicBoolean tracingActivated = new AtomicBoolean(false);

  private AtomicLong lastTracingActivatedTime = new AtomicLong(System.currentTimeMillis());

  /**
   * Constructor.
   *
   * @param loggingEnabled               The loggingEnabled.
   * @param tracerEnabled                The tracerEnabled.
   * @param tracerIdleTime               The tracerIdleTime.
   * @param exchangeCreatedEventTracer   The exchangeCreatedEventTracer.
   * @param exchangeSendingEventTracer   The exchangeSendingEventTracer.
   * @param exchangeSentEventTracer      The exchangeSentEventTracer.
   * @param exchangeCompletedEventTracer The exchangeCompletedEventTracer.
   */
  public TracerService(@ConfigProperty(name = "camelbee.logging-enabled", defaultValue = "false") boolean loggingEnabled,
      @ConfigProperty(name = "camelbee.tracer-enabled", defaultValue = "false") boolean tracerEnabled,
      @ConfigProperty(name = "camelbee.tracer-max-idle-time", defaultValue = "300000") long tracerIdleTime,
      ExchangeCreatedEventTracer exchangeCreatedEventTracer,
      ExchangeSendingEventTracer exchangeSendingEventTracer, ExchangeSentEventTracer exchangeSentEventTracer,
      ExchangeCompletedEventTracer exchangeCompletedEventTracer, MessageService messageService,
      LoggingService loggingService) {
    this.loggingEnabled = loggingEnabled;
    this.tracerEnabled = tracerEnabled;
    this.tracerIdleTime = tracerIdleTime;
    this.exchangeCreatedEventTracer = exchangeCreatedEventTracer;
    this.exchangeSendingEventTracer = exchangeSendingEventTracer;
    this.exchangeSentEventTracer = exchangeSentEventTracer;
    this.exchangeCompletedEventTracer = exchangeCompletedEventTracer;
    this.messageService = messageService;
    this.loggingService = loggingService;
  }

  private boolean isActive() {
    return loggingEnabled || (tracerEnabled && isTracingActivated());
  }

  private boolean isDirectEndpointWithoutTracing(String endpointUri) {
    return !(tracerEnabled && isTracingActivated()) && endpointUri.startsWith(DIRECT);
  }

  /**
   * traceExchangeCreateEvent.
   *
   * @param exchangeCreatedEvent The exchange.
   */
  public void traceExchangeCreateEvent(ExchangeCreatedEvent exchangeCreatedEvent) {

    if (!isActive()) {
      return;
    }

    Message message = exchangeCreatedEventTracer.traceEvent(exchangeCreatedEvent);

    if (loggingEnabled) {
      loggingService.logMessage(message, "Request received:", false);
    }

    if (tracerEnabled && isTracingActivated()) {
      messageService.addMessage(message);
    }

  }

  /**
   * traceExchangeSendingEvent.
   *
   * @param exchangeSendingEvent The exchange.
   */
  public void traceExchangeSendingEvent(ExchangeSendingEvent exchangeSendingEvent) {

    if (!isActive()) {
      return;
    }

    if (isDirectEndpointWithoutTracing(exchangeSendingEvent.getEndpoint().getEndpointUri())) {
      return;
    }

    Message message = exchangeSendingEventTracer.traceEvent(exchangeSendingEvent);

    if (loggingEnabled) {
      loggingService.logMessage(message, "Request sent:", false);
    }

    if (tracerEnabled && isTracingActivated()) {
      messageService.addMessage(message);
    }

  }

  /**
   * traceExchangeSentEvent.
   *
   * @param exchangeSentEvent The exchange.
   */
  public void traceExchangeSentEvent(ExchangeSentEvent exchangeSentEvent) {

    if (!isActive()) {
      return;
    }

    if (isDirectEndpointWithoutTracing(exchangeSentEvent.getEndpoint().getEndpointUri())) {
      return;
    }

    Message message = exchangeSentEventTracer.traceEvent(exchangeSentEvent);

    if (loggingEnabled) {
      loggingService.logMessage(message, "Response received:", false);
    }

    if (tracerEnabled && isTracingActivated()) {
      messageService.addMessage(message);
    }

  }

  /**
   * traceExchangeCompletedEvent.
   *
   * @param exchangeCompletedEvent The exchange.
   */
  public void traceExchangeCompletedEvent(ExchangeCompletedEvent exchangeCompletedEvent) {

    if (!isActive()) {
      return;
    }

    Message message = exchangeCompletedEventTracer.traceEvent(exchangeCompletedEvent);

    if (loggingEnabled) {
      loggingService.logMessage(message, "Response completed:", false);
    }

    if (tracerEnabled && isTracingActivated()) {
      messageService.addMessage(message);
    }

  }

  /**
   * isTracingActivated.
   *
   * @return boolean The tracing status.
   */
  public boolean isTracingActivated() {

    // if CamelBee WebGL application is not active anymore and not calling the keepTracingActive api
    if (tracingActivated.get() && System.currentTimeMillis() - lastTracingActivatedTime.get() > tracerIdleTime) {
      tracingActivated.set(false);
    }

    return tracingActivated.get();
  }

  /**
   * setTracingEnabled.
   *
   * @param activated The tracing status.
   */
  public void activateTracing(boolean activated) {
    this.tracingActivated.set(activated);
  }

  /**
   * keepTracingActive.
   */
  public void keepTracingActive() {
    lastTracingActivatedTime.set(System.currentTimeMillis());
  }

}
