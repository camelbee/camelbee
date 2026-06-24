package org.camelbee.logging;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the error-handling branch of {@link LoggingService#logMessage}: enriching the MDC fails
 * (here because the message has a null messageType), and the failure is swallowed/logged rather
 * than propagated.
 */
class LoggingServiceExtraTest {

  private final LoggingService service = new LoggingService();

  @AfterEach
  void cleanup() {
    MdcContext.clearAll();
  }

  @Test
  void logMessage_swallowsErrorWhenMdcEnrichmentFails() {
    Message message = new Message("ex-1", MessageEventType.SENDING, "body", "headers",
        "route-1", "kafka:topic", "ep-1", null, null);

    assertThatNoException().isThrownBy(() -> service.logMessage(message, "custom", true));
  }
}
