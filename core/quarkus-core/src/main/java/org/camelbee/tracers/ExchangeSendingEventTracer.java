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
import static org.camelbee.constants.CamelBeeConstants.PREVIOUS_EXCHANGE_ID;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.StringUtils;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.utils.ExchangeUtils;
import org.camelbee.utils.TracerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for tracing ExchangeSendingEventTracer.
 */
@ApplicationScoped
@SuppressWarnings("PMD.TooManyStaticImports")
public class ExchangeSendingEventTracer {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeSendingEventTracer.class);

  private final MessageService messageService;

  private final RouteContextService routeContextService;

  public ExchangeSendingEventTracer(MessageService messageService, RouteContextService routeContextService) {
    this.messageService = messageService;
    this.routeContextService = routeContextService;
  }

  /**
   * Trace ExchangeSendingEvent.
   *
   * @param event The ExchangeSendingEvent.
   * @return The Messages.
   */
  public Message traceEvent(ExchangeSendingEvent event) {

    Exchange exchange = event.getExchange();

    /*
     endpoint called from ProducerController is also intercepted here
     which we should not put into the messages
     */
    if (exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE) != null) {
      return null;
    }

    try {

      final String endpointUri = event.getEndpoint().getEndpointUri();
      final String requestBody = ExchangeUtils.readBodyAsString(exchange, false);
      final var requestHeaders = ExchangeUtils.getHeaders(exchange);

      return processSendingMessage(exchange, endpointUri, requestBody, requestHeaders);

    } catch (Exception e) {
      LOGGER.warn("Could not trace ExchangeSendingEvent Exchange: {} with exception: {}", exchange, e);
    }

    return null;

  }

  private Message processSendingMessage(Exchange exchange, String endpointUri, String requestBody, String requestHeaders) {

    final String endpointId = ((DefaultExchange) exchange).getExchangeExtension().getHistoryNodeId();

    String currentRoute = endpointUri;

    Deque<String> routeStack = (Deque<String>) exchange.getProperty(CURRENT_ROUTE_TRACE_STACK);

    /*
     if null then in the initial ExchangeCreatedEvent the exchange.getFromRouteId() was null
     which happens with the platform-http producer component
     */
    if (routeStack == null) {
      routeStack = initializeRouteStack(exchange, endpointId);
    }

    Deque<String> clonedRouteStack;

    clonedRouteStack = adjustStack(exchange, routeStack);

    final String currentRouteProperty = (String) exchange.getProperty(CURRENT_ROUTE_NAME);

    clonedRouteStack.push(currentRoute);

    exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, clonedRouteStack);

    exchange.setProperty(CURRENT_ROUTE_NAME, currentRoute);

    if (currentRouteProperty.startsWith(DIRECT)) {
      exchange.setProperty(LAST_DIRECT_ROUTE, currentRouteProperty);
    }
    String routeId = null;
    if (endpointId == null && !currentRouteProperty.startsWith(DIRECT) && !currentRoute.startsWith(DIRECT)) {
      //dynamicRouter with 2 times producer endpoint next to each other like mock:D,mock:C
      //change Caller route find the previous directRouteName
      routeId = exchange.getProperty(LAST_DIRECT_ROUTE, String.class);
    } else {
      routeId = currentRouteProperty;
    }

    // if custom error is thrown then we need to handle that one as well.
    String errorMessage = TracerUtils.handleError(exchange);

    return new Message(exchange.getExchangeId(), MessageEventType.SENDING, requestBody, requestHeaders, routeId,
        currentRoute, endpointId, MessageType.REQUEST, errorMessage);
  }

  private Deque<String> adjustStack(Exchange exchange, Deque<String> routeStack) {
    Deque<String> clonedRouteStack;
    String previousExchangeId = (String) exchange.getProperty(PREVIOUS_EXCHANGE_ID);

    if (previousExchangeId != null && !previousExchangeId.equalsIgnoreCase(exchange.getExchangeId())) {

      /* clone the stack only if the exchangeId has changed
       which means there is a new cloned exchange with exchangeId
       */
      clonedRouteStack = new ArrayDeque<>(routeStack);

    } else {

      /*
       if the exchangeId is the same even it is a cloned exchange (which happens with the routeslip and dynamicRouter)
       dont clone the stack because it is still the same routestack which is executed serially
       */
      clonedRouteStack = routeStack;
    }

    exchange.setProperty(PREVIOUS_EXCHANGE_ID, exchange.getExchangeId());
    return clonedRouteStack;
  }

  private Deque<String> initializeRouteStack(Exchange exchange, String endpointId) {
    //find the actual consumer routeId
    final String actualCurrentRoute = getCallerRouteIdFromRouteContext(endpointId);

    Deque<String> routeStack;
    routeStack = new ArrayDeque<>();
    routeStack.push(actualCurrentRoute);

    /*
     set CURRENT_ROUTE_NAME property which was not set in the ExchangeCreatedEvent
     */
    exchange.setProperty(CURRENT_ROUTE_NAME, actualCurrentRoute);

    /*
     for only one time fix the empty routeId of the
     previous message created in the ExchangeCreatedEvent
     */
    messageService.getMessageList()
        .stream()
        .filter(p -> p.getExchangeId().equals(exchange.getExchangeId()))
        .forEach(q -> q.setRouteId(actualCurrentRoute));

    return routeStack;
  }

  private String getCallerRouteIdFromRouteContext(String endpointId) {

    List<CamelRoute> routes = routeContextService.getCamelRoutes();

    Optional<CamelRoute> routeOptional = routes.stream()
        .filter(p -> p.getOutputs().stream().anyMatch(q -> q.getId().equals(endpointId)))
        .findFirst();

    /*
    there is no way to have an empty optional because
    the endpointId should be output of one of the routes
     */
    if (routeOptional.isPresent()) {
      return routeOptional.get().getId();
    }

    return StringUtils.EMPTY;
  }

}
