package org.camelbee.tracers;

import static org.mockito.Mockito.*;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.logging.LoggingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracerServiceTest {

  @Mock
  ExchangeCreatedEventTracer createdEventTracer;
  @Mock
  ExchangeSendingEventTracer sendingEventTracer;
  @Mock
  ExchangeSentEventTracer sentEventTracer;
  @Mock
  ExchangeCompletedEventTracer completedEventTracer;
  @Mock
  MessageService messageService;
  @Mock
  LoggingService loggingService;
  @Mock
  ExchangeCreatedEvent createdEvent;
  @Mock
  ExchangeSendingEvent sendingEvent;
  @Mock
  ExchangeSentEvent sentEvent;
  @Mock
  ExchangeCompletedEvent completedEvent;
  @Mock
  Endpoint endpoint;
  @Mock
  Message message;

  private TracerService buildService(boolean loggingEnabled, boolean tracerEnabled) {
    return new TracerService(loggingEnabled, tracerEnabled, 300000L,
        createdEventTracer, sendingEventTracer, sentEventTracer, completedEventTracer,
        messageService, loggingService);
  }

  @Test
  void shouldNotCallAnyTracerWhenBothDisabled() {
    TracerService service = buildService(false, false);

    service.traceExchangeCreateEvent(createdEvent);
    service.traceExchangeSendingEvent(sendingEvent);
    service.traceExchangeSentEvent(sentEvent);
    service.traceExchangeCompletedEvent(completedEvent);

    verifyNoInteractions(createdEventTracer, sendingEventTracer, sentEventTracer, completedEventTracer);
  }

  @Test
  void shouldCallCreatedAndCompletedTracerWhenOnlyLoggingEnabled() {
    TracerService service = buildService(true, false);
    when(createdEventTracer.traceEvent(createdEvent)).thenReturn(message);
    when(completedEventTracer.traceEvent(completedEvent)).thenReturn(message);

    service.traceExchangeCreateEvent(createdEvent);
    service.traceExchangeCompletedEvent(completedEvent);

    verify(createdEventTracer).traceEvent(createdEvent);
    verify(completedEventTracer).traceEvent(completedEvent);
  }

  @Test
  void shouldSkipSendingTracerForDirectEndpointWhenOnlyLoggingEnabled() {
    TracerService service = buildService(true, false);
    when(sendingEvent.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getEndpointUri()).thenReturn("direct:internalRoute");

    service.traceExchangeSendingEvent(sendingEvent);

    verifyNoInteractions(sendingEventTracer);
  }

  @Test
  void shouldSkipSentTracerForDirectEndpointWhenOnlyLoggingEnabled() {
    TracerService service = buildService(true, false);
    when(sentEvent.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getEndpointUri()).thenReturn("direct:internalRoute");

    service.traceExchangeSentEvent(sentEvent);

    verifyNoInteractions(sentEventTracer);
  }

  @Test
  void shouldCallSendingTracerForDirectEndpointWhenTracingActive() {
    TracerService service = buildService(false, true);
    service.activateTracing(true);
    when(sendingEvent.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getEndpointUri()).thenReturn("direct:internalRoute");
    when(sendingEventTracer.traceEvent(sendingEvent)).thenReturn(message);

    service.traceExchangeSendingEvent(sendingEvent);

    verify(sendingEventTracer).traceEvent(sendingEvent);
  }

  @Test
  void shouldCallSentTracerForDirectEndpointWhenTracingActive() {
    TracerService service = buildService(false, true);
    service.activateTracing(true);
    when(sentEvent.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getEndpointUri()).thenReturn("direct:internalRoute");
    when(sentEventTracer.traceEvent(sentEvent)).thenReturn(message);

    service.traceExchangeSentEvent(sentEvent);

    verify(sentEventTracer).traceEvent(sentEvent);
  }

  @Test
  void shouldCallSendingTracerForNonDirectEndpointWhenOnlyLoggingEnabled() {
    TracerService service = buildService(true, false);
    when(sendingEvent.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getEndpointUri()).thenReturn("https://external-service");
    when(sendingEventTracer.traceEvent(sendingEvent)).thenReturn(message);

    service.traceExchangeSendingEvent(sendingEvent);

    verify(sendingEventTracer).traceEvent(sendingEvent);
    verify(loggingService).logMessage(message, "Request sent:", false);
  }

  @Test
  void shouldCallSentTracerForNonDirectEndpointWhenOnlyLoggingEnabled() {
    TracerService service = buildService(true, false);
    when(sentEvent.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getEndpointUri()).thenReturn("https://external-service");
    when(sentEventTracer.traceEvent(sentEvent)).thenReturn(message);

    service.traceExchangeSentEvent(sentEvent);

    verify(sentEventTracer).traceEvent(sentEvent);
    verify(loggingService).logMessage(message, "Response received:", false);
  }

  @Test
  void shouldAddMessageToServiceWhenTracerEnabledAndActivated() {
    TracerService service = buildService(false, true);
    service.activateTracing(true);
    when(sendingEvent.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getEndpointUri()).thenReturn("https://external-service");
    when(sendingEventTracer.traceEvent(sendingEvent)).thenReturn(message);

    service.traceExchangeSendingEvent(sendingEvent);

    verify(messageService).addMessage(message);
    verifyNoInteractions(loggingService);
  }
}