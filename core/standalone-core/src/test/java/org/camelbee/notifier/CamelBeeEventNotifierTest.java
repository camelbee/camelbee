package org.camelbee.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.event.CamelContextStartedEvent;
import org.apache.camel.impl.event.ExchangeCompletedEvent;
import org.apache.camel.impl.event.ExchangeCreatedEvent;
import org.apache.camel.impl.event.ExchangeSendingEvent;
import org.apache.camel.impl.event.ExchangeSentEvent;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CamelBeeEventNotifierTest {

  private DefaultCamelContext camelContext;
  private TracerService tracerService;
  private CamelBeeEventNotifier notifier;

  @BeforeAll
  void start() {
    camelContext = new DefaultCamelContext();
    camelContext.start();
  }

  @BeforeEach
  void freshNotifier() {
    // Fresh mock per test so verifyNoInteractions in notify_otherEvent_isIgnored is not polluted
    // by interactions from the other tests (the context is shared, the mock is not).
    tracerService = mock(TracerService.class);
    notifier = new CamelBeeEventNotifier(tracerService);
  }

  @AfterAll
  void stop() {
    camelContext.stop();
  }

  private DefaultExchange exchange() {
    return new DefaultExchange(camelContext);
  }

  @Test
  void notify_createdEvent_isDelegated() throws Exception {
    ExchangeCreatedEvent event = new ExchangeCreatedEvent(exchange());
    notifier.notify(event);
    verify(tracerService).traceExchangeCreateEvent(event);
  }

  @Test
  void notify_sendingEvent_isDelegated() throws Exception {
    ExchangeSendingEvent event = new ExchangeSendingEvent(exchange(), null);
    notifier.notify(event);
    verify(tracerService).traceExchangeSendingEvent(event);
  }

  @Test
  void notify_sentEvent_isDelegated() throws Exception {
    ExchangeSentEvent event = new ExchangeSentEvent(exchange(), null, 1L);
    notifier.notify(event);
    verify(tracerService).traceExchangeSentEvent(event);
  }

  @Test
  void notify_completedEvent_isDelegated() throws Exception {
    ExchangeCompletedEvent event = new ExchangeCompletedEvent(exchange());
    notifier.notify(event);
    verify(tracerService).traceExchangeCompletedEvent(event);
  }

  @Test
  void notify_otherEvent_isIgnored() throws Exception {
    notifier.notify(new CamelContextStartedEvent(camelContext));
    verifyNoInteractions(tracerService);
  }

  @Test
  void isEnabled_onlyForExchangeEvents() {
    assertThat(notifier.isEnabled(new ExchangeCreatedEvent(exchange()))).isTrue();
    assertThat(notifier.isEnabled(new CamelContextStartedEvent(camelContext))).isFalse();
  }
}
