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
import static org.camelbee.constants.CamelBeeConstants.INITIAL_EXCHANGE_ID;

import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.utils.ExchangeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Responsible for tracing ExchangeCreatedEvent as an entry point.
 */
@Component
public class ExchangeCreatedEventTracer {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeCreatedEventTracer.class);

  private final MessageService messageService;

  public ExchangeCreatedEventTracer(MessageService messageService) {
    this.messageService = messageService;
  }

  /**
   * Trace ExchangeCreatedEvent.
   *
   * @param event The ExchangeCreatedEvent.
   * @return The Messages.
   */
  public Message traceEvent(ExchangeCreatedEvent event) {

    Exchange exchange = event.getExchange();

    //  trace only the first created Exchange instance not the duplicates
    if (exchange.getProperty(INITIAL_EXCHANGE_ID) != null) {
      return null;
    }
    exchange.setProperty(INITIAL_EXCHANGE_ID, exchange.getExchangeId());

    /*
     endpoint called from ProducerController is also intercepted here
     which we should not put into the messages
     */
    if (exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE) != null) {
      return null;
    }

    try {

      final String directRequestBody = ExchangeUtils.readBodyAsString(exchange, false);

      final var requestHeaders = ExchangeUtils.getHeaders(exchange);

      return processCreatedMessage(exchange, directRequestBody, requestHeaders);

    } catch (Exception e) {
      LOGGER.warn("Could not trace ExchangeCreatedEvent: {} with exception: {}", exchange, e);
      return null;
    }

  }

  private Message processCreatedMessage(Exchange exchange, String directRequestBody, String requestHeaders) {

    final String currentRouteName = (String) exchange.getProperty(Exchange.TO_ENDPOINT);

    /*
     when an exchange created by platform-http producer component then exchange.getFromRouteId() is null
     so we need to find the routeId of the first entrypoint route in the first sending event notifier
     note: the other producer component has this value set to the entrypoint route id.
     */
    final String initialRoute = exchange.getFromRouteId();

    if (initialRoute != null) {

      Deque<String> routeStack = new ArrayDeque<>();
      routeStack.push(initialRoute);

      exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, routeStack);
    }

    exchange.setProperty(CURRENT_ROUTE_NAME, currentRouteName != null ? currentRouteName : initialRoute);

    final String endpointId = ((DefaultExchange) exchange).getExchangeExtension().getHistoryNodeId();

    return new Message(exchange.getExchangeId(), MessageEventType.CREATED, directRequestBody, requestHeaders, initialRoute,
        currentRouteName, endpointId, MessageType.REQUEST, null);

  }

}
