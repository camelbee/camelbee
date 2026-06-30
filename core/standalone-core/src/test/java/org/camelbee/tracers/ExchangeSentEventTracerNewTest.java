package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.camelbee.constants.CamelBeeConstants.LAST_DIRECT_ROUTE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Happy-path tests for ExchangeSentEventTracer (the existing test only covers early-return
 * branches).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangeSentEventTracerNewTest {

  private DefaultCamelContext camelContext;
  private ExchangeSentEventTracer tracer;

  @BeforeAll
  void start() {
    camelContext = new DefaultCamelContext();
    camelContext.start();
    tracer = new ExchangeSentEventTracer();
  }

  @AfterAll
  void stop() {
    camelContext.stop();
  }

  private ExchangeSentEvent eventFor(Exchange exchange) {
    ExchangeSentEvent event = mock(ExchangeSentEvent.class);
    when(event.getExchange()).thenReturn(exchange);
    return event;
  }

  private Exchange exchangeWithStack(String... routesBottomToTop) {
    Exchange exchange = new DefaultExchange(camelContext);
    Deque<String> stack = new ArrayDeque<>();
    for (String route : routesBottomToTop) {
      stack.push(route);
    }
    exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, stack);
    return exchange;
  }

  @Test
  void returnsNullWhenStackIsEmpty() {
    Exchange exchange = exchangeWithStack();
    assertThat(tracer.traceEvent(eventFor(exchange))).isNull();
  }

  @Test
  void tracesSentMessageAsResponse() {
    Exchange exchange = exchangeWithStack("direct:caller", "mock:out");

    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getExchangeEventType()).isEqualTo(MessageEventType.SENT);
    assertThat(message.getEndpoint()).isEqualTo("mock:out");
    assertThat(message.getRouteId()).isEqualTo("direct:caller");
    assertThat(message.getMessageType()).isEqualTo(MessageType.RESPONSE);
  }

  @Test
  void fallsBackToLastDirectRouteWhenBothRoutesAreProducers() {
    Exchange exchange = exchangeWithStack("mock:caller", "mock:out");
    exchange.setProperty(LAST_DIRECT_ROUTE, "direct:realCaller");

    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getRouteId()).isEqualTo("direct:realCaller");
  }

  @Test
  void tracesSentMessageAsErrorResponseWhenExchangeFailed() {
    Exchange exchange = exchangeWithStack("direct:caller", "mock:out");
    exchange.setException(new RuntimeException("boom"));

    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getMessageType()).isEqualTo(MessageType.ERROR_RESPONSE);
    assertThat(message.getException()).isEqualTo("boom");
  }
}
