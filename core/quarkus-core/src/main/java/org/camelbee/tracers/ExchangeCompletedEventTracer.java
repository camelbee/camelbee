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
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.service.MessageService;
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

  private final MessageService messageService;

  public ExchangeCompletedEventTracer(MessageService messageService) {
    this.messageService = messageService;
  }

  /**
   * Trace ExchangeCompletedEvent.
   *
   * @param event The ExchangeCompletedEvent.
   * @return The Messages.
   */
  public Message traceEvent(ExchangeCompletedEvent event) {

    Exchange exchange = event.getExchange();

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

      return processCompletedMessage(exchange, responseCompletedBody, responseHeaders);

    } catch (Exception e) {
      LOGGER.warn("Could not trace ExchangeCompletedEvent: {} with exception: {}", exchange, e);
    }
    return null;
  }

  private Message processCompletedMessage(Exchange exchange, String responseCompletedBody, String requestHeaders) {

    Deque<String> routeStack = (Deque<String>) exchange.getProperty(CURRENT_ROUTE_TRACE_STACK);

    if (routeStack == null || routeStack.isEmpty()) {
      LOGGER.warn("Empty or null route stack in ExchangeCompletedEvent for exchange: {}", exchange.getExchangeId());
      return null;
    }

    final String currentRoute = routeStack.pop();
    final String callerRoute = routeStack.peek();

    MessageType messageType = MessageType.RESPONSE;

    String errorMessage = TracerUtils.handleError(exchange);

    if (errorMessage != null) {
      messageType = MessageType.ERROR_RESPONSE;
    }

    /*
      if this ExchangeCompletedEvent is triggered after another ExchangeCompletedEvent
      then endpointId will be null, Camel does not keep track of nested getHistoryNodeId
      that's why we need the stack
     */
    final String endpointId = ((DefaultExchange) exchange).getExchangeExtension().getHistoryNodeId();

    return new Message(exchange.getExchangeId(), MessageEventType.COMPLETED, responseCompletedBody, requestHeaders, callerRoute,
        currentRoute, endpointId, messageType, errorMessage);

  }

}
