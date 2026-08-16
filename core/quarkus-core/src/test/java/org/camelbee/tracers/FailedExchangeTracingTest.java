/*
 * Copyright 2023 Rahmi Ege Karaosmanoglu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Camel fires {@code ExchangeFailedEvent} <em>instead of</em> {@code ExchangeCompletedEvent} when an
 * exchange ends with an unhandled exception - never both. The failed event used to be discarded,
 * which left a failed exchange with no closing marker.
 *
 * <p>How much that cost depended on how the route was started, which is why both shapes are pinned
 * here. A producer-started route still surfaced the error on the enclosing send's SENT. A
 * consumer-started one - timer, file, jms, the common production shape - has no enclosing send, so
 * the failure was recorded nowhere at all: the trace stopped at the last successful hop and looked
 * exactly like a route that finished cleanly.
 */
class FailedExchangeTracingTest {

  private static final String BOOM = "kaboom";

  private List<Message> run(Consumer<RouteBuilder> routes, boolean sendFromProducer) throws Exception {
    MessageService messageService = new MessageService(1000);

    try (CamelContext context = new DefaultCamelContext()) {
      RouteContextService routeContextService = mock(RouteContextService.class);
      when(routeContextService.getCamelRoutes()).thenReturn(List.of());

      TracerService tracerService = new TracerService(false, true, 60000,
          new ExchangeCreatedEventTracer(),
          new ExchangeSendingEventTracer(messageService, routeContextService),
          new ExchangeSentEventTracer(),
          new ExchangeCompletedEventTracer(),
          messageService,
          new LoggingService());
      tracerService.activateTracing(true);

      context.getManagementStrategy().addEventNotifier(new CamelBeeEventNotifier(tracerService));
      context.getCamelContextExtension().addInterceptStrategy(new NodeIdInterceptStrategy(tracerService));

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          routes.accept(this);
        }
      });

      context.start();

      if (sendFromProducer) {
        ProducerTemplate template = context.createProducerTemplate();
        try {
          template.requestBody("direct:start", "trigger");
        } catch (Exception expected) {
          // the caller sees it; what matters here is what the tracer recorded
        }
      }
      Thread.sleep(600);

      return List.copyOf(messageService.getMessageList());
    }
  }

  private static List<Message> closingMarkers(List<Message> traced) {
    return traced.stream().filter(m -> m.getExchangeEventType() == MessageEventType.COMPLETED).toList();
  }

  @Test
  @DisplayName("consumer-started route: the failure is recorded, not silently dropped")
  void consumerStartedFailureIsRecorded() throws Exception {
    List<Message> traced = run(rb -> rb.from("timer://tick?repeatCount=1&delay=100").routeId("boom")
        .to("mock:before").id("toBefore")
        .process(e -> {
          throw new IllegalStateException(BOOM);
        }).id("thrower")
        .to("mock:after").id("toAfter"), false);

    // nothing outside the route is sending, so before this change the exception appeared nowhere
    assertThat(traced)
        .as("the exception is reported somewhere in the trace")
        .anySatisfy(m -> assertThat(m.getException()).contains(BOOM));

    assertThat(closingMarkers(traced))
        .as("the exchange gets a closing marker, and it says the exchange failed")
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.getMessageType()).isEqualTo(MessageType.ERROR_RESPONSE));
  }

  @Test
  @DisplayName("producer-started route: still reported, and the exception is not duplicated")
  void producerStartedFailureIsReportedOnce() throws Exception {
    List<Message> traced = run(rb -> rb.from("direct:start").routeId("boom")
        .to("mock:before").id("toBefore")
        .process(e -> {
          throw new IllegalStateException(BOOM);
        }).id("thrower")
        .to("mock:after").id("toAfter"), true);

    assertThat(traced.stream().filter(m -> m.getException() != null && m.getException().contains(BOOM)))
        .as("handleError dedups by exception identity, so the text is carried exactly once")
        .hasSize(1);

    // the closing marker still reports failure even though the text was claimed by an earlier hop
    assertThat(closingMarkers(traced))
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.getMessageType()).isEqualTo(MessageType.ERROR_RESPONSE));
  }

  @Test
  @DisplayName("a handled exception is not reported as a failed exchange")
  void handledExceptionIsNotAFailure() throws Exception {
    List<Message> traced = run(rb -> {
      rb.onException(IllegalStateException.class).handled(true).to("mock:recovered").id("toRecovered");
      rb.from("timer://tick?repeatCount=1&delay=100").routeId("recovering")
          .to("mock:before").id("toBefore")
          .process(e -> {
            throw new IllegalStateException(BOOM);
          }).id("thrower");
    }, false);

    // Camel fires ExchangeCompletedEvent for a handled exchange, so the closing marker must not
    // claim failure - otherwise every recovered route would look broken in the UI
    assertThat(traced).isNotEmpty();
    assertThat(closingMarkers(traced))
        .as("handled means completed: %s", traced.stream().map(Message::getMessageType).toList())
        .allSatisfy(m -> assertThat(m.getMessageType()).isEqualTo(MessageType.RESPONSE));
  }

  @Test
  @DisplayName("a successful exchange is untouched by the failed-event branch")
  void successfulExchangeStillCompletesCleanly() throws Exception {
    List<Message> traced = run(rb -> rb.from("timer://tick?repeatCount=1&delay=100").routeId("fine")
        .to("mock:a").id("toA")
        .to("mock:b").id("toB"), false);

    assertThat(traced).allSatisfy(m -> assertThat(m.getException()).isNull());
    assertThat(closingMarkers(traced))
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.getMessageType()).isEqualTo(MessageType.RESPONSE));
  }
}
