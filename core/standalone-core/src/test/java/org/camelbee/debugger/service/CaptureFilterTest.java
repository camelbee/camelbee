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

import static org.assertj.core.api.Assertions.assertThat;

import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The capture filter exists for production: with hundreds of exchanges a second, recording only the
 * transaction under investigation is both the only readable option and the smallest exposure.
 *
 * <p>It is applied at capture, not in the UI. Filtering in the browser hides messages that were
 * already recorded and already served, which is exactly the wrong way round here.
 */
class CaptureFilterTest {

  private static Message message(String exchangeId, String body, String headers, String parent) {
    Message m = new Message(exchangeId, MessageEventType.SENDING, body, headers, "route1",
        "mock://a", "out-1", MessageType.REQUEST, null);
    m.setParentExchangeId(parent);
    return m;
  }

  private static MessageService serviceWithFilter(String filter) {
    MessageService service = new MessageService(1000);
    service.setCaptureFilter(filter);
    return service;
  }

  @Test
  @DisplayName("no filter records everything, as before")
  void noFilterRecordsEverything() {
    MessageService service = new MessageService(1000);

    service.addMessage(message("ex-1", "anything", "", null));
    service.addMessage(message("ex-2", "anything else", "", null));

    assertThat(service.getMessageList()).hasSize(2);
  }

  @Test
  @DisplayName("records only the exchange whose body matches")
  void recordsOnlyMatchingBody() {
    MessageService service = serviceWithFilter("order-42");

    service.addMessage(message("ex-1", "{\"orderId\":\"order-42\"}", "", null));
    service.addMessage(message("ex-2", "{\"orderId\":\"order-99\"}", "", null));

    assertThat(service.getMessageList()).hasSize(1);
    assertThat(service.getMessageList().get(0).getExchangeId()).isEqualTo("ex-1");
  }

  @Test
  @DisplayName("matches on headers too, not only the body")
  void matchesHeaders() {
    MessageService service = serviceWithFilter("order-42");

    service.addMessage(message("ex-1", "no id here", "X-Order-Id:order-42\n", null));

    assertThat(service.getMessageList()).hasSize(1);
  }

  @Test
  @DisplayName("ignores case, so the user does not have to match the payload exactly")
  void ignoresCase() {
    MessageService service = serviceWithFilter("ORDER-42");

    service.addMessage(message("ex-1", "{\"orderId\":\"order-42\"}", "", null));

    assertThat(service.getMessageList()).hasSize(1);
  }

  @Test
  @DisplayName("keeps the REST of a matched exchange, even where the id never appears again")
  void keepsRestOfMatchedExchange() {
    // matching per message rather than per exchange would record the request and drop the response
    MessageService service = serviceWithFilter("order-42");

    service.addMessage(message("ex-1", "{\"orderId\":\"order-42\"}", "", null));
    service.addMessage(message("ex-1", "OK", "", null));
    service.addMessage(message("ex-1", "done", "", null));

    assertThat(service.getMessageList()).hasSize(3);
  }

  @Test
  @DisplayName("keeps the children of a matched exchange, which rarely repeat the id")
  void keepsChildrenOfMatchedExchange() {
    // a wireTap/multicast branch gets a fresh exchange id and usually a transformed body; half a
    // flow would be worse than none of it
    MessageService service = serviceWithFilter("order-42");

    service.addMessage(message("root", "{\"orderId\":\"order-42\"}", "", null));
    service.addMessage(message("child", "totally different body", "", "root"));

    assertThat(service.getMessageList()).hasSize(2);
  }

  @Test
  @DisplayName("keeps grandchildren, following the chain rather than one level")
  void keepsGrandchildren() {
    MessageService service = serviceWithFilter("order-42");

    service.addMessage(message("root", "{\"orderId\":\"order-42\"}", "", null));
    service.addMessage(message("child", "x", "", "root"));
    service.addMessage(message("grandchild", "y", "", "child"));

    assertThat(service.getMessageList()).hasSize(3);
  }

  @Test
  @DisplayName("does not keep an unrelated exchange that merely has a parent")
  void doesNotKeepUnrelatedBranches() {
    MessageService service = serviceWithFilter("order-42");

    service.addMessage(message("other-root", "{\"orderId\":\"order-99\"}", "", null));
    service.addMessage(message("other-child", "x", "", "other-root"));

    assertThat(service.getMessageList()).isEmpty();
  }

  @Test
  @DisplayName("changing the filter starts a clean investigation")
  void changingFilterResetsMatches() {
    MessageService service = serviceWithFilter("order-42");
    service.addMessage(message("ex-1", "order-42", "", null));

    service.setCaptureFilter("order-99");
    // ex-1 was matched under the old filter and must not keep being collected under the new one
    service.addMessage(message("ex-1", "no id", "", null));

    assertThat(service.getMessageList()).hasSize(1);
  }

  @Test
  @DisplayName("clearing the filter records everything again")
  void clearingFilterRecordsEverything() {
    MessageService service = serviceWithFilter("order-42");
    service.addMessage(message("ex-1", "nope", "", null));
    assertThat(service.getMessageList()).isEmpty();

    service.setCaptureFilter(null);
    service.addMessage(message("ex-2", "nope", "", null));

    assertThat(service.getMessageList()).hasSize(1);
  }

  @Test
  @DisplayName("a blank filter is treated as no filter, not as a match-nothing filter")
  void blankIsNoFilter() {
    MessageService service = serviceWithFilter("   ");

    service.addMessage(message("ex-1", "anything", "", null));

    assertThat(service.getCaptureFilter()).isNull();
    assertThat(service.getMessageList()).hasSize(1);
  }

  @Test
  @DisplayName("reset clears what has matched, so a new session does not inherit the old one")
  void resetClearsMatches() {
    MessageService service = serviceWithFilter("order-42");
    service.addMessage(message("ex-1", "order-42", "", null));

    service.reset();
    service.addMessage(message("ex-1", "no id now", "", null));

    assertThat(service.getMessageList()).isEmpty();
  }

  @Test
  @DisplayName("parentage is still learned for dropped messages, or children could not be found")
  void learnsParentageOfDroppedMessages() {
    MessageService service = serviceWithFilter("order-42");

    // this one is dropped, but its parent link must still be remembered
    service.addMessage(message("child", "no id", "", "root"));
    // ... so that when the parent matches later, the child's own later messages are kept
    service.addMessage(message("root", "order-42", "", null));
    service.addMessage(message("child", "still no id", "", null));

    assertThat(service.getMessageList())
        .extracting(Message::getExchangeId)
        .containsExactly("root", "child");
  }
}
