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

package org.camelbee.debugger.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageListInfo;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MessageService with dual version control for reset and addition operations.
 */
@Component
public class MessageService {

  private final long maxTracedMessageCount;

  private List<Message> messageList = new CopyOnWriteArrayList<>();

  // Version that increments when the list is reset (cleared)
  private final AtomicLong resetVersion = new AtomicLong(0);

  // Version that increments when messages are added
  private final AtomicLong addVersion = new AtomicLong(0);

  private volatile Instant lastModified = Instant.now();
  private volatile Instant lastResetTime = Instant.now();

  public List<Message> getMessageList() {
    return messageList;
  }

  /**
   * Constructor.
   *
   * @param maxTracedMessageCount The maxTracedMessageCount.
   */
  public MessageService(
      @Value("${camelbee.tracer-max-messages-count:1000}") long maxTracedMessageCount) {
    this.maxTracedMessageCount = maxTracedMessageCount;
  }

  /**
   * Add message to the messageList for the CamelBee WebGl application.
   * Increments the addVersion to track new messages.
   *
   * @param message The message.
   */
  public void addMessage(Message message) {
    if (message != null && maxTracedMessageCount > messageList.size()) {
      messageList.add(message);
      addVersion.incrementAndGet();
      lastModified = Instant.now();
    }
  }

  /**
   * Reset the message list and increment the reset version.
   * This also resets the addVersion to 0 as we're starting fresh.
   */
  public void reset() {
    messageList.clear();
    resetVersion.incrementAndGet();
    addVersion.set(0); // Reset add version when list is cleared
    lastModified = Instant.now();
    lastResetTime = Instant.now();
  }

  /**
   * Returns messages starting from the specified index along with version info.
   *
   * @param fromIndex The index to start retrieving messages from (0-based)
   * @return MessageListWithInfo containing messages and metadata
   */
  public MessageListWithInfo getMessagesFrom(int fromIndex, long addVersion, long resetVersion) {
    MessageListInfo info = getMessageListInfo();

    List<Message> messages = new ArrayList<>();

    if (this.addVersion.get() != addVersion || this.resetVersion.get() != resetVersion) {
      List<Message> allMessages = getMessageList();

      if (fromIndex >= 0 && fromIndex < allMessages.size()) {
        messages = allMessages.subList(fromIndex, allMessages.size());
      }
    }

    return new MessageListWithInfo(messages, info);
  }

  /**
   * Returns metadata about the message list including counts, versions, and timestamps.
   *
   * @return MessageListInfo containing metadata
   */
  public MessageListInfo getMessageListInfo() {
    List<Message> messages = getMessageList();
    return new MessageListInfo(
        messages.size(),
        resetVersion.get(),
        addVersion.get(),
        lastModified,
        lastResetTime
    );
  }
}
