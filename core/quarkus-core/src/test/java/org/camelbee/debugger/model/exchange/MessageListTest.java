package org.camelbee.debugger.model.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageListTest {

  @Test
  void messageListInfo_exposesAllFields() {
    Instant modified = Instant.ofEpochMilli(1000);
    Instant reset = Instant.ofEpochMilli(500);
    MessageListInfo info = new MessageListInfo(7, 2, 5, modified, reset);

    assertThat(info.getCount()).isEqualTo(7);
    assertThat(info.getResetVersion()).isEqualTo(2);
    assertThat(info.getAddVersion()).isEqualTo(5);
    assertThat(info.getLastModified()).isEqualTo(modified);
    assertThat(info.getLastResetTime()).isEqualTo(reset);
  }

  @Test
  void messageListWithInfo_exposesMessagesAndInfo() {
    Message message = new Message("ex-1", MessageEventType.SENDING, "body", "headers",
        "route-1", "kafka:topic", "ep-1", MessageType.REQUEST, null);
    MessageListInfo info = new MessageListInfo(1, 0, 1, Instant.now(), Instant.now());

    MessageListWithInfo withInfo = new MessageListWithInfo(List.of(message), info);

    assertThat(withInfo.getMessages()).containsExactly(message);
    assertThat(withInfo.getInfo()).isSameAs(info);
  }
}
