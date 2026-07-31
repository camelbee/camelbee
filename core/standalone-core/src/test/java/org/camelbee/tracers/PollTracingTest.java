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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code poll()} and {@code pollEnrich()} are the only EIPs Camel emits no events for - a poll is a
 * receive, and {@code PollProcessor}/{@code PollEnricher} notify nothing - so the hop is
 * reconstructed by {@link PollInterceptStrategy}. These tests drive it through the real notifier,
 * tracer and message service.
 *
 * <p>A file waiting on disk is used rather than an empty endpoint so the poll genuinely succeeds.
 * That matters: from the exchange alone a timed-out poll and a successful one are indistinguishable
 * for {@code pollEnrich}, so testing against an empty endpoint would prove much less.
 */
class PollTracingTest {

  @TempDir
  Path inbox;

  private record Fixture(List<Message> traced, String endpointUri) {

    List<Message> pollHop() {
      return traced.stream().filter(m -> endpointUri.equals(m.getEndpoint())).toList();
    }
  }

  private Fixture run(String dsl, boolean seedFile, boolean tracingActive) throws Exception {
    if (seedFile) {
      Files.writeString(inbox.resolve("payload.txt"), "content-from-disk");
    }
    final String fileUri = "file://" + inbox.toAbsolutePath() + "?noop=true&idempotent=false";

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
      tracerService.activateTracing(tracingActive);

      context.getManagementStrategy().addEventNotifier(new CamelBeeEventNotifier(tracerService));
      context.getCamelContextExtension().addInterceptStrategy(new NodeIdInterceptStrategy());
      context.getCamelContextExtension().addInterceptStrategy(new PollInterceptStrategy(tracerService));

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          if ("poll".equals(dsl)) {
            from("direct:start").routeId("pollRoute").poll(fileUri, 500).to("mock:after").id("afterTo");
          } else {
            from("direct:start").routeId("pollEnrichRoute")
                // keeps the original body, so the exchange is unchanged even on a successful poll
                .pollEnrich(fileUri, 500, (original, resource) -> original)
                .to("mock:after").id("afterTo");
          }
        }
      });

      context.start();
      ProducerTemplate template = context.createProducerTemplate();
      template.requestBody("direct:start", "trigger");
      Thread.sleep(200);

      // TO_ENDPOINT is normalised by Camel, so read back what the hop was actually recorded against
      return new Fixture(List.copyOf(messageService.getMessageList()),
          messageService.getMessageList().stream()
              .map(Message::getEndpoint)
              .filter(e -> e != null && e.startsWith("file:"))
              .findFirst().orElse("file:<none>"));
    }
  }

  @Test
  @DisplayName("poll() records the hop, with what it received as the response body")
  void pollRecordsTheHopAndTheReceivedBody() throws Exception {
    Fixture fixture = run("poll", true, true);
    List<Message> hop = fixture.pollHop();

    assertThat(hop).hasSize(2);
    assertThat(hop).allSatisfy(m -> assertThat(m.getEndpointId()).startsWith("poll"));
    assertThat(hop).allSatisfy(m -> assertThat(m.getRouteId()).isEqualTo("direct://start"));

    Message request = hop.get(0);
    Message response = hop.get(1);
    assertThat(request.getMessageType()).isEqualTo(MessageType.REQUEST);
    assertThat(request.getMessageBody()).isEqualTo("trigger");

    assertThat(response.getMessageType()).isEqualTo(MessageType.RESPONSE);
    // poll() replaces the body with the polled message, so this really is what was read
    assertThat(response.getMessageBody()).isEqualTo("content-from-disk");
    assertThat(response.getTimeTaken()).isGreaterThanOrEqualTo(0);

    // both halves share the exchange, so the UI pairs them into one interaction
    assertThat(request.getExchangeId()).isEqualTo(response.getExchangeId());
  }

  @Test
  @DisplayName("pollEnrich() records the hop even when the aggregation strategy discards the result")
  void pollEnrichRecordsTheHop() throws Exception {
    Fixture fixture = run("pollEnrich", true, true);
    List<Message> hop = fixture.pollHop();

    assertThat(hop).hasSize(2);
    assertThat(hop).allSatisfy(m -> assertThat(m.getEndpointId()).startsWith("pollEnrich"));

    // The strategy kept the original, so the body is unchanged. That is reported as-is: an unchanged
    // body does NOT mean nothing was received, and nothing here claims otherwise.
    assertThat(hop.get(1).getMessageBody()).isEqualTo("trigger");
  }

  @Test
  @DisplayName("a poll that times out is still recorded, so the hop does not vanish")
  void timedOutPollIsStillRecorded() throws Exception {
    Fixture fixture = run("poll", false, true);
    List<Message> hop = fixture.pollHop();

    assertThat(hop).hasSize(2);
    assertThat(hop.get(1).getMessageType()).isEqualTo(MessageType.RESPONSE);
    // it waited for the full timeout and got nothing
    assertThat(hop.get(1).getTimeTaken()).isGreaterThanOrEqualTo(400);
  }

  @Test
  @DisplayName("nothing is recorded while tracing is inactive")
  void recordsNothingWhenTracingIsOff() throws Exception {
    assertThat(run("poll", true, false).traced()).isEmpty();
  }
}
