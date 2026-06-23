package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_PRODUCED_EXCHANGE;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_NAME;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Happy-path tests for ExchangeSendingEventTracer (the existing test only covers
 * early-return branches via mocks).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangeSendingEventTracerNewTest {

  private DefaultCamelContext camelContext;
  private MessageService messageService;
  private RouteContextService routeContextService;
  private ExchangeSendingEventTracer tracer;

  @BeforeAll
  void start() {
    camelContext = new DefaultCamelContext();
    camelContext.start();
    messageService = mock(MessageService.class);
    routeContextService = mock(RouteContextService.class);
    tracer = new ExchangeSendingEventTracer(messageService, routeContextService);
  }

  @AfterAll
  void stop() {
    camelContext.stop();
  }

  private ExchangeSendingEvent eventFor(Exchange exchange, String endpointUri) {
    ExchangeSendingEvent event = mock(ExchangeSendingEvent.class);
    Endpoint endpoint = mock(Endpoint.class);
    lenient().when(endpoint.getEndpointUri()).thenReturn(endpointUri);
    lenient().when(event.getEndpoint()).thenReturn(endpoint);
    when(event.getExchange()).thenReturn(exchange);
    return event;
  }

  @Test
  void returnsNullForProducedExchange() {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.setProperty(CAMELBEE_PRODUCED_EXCHANGE, "true");
    assertThat(tracer.traceEvent(eventFor(exchange, "mock:out"))).isNull();
  }

  @Test
  void tracesSendingMessageWithExistingDirectRouteStack() {
    Exchange exchange = new DefaultExchange(camelContext);
    Deque<String> stack = new ArrayDeque<>();
    stack.push("direct:start");
    exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, stack);
    exchange.setProperty(CURRENT_ROUTE_NAME, "direct:start");

    Message message = tracer.traceEvent(eventFor(exchange, "mock:out"));

    assertThat(message).isNotNull();
    assertThat(message.getExchangeEventType()).isEqualTo(MessageEventType.SENDING);
    assertThat(message.getEndpoint()).isEqualTo("mock:out");
  }

  @Test
  void clonesRouteStackWhenExchangeIdChanged() {
    Exchange exchange = new DefaultExchange(camelContext);
    Deque<String> stack = new ArrayDeque<>();
    stack.push("direct:start");
    exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, stack);
    exchange.setProperty(CURRENT_ROUTE_NAME, "direct:start");
    // A different previous exchange id forces adjustStack to clone the stack.
    exchange.setProperty(
        org.camelbee.constants.CamelBeeConstants.PREVIOUS_EXCHANGE_ID, "some-other-id");

    Message message = tracer.traceEvent(eventFor(exchange, "mock:out"));

    assertThat(message).isNotNull();
    assertThat(message.getExchangeEventType()).isEqualTo(MessageEventType.SENDING);
  }

  @Test
  void initializesRouteStackWhenAbsent() {
    when(routeContextService.getCamelRoutes()).thenReturn(List.of());
    when(messageService.getMessageList()).thenReturn(List.of());

    Exchange exchange = new DefaultExchange(camelContext);
    // No CURRENT_ROUTE_TRACE_STACK set -> initializeRouteStack path.
    Message message = tracer.traceEvent(eventFor(exchange, "mock:dynamic"));

    assertThat(message).isNotNull();
    assertThat(message.getExchangeEventType()).isEqualTo(MessageEventType.SENDING);
    assertThat(exchange.getProperty(CURRENT_ROUTE_TRACE_STACK)).isNotNull();
  }
}
