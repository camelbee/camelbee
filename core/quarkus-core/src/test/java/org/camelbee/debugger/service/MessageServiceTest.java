package org.camelbee.debugger.service;

import static org.junit.jupiter.api.Assertions.*;

import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.model.exchange.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  private MessageService messageService;

  @BeforeEach
  void setUp() {
    messageService = new MessageService(1000);
  }

  private Message createTestMessage(String exchangeId) {
    return new Message(exchangeId, MessageEventType.SENT, "body", "headers",
        "routeId", "endpoint", "endpointId", MessageType.REQUEST, null);
  }

  @Test
  void getMessagesFromShouldReturnDefensiveCopy() {
    // Arrange
    messageService.addMessage(createTestMessage("exchange-1"));

    // Act
    MessageListWithInfo result = messageService.getMessagesFrom(0, -1, -1);

    // Assert
    assertEquals(1, result.getMessages().size());
    result.getMessages().clear();
    assertEquals(1, messageService.getMessageList().size());
  }

  @Test
  void getMessagesFromShouldReturnEmptyWhenVersionsMatch() {
    // Arrange
    messageService.addMessage(createTestMessage("exchange-1"));
    MessageListWithInfo firstCall = messageService.getMessagesFrom(0, -1, -1);
    long addVersion = firstCall.getInfo().getAddVersion();
    long resetVersion = firstCall.getInfo().getResetVersion();

    // Act
    MessageListWithInfo result = messageService.getMessagesFrom(1, addVersion, resetVersion);

    // Assert
    assertTrue(result.getMessages().isEmpty());
  }

  @Test
  void getMessagesFromShouldReturnMessagesWhenVersionChanged() {
    // Arrange
    messageService.addMessage(createTestMessage("exchange-1"));
    MessageListWithInfo firstCall = messageService.getMessagesFrom(0, -1, -1);
    long addVersion = firstCall.getInfo().getAddVersion();
    long resetVersion = firstCall.getInfo().getResetVersion();

    messageService.addMessage(createTestMessage("exchange-2"));

    // Act
    MessageListWithInfo result = messageService.getMessagesFrom(1, addVersion, resetVersion);

    // Assert
    assertEquals(1, result.getMessages().size());
    assertEquals("exchange-2", result.getMessages().get(0).getExchangeId());
  }

  @Test
  void resetShouldIncrementResetVersionAndClearMessages() {
    // Arrange
    messageService.addMessage(createTestMessage("exchange-1"));
    messageService.addMessage(createTestMessage("exchange-2"));
    long resetVersionBefore = messageService.getMessagesFrom(0, -1, -1).getInfo().getResetVersion();

    // Act
    messageService.reset();

    // Assert
    assertTrue(messageService.getMessageList().isEmpty());
    long resetVersionAfter = messageService.getMessagesFrom(0, -1, -1).getInfo().getResetVersion();
    assertEquals(resetVersionBefore + 1, resetVersionAfter);
  }

  @Test
  void addMessageShouldIncrementAddVersion() {
    // Arrange
    long addVersionBefore = messageService.getMessagesFrom(0, -1, -1).getInfo().getAddVersion();

    // Act
    messageService.addMessage(createTestMessage("exchange-1"));

    // Assert
    long addVersionAfter = messageService.getMessagesFrom(0, -1, -1).getInfo().getAddVersion();
    assertEquals(addVersionBefore + 1, addVersionAfter);
  }
}
