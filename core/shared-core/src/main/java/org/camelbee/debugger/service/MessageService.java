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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageListInfo;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;

/**
 * MessageService with dual version control for reset and addition operations.
 */
public class MessageService {

  private final long maxTracedMessageCount;

  private List<Message> messageList = new CopyOnWriteArrayList<>();

  // Version that increments when the list is reset (cleared)
  private final AtomicLong resetVersion = new AtomicLong(0);

  // Version that increments when messages are added
  private final AtomicLong addVersion = new AtomicLong(0);

  private volatile Instant lastModified = Instant.now();
  private volatile Instant lastResetTime = Instant.now();

  // True once maxTracedMessageCount has been hit and further messages are being dropped
  private volatile boolean capReached = false;

  /** Sentinel: ConcurrentHashMap cannot hold a null value, and "no parent" must be remembered. */
  private static final String NO_PARENT = "";

  /**
   * Write-once parent per exchange - see {@link #applyRememberedParent(Message)}.
   *
   * <p>Bounded by insertion order rather than left to grow. Before the capture filter existed this
   * only grew when a message was actually recorded, so the message cap bounded it too; now it has to
   * learn the parent of exchanges whose messages are DROPPED, otherwise a filtered flow's children
   * could not be recognised. In a production app with a narrow filter that is most exchanges, for as
   * long as tracing stays on.
   */
  private final Map<String, String> parentByExchange = Collections.synchronizedMap(
      new LinkedHashMap<>(16, 0.75f, false) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
          return size() > PARENT_MEMORY_LIMIT;
        }
      });

  /**
   * How many exchange parents to remember. Generous - the cost is one short string pair each - but
   * finite, so a long tracing session cannot grow without bound.
   */
  private static final int PARENT_MEMORY_LIMIT = 50_000;

  /**
   * Substring every captured message must contain, or null to capture everything.
   *
   * <p>Applied at capture rather than in the UI. Client-side filtering hides messages that were
   * already recorded, which in production is exactly the wrong way round: the point is to keep the
   * one transaction under investigation and never record the rest.
   *
   * <p>Matched against the text AFTER masking, so a value that redaction removes cannot be filtered
   * on. That is deliberate - being able to search by a secret would defeat masking.
   */
  private volatile String captureFilter;

  /**
   * Exchanges known to belong to a filtered flow. Only matches land here, so with a narrow filter it
   * stays small - which is the whole point of filtering at capture.
   */
  private final Set<String> matchedExchanges = ConcurrentHashMap.newKeySet();

  public List<Message> getMessageList() {
    return messageList;
  }

  /**
   * Constructor.
   *
   * @param maxTracedMessageCount The maxTracedMessageCount.
   */
  public MessageService(
      long maxTracedMessageCount) {
    this.maxTracedMessageCount = maxTracedMessageCount;
  }

  /**
   * Add message to the messageList for the CamelBee WebGl application.
   * Increments the addVersion to track new messages.
   *
   * @param message The message.
   */
  public void addMessage(Message message) {
    if (message == null) {
      return;
    }
    /*
     Before the filter: parentage must be pinned even for a message that is about to be dropped,
     because a later child of this exchange is recognised through it.
     */
    applyRememberedParent(message);

    if (!shouldCapture(message)) {
      return;
    }

    if (maxTracedMessageCount > messageList.size()) {
      messageList.add(message);
      addVersion.incrementAndGet();
      lastModified = Instant.now();
    } else {
      capReached = true;
    }
  }

  /**
   * Pins an exchange's parent to whatever was resolved for its FIRST traced message, and forces
   * every later message of that exchange to agree.
   *
   * <p>The tracers resolve the parent from exchange properties, which are only trustworthy the
   * first time an exchange is seen. Camel's aggregating EIPs copy a branch's properties back onto
   * the original when merging results: {@code MulticastProcessor} guards its correlation id against
   * that, but {@code PollEnricher} does not - so after {@code pollEnrich} drains a queue that a
   * {@code wireTap} fed, the ORIGINAL exchange carries its own descendant's correlation id and
   * would otherwise report that descendant as its parent. That is a cycle, and it splits one
   * request into a dozen unrelated flows in the UI.
   *
   * <p>Deciding once, here, is what makes it safe: the first message of an exchange is emitted
   * before any of its branches exist, so nothing has flowed back yet. Bounded by
   * {@code maxTracedMessageCount} (there cannot be more exchanges than messages) and cleared by
   * {@link #reset()}.
   *
   * @param message the message about to be recorded; its parent is rewritten in place.
   */
  private void applyRememberedParent(Message message) {
    final String remembered = parentByExchange.computeIfAbsent(
        message.getExchangeId(),
        exchangeId -> message.getParentExchangeId() == null ? NO_PARENT : message.getParentExchangeId());

    message.setParentExchangeId(NO_PARENT.equals(remembered) ? null : remembered);
  }

  /**
   * Sets the substring a message must contain to be recorded at all.
   *
   * <p>Clears what has matched so far, so changing the filter starts a clean investigation rather
   * than continuing to collect the previous one's flows.
   *
   * @param filter the substring, or null/blank to record everything.
   */
  public void setCaptureFilter(String filter) {
    this.captureFilter = filter == null || filter.isBlank() ? null : filter.toLowerCase(Locale.ROOT);
    matchedExchanges.clear();
  }

  /** The active capture filter, or null when everything is recorded. */
  public String getCaptureFilter() {
    return captureFilter;
  }

  /**
   * Whether this message belongs to a flow the filter selected.
   *
   * <p>Matching is per exchange, not per message: once anything in an exchange matches, the rest of
   * that exchange is kept, and so are its children. A wireTap branch rarely repeats the id its
   * parent was matched on, and recording half a flow would be worse than recording none of it.
   *
   * <p>Known limit: messages of an exchange emitted BEFORE the matching one are not recovered. In
   * practice the identifying value is in the body from the first message, so the match happens
   * there; buffering to cover the rest would mean holding unbounded unmatched traffic in memory,
   * which is the opposite of what this feature is for.
   */
  private boolean shouldCapture(Message message) {
    final String filter = captureFilter;

    if (filter == null) {
      return true;
    }

    final String exchangeId = message.getExchangeId();

    if (matchedExchanges.contains(exchangeId)) {
      return true;
    }

    final String parentExchangeId = message.getParentExchangeId();

    if (parentExchangeId != null && matchedExchanges.contains(parentExchangeId)) {
      matchedExchanges.add(exchangeId);
      return true;
    }

    if (containsIgnoreCase(message.getMessageBody(), filter)
        || containsIgnoreCase(message.getHeaders(), filter)) {
      matchedExchanges.add(exchangeId);
      return true;
    }

    return false;
  }

  private static boolean containsIgnoreCase(String text, String lowerCaseNeedle) {
    return text != null && text.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
  }

  /**
   * Reset the message list and increment the reset version.
   * This also resets the addVersion to 0 as we're starting fresh.
   */
  public void reset() {
    messageList.clear();
    parentByExchange.clear();
    matchedExchanges.clear();
    resetVersion.incrementAndGet();
    addVersion.set(0); // Reset add version when list is cleared
    lastModified = Instant.now();
    lastResetTime = Instant.now();
    capReached = false;
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
      // messageList is a CopyOnWriteArrayList; subList() returns a live view that throws
      // ConcurrentModificationException if addMessage() mutates the list while it's in use.
      // toArray() is an atomic snapshot, so slice that instead.
      Message[] snapshot = getMessageList().toArray(new Message[0]);

      if (fromIndex >= 0 && fromIndex < snapshot.length) {
        messages = new ArrayList<>(Arrays.asList(snapshot).subList(fromIndex, snapshot.length));
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
        lastResetTime,
        capReached
    );
  }
}
