package org.camelbee.debugger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.model.exchange.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageServiceTest {

  private MessageService messageService;

  @BeforeEach
  void setUp() {
    messageService = new MessageService(1000);
  }

  @Test
  void getMessagesFromShouldReturnDefensiveCopy() {
    // Arrange
    Message message = createTestMessage("exchange-1");
    messageService.addMessage(message);

    // Act
    MessageListWithInfo result = messageService.getMessagesFrom(0, 0, 0);

    // Assert
    assertEquals(1, result.getMessages().size());
    assertNotSame(messageService.getMessageList(), result.getMessages());
  }

  @Test
  void getMessagesFromShouldReturnEmptyWhenVersionsMatch() {
    // Arrange
    Message message = createTestMessage("exchange-1");
    messageService.addMessage(message);
    long currentAddVersion = messageService.getMessageListInfo().getAddVersion();
    long currentResetVersion = messageService.getMessageListInfo().getResetVersion();

    // Act
    MessageListWithInfo result = messageService.getMessagesFrom(0, currentAddVersion, currentResetVersion);

    // Assert
    assertTrue(result.getMessages().isEmpty());
  }

  @Test
  void getMessagesFromShouldReturnMessagesWhenVersionChanged() {
    // Arrange
    Message message1 = createTestMessage("exchange-1");
    Message message2 = createTestMessage("exchange-2");
    messageService.addMessage(message1);
    messageService.addMessage(message2);

    // Act - pass stale versions (0, 0) so they don't match current
    MessageListWithInfo result = messageService.getMessagesFrom(0, 0, 0);

    // Assert
    assertEquals(2, result.getMessages().size());
  }

  @Test
  void resetShouldIncrementResetVersionAndClearMessages() {
    // Arrange
    Message message = createTestMessage("exchange-1");
    messageService.addMessage(message);
    assertEquals(1, messageService.getMessageList().size());

    // Act
    messageService.reset();

    // Assert
    assertTrue(messageService.getMessageList().isEmpty());
    assertEquals(1, messageService.getMessageListInfo().getResetVersion());
    assertEquals(0, messageService.getMessageListInfo().getAddVersion());
  }

  @Test
  void addMessageShouldIncrementAddVersion() {
    // Arrange
    long initialAddVersion = messageService.getMessageListInfo().getAddVersion();

    // Act
    messageService.addMessage(createTestMessage("exchange-1"));
    messageService.addMessage(createTestMessage("exchange-2"));

    // Assert
    assertEquals(initialAddVersion + 2, messageService.getMessageListInfo().getAddVersion());
  }

  private Message createTestMessage(String exchangeId) {
    return new Message(exchangeId, MessageEventType.SENT, "test-body", "test-headers",
        "direct:caller", "direct:current", "endpoint-1", MessageType.RESPONSE, null);
  }
}
