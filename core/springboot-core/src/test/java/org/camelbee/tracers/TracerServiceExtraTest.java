package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.logging.LoggingService;
import org.junit.jupiter.api.Test;

/**
 * Covers the active-tracing branches of TracerService that the existing test does not: message
 * publishing when tracing is activated, the keep-alive call, and the idle-timeout deactivation.
 */
class TracerServiceExtraTest {

  private final ExchangeCreatedEventTracer createdEventTracer = mock(ExchangeCreatedEventTracer.class);
  private final ExchangeSendingEventTracer sendingEventTracer = mock(ExchangeSendingEventTracer.class);
  private final ExchangeSentEventTracer sentEventTracer = mock(ExchangeSentEventTracer.class);
  private final ExchangeCompletedEventTracer completedEventTracer = mock(ExchangeCompletedEventTracer.class);
  private final MessageService messageService = mock(MessageService.class);
  private final LoggingService loggingService = mock(LoggingService.class);

  private TracerService service(long idleTime) {
    return new TracerService(false, true, idleTime,
        createdEventTracer, sendingEventTracer, sentEventTracer, completedEventTracer,
        messageService, loggingService);
  }

  @Test
  void publishesMessagesWhenTracerEnabledAndActivated() {
    TracerService service = service(300000L);
    service.activateTracing(true);
    service.keepTracingActive();

    Message message = mock(Message.class);
    ExchangeCreatedEvent createdEvent = mock(ExchangeCreatedEvent.class);
    ExchangeCompletedEvent completedEvent = mock(ExchangeCompletedEvent.class);
    when(createdEventTracer.traceEvent(createdEvent)).thenReturn(message);
    when(completedEventTracer.traceEvent(completedEvent)).thenReturn(message);

    service.traceExchangeCreateEvent(createdEvent);
    service.traceExchangeCompletedEvent(completedEvent);

    assertThat(service.isTracingActivated()).isTrue();
    verify(messageService, times(2)).addMessage(message);
  }

  @Test
  void deactivatesTracingAfterIdleTimeout() {
    // Negative idle window: any elapsed time since activation counts as stale, so the next status
    // check deactivates tracing (exercises the idle-timeout branch deterministically).
    TracerService service = service(-1L);
    service.activateTracing(true);

    assertThat(service.isTracingActivated()).isFalse();
  }
}
