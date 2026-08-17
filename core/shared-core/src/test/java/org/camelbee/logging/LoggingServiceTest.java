package org.camelbee.logging;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.junit.jupiter.api.Test;

class LoggingServiceTest {

  private final LoggingService service = new LoggingService();

  private Message message(String endpoint) {
    return new Message("ex-1", MessageEventType.SENDING, "body", "headers",
        "route-1", endpoint, "ep-1", MessageType.REQUEST, null);
  }

  @Test
  void logMessage_skipsNullMessage() {
    assertThatNoException().isThrownBy(() -> service.logMessage(null, "msg", true));
  }

  @Test
  void logMessage_skipsDirectEndpoint() {
    assertThatNoException()
        .isThrownBy(() -> service.logMessage(message("direct:internal"), "msg", true));
  }

  @Test
  void logMessage_skipsNullEndpoint() {
    assertThatNoException().isThrownBy(() -> service.logMessage(message(null), "msg", true));
  }

  @Test
  void logMessage_logsRealEndpointAndClearsMdc() {
    assertThatNoException()
        .isThrownBy(() -> service.logMessage(message("kafka:topic"), "custom", true));
  }

  @Test
  void logMessage_logsWithDefaultMessageAndKeepsMdc() {
    assertThatNoException()
        .isThrownBy(() -> service.logMessage(message("http://host/api"), null, false));
    MdcContext.clearAll();
  }
}
