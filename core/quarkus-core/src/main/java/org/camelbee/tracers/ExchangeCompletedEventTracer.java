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

import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_PRODUCED_EXCHANGE;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.camelbee.constants.CamelBeeConstants.INITIAL_EXCHANGE_ID;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Deque;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.utils.ExchangeUtils;
import org.camelbee.utils.TracerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for tracing ExchangeCompletedEventTracer.
 */
@ApplicationScoped
public class ExchangeCompletedEventTracer {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeCompletedEventTracer.class);

  /**
   * Trace ExchangeCompletedEvent.
   *
   * @param event The ExchangeCompletedEvent.
   * @return The Messages.
   */
  public Message traceEvent(ExchangeCompletedEvent event) {
    return traceTermination(event.getExchange(), false);
  }

  /**
   * Trace ExchangeFailedEvent, which Camel fires <em>instead of</em> ExchangeCompletedEvent when an
   * exchange terminates with an unhandled exception - the two are mutually exclusive.
   *
   * <p>Without this the closing marker is simply absent for a failed exchange. On a producer-started
   * route the error still surfaces, because the enclosing send reports it on its own SENT; on a
   * consumer-started route (timer, file, jms - the common production shape) there is no enclosing
   * send, and the failure went unrecorded entirely: the trace ended at the last successful hop and
   * was indistinguishable from a route that finished cleanly.
   *
   * @param event The ExchangeFailedEvent.
   * @return The Message.
   */
  public Message traceEvent(ExchangeFailedEvent event) {
    return traceTermination(event.getExchange(), true);
  }

  private Message traceTermination(Exchange exchange, boolean failed) {

    try {
      /*
        endpoint called from ProducerController is also intercepted here
        which we should not put into the messages
      */
      if (exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE) != null) {
        return null;
      }

      //  trace completed event only for the first created Exchange instance
      if (exchange.getProperty(INITIAL_EXCHANGE_ID) == null
          || !exchange.getProperty(INITIAL_EXCHANGE_ID, String.class).equals(exchange.getExchangeId())) {
        return null;
      }

      final String responseCompletedBody = ExchangeUtils.readBodyAsString(exchange, true);
      final var responseHeaders = ExchangeUtils.getHeaders(exchange);

      return processCompletedMessage(exchange, responseCompletedBody, responseHeaders, failed);

    } catch (Exception e) {
      LOGGER.warn("Could not trace ExchangeCompletedEvent: {} with exception: {}", exchange, e);
    }
    return null;
  }

  private Message processCompletedMessage(Exchange exchange, String responseCompletedBody, String requestHeaders,
      boolean failed) {

    Deque<String> routeStack = (Deque<String>) exchange.getProperty(CURRENT_ROUTE_TRACE_STACK);

    if (routeStack == null || routeStack.isEmpty()) {
      LOGGER.warn("Empty or null route stack in ExchangeCompletedEvent for exchange: {}", exchange.getExchangeId());
      return null;
    }

    final String currentRoute = routeStack.pop();
    final String callerRoute = routeStack.peek();

    String errorMessage = TracerUtils.handleError(exchange, currentRoute);

    /*
     A failed exchange is reported as an error even when handleError returns nothing. That happens
     when the exception was already reported on an earlier hop - handleError deduplicates by the
     exception's identity - which is the producer-started case, where the enclosing send got there
     first. Suppressing the text avoids showing the same exception twice; keeping ERROR_RESPONSE
     keeps the closing marker from claiming the exchange ended cleanly.
     */
    MessageType messageType = failed || errorMessage != null
        ? MessageType.ERROR_RESPONSE
        : MessageType.RESPONSE;

    /*
      if this ExchangeCompletedEvent is triggered after another ExchangeCompletedEvent
      then endpointId will be null, Camel does not keep track of nested getHistoryNodeId
      that's why we need the stack
     */
    final String endpointId = ((DefaultExchange) exchange).getExchangeExtension().getHistoryNodeId();

    return TracerUtils.stampParentExchangeId(
        new Message(exchange.getExchangeId(), MessageEventType.COMPLETED, responseCompletedBody, requestHeaders, callerRoute,
            currentRoute, TracerUtils.resolveNodeId(exchange, endpointId), messageType, errorMessage), exchange);

  }

}
