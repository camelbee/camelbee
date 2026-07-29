package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Roadmap #9 (latency): {@code ExchangeSentEvent.getTimeTaken()} must propagate to
 * {@code Message.getTimeTaken()} on SENT events (README-camel421-notes.md, FINAL
 * ROADMAP v2). Uses a real DefaultExchange (the existing ExchangeSentEventTracerTest
 * only covers early-return branches via interface mocks, which can't reach the
 * {@code (DefaultExchange) exchange} cast in the happy path).
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

  private ExchangeSentEvent eventFor(Exchange exchange, String endpointUri, long timeTaken) {
    ExchangeSentEvent event = mock(ExchangeSentEvent.class);
    Endpoint endpoint = mock(Endpoint.class);
    lenient().when(endpoint.getEndpointUri()).thenReturn(endpointUri);
    lenient().when(event.getEndpoint()).thenReturn(endpoint);
    when(event.getExchange()).thenReturn(exchange);
    when(event.getTimeTaken()).thenReturn(timeTaken);
    return event;
  }

  @Test
  void tracesSentMessageWithTimeTaken() {
    Exchange exchange = new DefaultExchange(camelContext);
    Deque<String> stack = new ArrayDeque<>();
    stack.push("mock:out");
    stack.push("direct:start");
    exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, stack);

    Message message = tracer.traceEvent(eventFor(exchange, "mock:out", 42L));

    assertThat(message).isNotNull();
    assertThat(message.getTimeTaken()).isEqualTo(42L);
  }

  @Test
  void tracesSentMessageWithZeroTimeTakenWhenEventReportsZero() {
    Exchange exchange = new DefaultExchange(camelContext);
    Deque<String> stack = new ArrayDeque<>();
    stack.push("mock:out");
    stack.push("direct:start");
    exchange.setProperty(CURRENT_ROUTE_TRACE_STACK, stack);

    Message message = tracer.traceEvent(eventFor(exchange, "mock:out", 0L));

    assertThat(message).isNotNull();
    assertThat(message.getTimeTaken()).isZero();
  }
}
