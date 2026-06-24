package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.camelbee.constants.CamelBeeConstants.INITIAL_EXCHANGE_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.service.MessageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Happy-path tests for ExchangeCompletedEventTracer (the existing test only covers early-return
 * branches).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangeCompletedEventTracerNewTest {

  private DefaultCamelContext camelContext;
  private ExchangeCompletedEventTracer tracer;

  @BeforeAll
  void start() {
    camelContext = new DefaultCamelContext();
    camelContext.start();
    tracer = new ExchangeCompletedEventTracer(mock(MessageService.class));
  }

  @AfterAll
  void stop() {
    camelContext.stop();
  }

  private ExchangeCompletedEvent eventFor(Exchange exchange) {
    ExchangeCompletedEvent event = mock(ExchangeCompletedEvent.class);
    when(event.getExchange()).thenReturn(exchange);
    return event;
  }

  /** An exchange that is the initial (entrypoint) one, with a populated route stack. */
  private Exchange initialExchangeWithStack(String... routesBottomToTop) {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.setProperty(INITIAL_EXCHANGE_ID, exchange.getExchangeId());
    Deque<String> stack = new ArrayDeque<>();
    for (String route : routesBottomToTop) {
      stack.push(route);
    }
    exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, stack);
    return exchange;
  }

  @Test
  void returnsNullWhenNotTheInitialExchange() {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.setProperty(INITIAL_EXCHANGE_ID, "some-other-id");
    assertThat(tracer.traceEvent(eventFor(exchange))).isNull();
  }

  @Test
  void returnsNullWhenStackIsEmpty() {
    Exchange exchange = initialExchangeWithStack();
    assertThat(tracer.traceEvent(eventFor(exchange))).isNull();
  }

  @Test
  void tracesCompletedMessageAsResponse() {
    Exchange exchange = initialExchangeWithStack("direct:caller", "direct:start");

    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getExchangeEventType()).isEqualTo(MessageEventType.COMPLETED);
    assertThat(message.getEndpoint()).isEqualTo("direct:start");
    assertThat(message.getRouteId()).isEqualTo("direct:caller");
    assertThat(message.getMessageType()).isEqualTo(MessageType.RESPONSE);
  }

  @Test
  void tracesCompletedMessageAsErrorResponseWhenExchangeFailed() {
    Exchange exchange = initialExchangeWithStack("direct:caller", "direct:start");
    exchange.setException(new RuntimeException("kaboom"));

    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getMessageType()).isEqualTo(MessageType.ERROR_RESPONSE);
    assertThat(message.getException()).isEqualTo("kaboom");
  }
}
