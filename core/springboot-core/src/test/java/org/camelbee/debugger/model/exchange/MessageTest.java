package org.camelbee.debugger.model.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageTest {

  @Test
  void exposesAllFieldsAndGeneratesTimestamp() {
    Message message = new Message("ex-1", MessageEventType.SENDING, "body", "headers",
        "route-1", "kafka:topic", "ep-1", MessageType.REQUEST, "boom");

    assertThat(message.getExchangeId()).isEqualTo("ex-1");
    assertThat(message.getExchangeEventType()).isEqualTo(MessageEventType.SENDING);
    assertThat(message.getMessageBody()).isEqualTo("body");
    assertThat(message.getHeaders()).isEqualTo("headers");
    assertThat(message.getRouteId()).isEqualTo("route-1");
    assertThat(message.getEndpoint()).isEqualTo("kafka:topic");
    assertThat(message.getEndpointId()).isEqualTo("ep-1");
    assertThat(message.getMessageType()).isEqualTo(MessageType.REQUEST);
    assertThat(message.getException()).isEqualTo("boom");
    assertThat(message.getTimeStamp()).isNotBlank();
  }

  @Test
  void allowsUpdatingRouteId() {
    Message message = new Message("ex-1", MessageEventType.SENT, null, null,
        "route-1", "kafka:topic", "ep-1", MessageType.RESPONSE, null);
    message.setRouteId("route-2");
    assertThat(message.getRouteId()).isEqualTo("route-2");
  }
}
