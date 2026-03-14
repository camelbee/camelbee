package org.camelbee.tracers;

import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_PRODUCED_EXCHANGE;
import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_TRACE_STACK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.camelbee.debugger.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeSentEventTracerTest {

  @Mock
  private MessageService messageService;

  @Mock
  private Exchange exchange;

  @Mock
  private ExchangeSentEvent event;

  @Mock
  private Message camelMessage;

  @InjectMocks
  private ExchangeSentEventTracer tracer;

  private void stubExchangeForBodyAndHeaders() {
    lenient().when(exchange.getMessage()).thenReturn(camelMessage);
    lenient().when(camelMessage.getBody()).thenReturn(null);
    lenient().when(exchange.getIn()).thenReturn(camelMessage);
    lenient().when(camelMessage.getHeaders()).thenReturn(new HashMap<>());
  }

  @Test
  void processSentMessageShouldReturnNullWhenRouteStackIsNull() {
    // Arrange
    when(event.getExchange()).thenReturn(exchange);
    when(exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE)).thenReturn(null);
    stubExchangeForBodyAndHeaders();
    when(exchange.getProperty(CURRENT_ROUTE_TRACE_STACK)).thenReturn(null);

    // Act
    org.camelbee.debugger.model.exchange.Message result = tracer.traceEvent(event);

    // Assert
    assertNull(result);
  }

  @Test
  void processSentMessageShouldReturnNullWhenRouteStackIsEmpty() {
    // Arrange
    Deque<String> emptyStack = new ArrayDeque<>();
    when(event.getExchange()).thenReturn(exchange);
    when(exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE)).thenReturn(null);
    stubExchangeForBodyAndHeaders();
    when(exchange.getProperty(CURRENT_ROUTE_TRACE_STACK)).thenReturn(emptyStack);

    // Act
    org.camelbee.debugger.model.exchange.Message result = tracer.traceEvent(event);

    // Assert
    assertNull(result);
  }

  @Test
  void traceEventShouldReturnNullForProducedExchange() {
    // Arrange
    when(event.getExchange()).thenReturn(exchange);
    when(exchange.getProperty(CAMELBEE_PRODUCED_EXCHANGE)).thenReturn("true");

    // Act
    org.camelbee.debugger.model.exchange.Message result = tracer.traceEvent(event);

    // Assert
    assertNull(result);
  }
}
