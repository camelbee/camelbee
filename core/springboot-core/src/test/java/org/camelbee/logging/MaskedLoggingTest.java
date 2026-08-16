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

package org.camelbee.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.masking.Masker;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.camelbee.tracers.ExchangeCompletedEventTracer;
import org.camelbee.tracers.ExchangeCreatedEventTracer;
import org.camelbee.tracers.ExchangeSendingEventTracer;
import org.camelbee.tracers.ExchangeSentEventTracer;
import org.camelbee.tracers.NodeIdInterceptStrategy;
import org.camelbee.tracers.TracerService;
import org.camelbee.utils.ExchangeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Structured logging is a second way traced data leaves the application, and a worse one than the
 * REST API: log lines are shipped off the host, retained for months, and read by people who never
 * opened the CamelBee UI. So {@code camelbee.logging-enabled} must not be a way around masking.
 *
 * <p>{@link LoggingService} only ever reads from the {@link Message} it is handed - body, headers,
 * routeId, exception - and never from the Exchange. These tests capture the Messages the logging
 * path receives and assert they are already redacted, which is what makes the log safe. If someone
 * later reads the body straight off the exchange here "just for the log line", the wiring these
 * assert stops being true and this fails.
 *
 * <p>Written against the Messages rather than captured log output so it runs identically on all
 * three cores: quarkus-core runs on JBoss LogManager, not logback, so an appender-based test could
 * not be mirrored.
 *
 * <p>{@code logging-enabled} works independently of {@code tracer-enabled}, so the combination
 * exercised here is logging ON with the UI tracer OFF - what an application would actually run.
 */
class MaskedLoggingTest {

  private static final String SECRET = "hunter2-must-not-be-logged";

  /** Records every Message the logging path was asked to log. */
  private static final class CapturingLoggingService extends LoggingService {

    private final List<Message> logged = new ArrayList<>();

    @Override
    public void logMessage(Message message, String logMessage, boolean clearMdc) {
      if (message != null) {
        logged.add(message);
      }
      super.logMessage(message, logMessage, clearMdc);
    }
  }

  @AfterEach
  void restore() {
    MdcContext.clearAll();
    ExchangeUtils.configureMasking(Masker.withDefaults(), true);
  }

  private String everythingLogged(boolean maskingEnabled) throws Exception {
    ExchangeUtils.configureMasking(
        maskingEnabled ? Masker.withDefaults() : Masker.disabled(), true);

    CapturingLoggingService loggingService = new CapturingLoggingService();
    MessageService messageService = new MessageService(1000);

    try (CamelContext context = new DefaultCamelContext()) {
      RouteContextService routeContextService = mock(RouteContextService.class);
      when(routeContextService.getCamelRoutes()).thenReturn(List.of());

      // logging on, UI tracer OFF
      TracerService tracerService = new TracerService(true, false, 60000,
          new ExchangeCreatedEventTracer(),
          new ExchangeSendingEventTracer(messageService, routeContextService),
          new ExchangeSentEventTracer(),
          new ExchangeCompletedEventTracer(),
          messageService,
          loggingService);

      context.getManagementStrategy().addEventNotifier(new CamelBeeEventNotifier(tracerService));
      context.getCamelContextExtension().addInterceptStrategy(new NodeIdInterceptStrategy(tracerService));

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          // a non-direct endpoint, because LoggingService skips direct: hops
          from("direct:start").routeId("main").to("mock:target").id("toTarget");
        }
      });

      context.start();
      ProducerTemplate template = context.createProducerTemplate();
      template.sendBodyAndHeader("direct:start",
          "{\"user\":\"ege\",\"password\":\"" + SECRET + "\"}",
          "Authorization", "Bearer " + SECRET);
      Thread.sleep(200);
    }

    assertThat(loggingService.logged)
        .as("the logging path was never invoked - the assertions would prove nothing")
        .isNotEmpty();

    StringBuilder all = new StringBuilder();
    loggingService.logged.forEach(m -> all
        .append(m.getMessageBody()).append('\n')
        .append(m.getHeaders()).append('\n'));
    return all.toString();
  }

  @Test
  @DisplayName("nothing sensitive reaches the log, body or headers")
  void logsAreMasked() throws Exception {
    String logged = everythingLogged(true);

    assertThat(logged).doesNotContain(SECRET);
    // the surrounding content is still logged, so this is redaction and not suppression
    assertThat(logged).contains("ege");
    assertThat(logged).contains(Masker.MASK);
  }

  @Test
  @DisplayName("with masking off the secret IS logged, so the test above is not vacuous")
  void withoutMaskingTheSecretIsLogged() throws Exception {
    assertThat(everythingLogged(false)).contains(SECRET);
  }
}
