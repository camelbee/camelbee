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
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_NAME;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.camelbee.constants.CamelBeeConstants.DIRECT;
import static org.camelbee.constants.CamelBeeConstants.LAST_DIRECT_ROUTE;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Deque;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
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
 * Responsible for tracing ExchangeSentEventTracer.
 */
@ApplicationScoped
@SuppressWarnings("PMD.TooManyStaticImports")
public class ExchangeSentEventTracer {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeSentEventTracer.class);

  private final MessageService messageService;

  public ExchangeSentEventTracer(MessageService messageService) {
    this.messageService = messageService;
  }

  /**
   * Trace ExchangeSentEvent.
   *
   * @param event The ExchangeSentEvent.
   * @return The Messages.
   */
  public Message traceEvent(ExchangeSentEvent event) {

    Exchange exchange = event.getExchange();

    try {
      /*
        endpoint called from ProducerController is also intercepted here
        which we should not put into the messages
      */
      if (exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE) != null) {
        return null;
      }

      final String responseSentBody = ExchangeUtils.readBodyAsString(exchange, true);
      final var requestHeaders = ExchangeUtils.getHeaders(exchange);

      return processSentMessage(exchange, responseSentBody, requestHeaders);

    } catch (Exception e) {
      LOGGER.warn("Could not trace ExchangeSentEvent: {} with exception: {}", exchange, e);
    }

    return null;
  }

  private Message processSentMessage(Exchange exchange, String responseSentBody, String requestHeaders) {

    Deque<String> routeStack = (Deque<String>) exchange.getProperty(CURRENT_ROUTE_TRACE_STACK);

    if (routeStack == null || routeStack.isEmpty()) {
      LOGGER.warn("Empty or null route stack in ExchangeSentEvent for exchange: {}", exchange.getExchangeId());
      return null;
    }

    final String currentRoute = routeStack.pop();
    String callerRoute = routeStack.peek();

    /*
     set the previous route (callerRoute) as the current route
     which would be used in SendToRequestTracers
     */
    exchange.setProperty(CURRENT_ROUTE_NAME, callerRoute);

    MessageType messageType = MessageType.RESPONSE;

    String errorMessage = TracerUtils.handleError(exchange);

    if (errorMessage != null) {
      messageType = MessageType.ERROR_RESPONSE;
    }

    /*
      if this ExchangeSentEvent is triggered after another ExchangeSentEvent
      then endpointId will be null, Camel does not keep track of nested getHistoryNodeId
      that's why we need the stack
     */
    final String endpointId = ((DefaultExchange) exchange).getExchangeExtension().getHistoryNodeId();

    if (endpointId == null && callerRoute != null && !callerRoute.startsWith(DIRECT) && !currentRoute.startsWith(DIRECT)) {
      //dynamicRouter with 2 times producer endpoint like mock:C and mock:D
      //change Caller route find the previous direcrRouteName
      callerRoute = exchange.getProperty(LAST_DIRECT_ROUTE, String.class);
    }

    return new Message(exchange.getExchangeId(), MessageEventType.SENT, responseSentBody, requestHeaders, callerRoute,
        currentRoute, endpointId, messageType, errorMessage);
  }

}
