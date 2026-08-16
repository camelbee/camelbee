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
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.masking.Masker;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.camelbee.utils.ExchangeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Masker} is unit tested on its own; what these assert is that nothing in the tracing path
 * BYPASSES it. That is the property that matters - a traced Message is served over HTTP and written
 * to the structured log, so a secret that reaches one has already been disclosed, and a mask that is
 * merely usually applied is worse than none because it invites trust.
 *
 * <p>Every assertion is "the secret is absent from everything traced", never "this one field looks
 * right", so a path that skips masking fails here rather than passing quietly.
 */
class MaskedTracingTest {

  private static final String SECRET = "hunter2-should-never-appear";

  @AfterEach
  void restoreDefaults() {
    ExchangeUtils.configureMasking(Masker.withDefaults(), true);
  }

  private List<Message> run(String body, boolean maskingEnabled, boolean bodyCapture) throws Exception {
    ExchangeUtils.configureMasking(
        maskingEnabled ? Masker.withDefaults() : Masker.disabled(), bodyCapture);

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
          // several hops, so a tracer that forgot to mask shows up somewhere
          from("direct:start").routeId("main")
              .to("mock:a").id("toA")
              .to("direct:second").id("toSecond");
          from("direct:second").routeId("second").to("mock:b").id("toB");
        }
      });

      context.start();
      ProducerTemplate template = context.createProducerTemplate();
      template.sendBodyAndHeader("direct:start", body, "Authorization", "Bearer " + SECRET);
      Thread.sleep(200);

      return List.copyOf(messageService.getMessageList());
    }
  }

  /** Everything a traced message could carry, as one string per message. */
  private static String everything(List<Message> traced) {
    StringBuilder all = new StringBuilder();
    traced.forEach(m -> all
        .append(m.getMessageBody()).append('\n')
        .append(m.getHeaders()).append('\n'));
    return all.toString();
  }

  @Test
  @DisplayName("a secret in the body never reaches any traced message")
  void secretInBodyIsMasked() throws Exception {
    List<Message> traced = run("{\"user\":\"ege\",\"password\":\"" + SECRET + "\"}", true, true);

    assertThat(traced).isNotEmpty();
    assertThat(everything(traced)).doesNotContain(SECRET);
    // and the surrounding, non-sensitive content is still there to debug with
    assertThat(everything(traced)).contains("ege");
  }

  @Test
  @DisplayName("a secret in a header never reaches any traced message")
  void secretInHeaderIsMasked() throws Exception {
    List<Message> traced = run("{\"user\":\"ege\"}", true, true);

    assertThat(everything(traced)).doesNotContain(SECRET);
    assertThat(everything(traced)).contains("Authorization");
  }

  @Test
  @DisplayName("masking survives every hop, not just the first")
  void maskingAppliesToEveryHop() throws Exception {
    List<Message> traced = run("{\"password\":\"" + SECRET + "\"}", true, true);

    // one message per hop per direction; each is built by a different tracer
    assertThat(traced).hasSizeGreaterThan(3);
    assertThat(traced).allSatisfy(m -> {
      assertThat(m.getMessageBody() == null || !m.getMessageBody().contains(SECRET)).isTrue();
      assertThat(m.getHeaders() == null || !m.getHeaders().contains(SECRET)).isTrue();
    });
  }

  @Test
  @DisplayName("turning masking off records the secret, proving the tests above are not vacuous")
  void disabledMaskingRecordsTheSecret() throws Exception {
    List<Message> traced = run("{\"password\":\"" + SECRET + "\"}", false, true);

    // if this ever fails, the assertions above stopped proving anything
    assertThat(everything(traced)).contains(SECRET);
  }

  @Test
  @DisplayName("body capture off is a guarantee: no body text is recorded at all")
  void bodyCaptureOffRecordsNothing() throws Exception {
    // the key at fault is one nobody configured, so masking could never have caught it - this is
    // the setting to reach for when a body must not be captured under any circumstances
    List<Message> traced = run("{\"anything\":\"" + SECRET + "\"}", true, false);

    assertThat(traced).isNotEmpty();
    assertThat(traced).allSatisfy(m -> assertThat(m.getMessageBody()).isIn(null, ExchangeUtils.BODY_NOT_CAPTURED));
    assertThat(everything(traced)).doesNotContain(SECRET);
  }

  @Test
  @DisplayName("body capture off does NOT protect headers - masking is still what does that")
  void bodyCaptureOffDoesNotProtectHeaders() throws Exception {
    // worth pinning because it is an easy assumption to make: the two settings cover different
    // things, and turning bodies off while turning masking off leaves Authorization in the clear
    List<Message> traced = run("{\"anything\":\"x\"}", false, false);

    assertThat(everything(traced)).contains(SECRET);
  }
}
