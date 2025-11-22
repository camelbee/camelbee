package org.camelbee.debugger.model.exchange;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Enhanced MessageList that includes version information.
 */
@RegisterForReflection
public class MessageListWithInfo {

  private final List<Message> messages;
  private final MessageListInfo info;

  public MessageListWithInfo(List<Message> messages, MessageListInfo info) {
    this.messages = messages;
    this.info = info;
  }

  public List<Message> getMessages() {
    return messages;
  }

  public MessageListInfo getInfo() {
    return info;
  }

}
