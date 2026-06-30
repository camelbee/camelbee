package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_PRODUCED_EXCHANGE;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.camelbee.constants.CamelBeeConstants.INITIAL_EXCHANGE_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangeCreatedEventTracerTest {

  private DefaultCamelContext camelContext;
  private ExchangeCreatedEventTracer tracer;

  @BeforeAll
  void start() {
    camelContext = new DefaultCamelContext();
    camelContext.start();
    tracer = new ExchangeCreatedEventTracer();
  }

  @AfterAll
  void stop() {
    camelContext.stop();
  }

  private ExchangeCreatedEvent eventFor(Exchange exchange) {
    ExchangeCreatedEvent event = mock(ExchangeCreatedEvent.class);
    when(event.getExchange()).thenReturn(exchange);
    return event;
  }

  @Test
  void returnsNullWhenAlreadyTraced() {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.setProperty(INITIAL_EXCHANGE_ID, "already");
    assertThat(tracer.traceEvent(eventFor(exchange))).isNull();
  }

  @Test
  void returnsNullForProducedExchange() {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.setProperty(CAMELBEE_PRODUCED_EXCHANGE, "true");
    assertThat(tracer.traceEvent(eventFor(exchange))).isNull();
  }

  @Test
  void tracesCreatedMessageWithoutInitialRoute() {
    Exchange exchange = new DefaultExchange(camelContext);
    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getExchangeEventType()).isEqualTo(MessageEventType.CREATED);
    assertThat(message.getExchangeId()).isEqualTo(exchange.getExchangeId());
  }

  @Test
  void tracesCreatedMessageUsingToEndpointAsCurrentRoute() {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.setProperty(Exchange.TO_ENDPOINT, "mock:downstream");

    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getEndpoint()).isEqualTo("mock:downstream");
  }

  @Test
  void tracesCreatedMessageWithInitialRoute() {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.getExchangeExtension().setFromRouteId("entry-route");

    Message message = tracer.traceEvent(eventFor(exchange));

    assertThat(message).isNotNull();
    assertThat(message.getRouteId()).isEqualTo("entry-route");
    assertThat(exchange.getProperty(CURRENT_ROUTE_TRACE_STACK)).isNotNull();
  }
}
