package org.camelbee.tracers;

import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_PRODUCED_EXCHANGE;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.camelbee.constants.CamelBeeConstants.INITIAL_EXCHANGE_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeCompletedEventTracerTest {

  @Mock
  private MessageService messageService;

  @Mock
  private Exchange exchange;

  @Mock
  private ExchangeCompletedEvent event;

  @Mock
  private org.apache.camel.Message camelMessage;

  @InjectMocks
  private ExchangeCompletedEventTracer tracer;

  private void stubExchangeForBodyAndHeaders() {
    lenient().when(exchange.getMessage()).thenReturn(camelMessage);
    lenient().when(camelMessage.getBody()).thenReturn(null);
    lenient().when(exchange.getIn()).thenReturn(camelMessage);
    lenient().when(camelMessage.getHeaders()).thenReturn(new HashMap<>());
  }

  private void stubInitialExchangeId(String exchangeId) {
    when(exchange.getProperty(INITIAL_EXCHANGE_ID)).thenReturn(exchangeId);
    when(exchange.getProperty(INITIAL_EXCHANGE_ID, String.class)).thenReturn(exchangeId);
    when(exchange.getExchangeId()).thenReturn(exchangeId);
  }

  @Test
  void processCompletedMessageShouldReturnNullWhenRouteStackIsNull() {
    // Arrange
    when(event.getExchange()).thenReturn(exchange);
    when(exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE)).thenReturn(null);
    stubInitialExchangeId("test-exchange-id");
    stubExchangeForBodyAndHeaders();
    when(exchange.getProperty(CURRENT_ROUTE_TRACE_STACK)).thenReturn(null);

    // Act
    Message result = tracer.traceEvent(event);

    // Assert
    assertNull(result);
  }

  @Test
  void processCompletedMessageShouldReturnNullWhenRouteStackIsEmpty() {
    // Arrange
    Deque<String> emptyStack = new ArrayDeque<>();
    when(event.getExchange()).thenReturn(exchange);
    when(exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE)).thenReturn(null);
    stubInitialExchangeId("test-exchange-id");
    stubExchangeForBodyAndHeaders();
    when(exchange.getProperty(CURRENT_ROUTE_TRACE_STACK)).thenReturn(emptyStack);

    // Act
    Message result = tracer.traceEvent(event);

    // Assert
    assertNull(result);
  }

  @Test
  void traceEventShouldReturnNullForProducedExchange() {
    // Arrange
    when(event.getExchange()).thenReturn(exchange);
    when(exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE)).thenReturn("true");

    // Act
    Message result = tracer.traceEvent(event);

    // Assert
    assertNull(result);
  }
}
