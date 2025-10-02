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
package org.camelbee.debugger.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.camel.CamelContext;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageListInfo;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.tracers.TracerService;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracerControllerTest {

  @Mock
  private CamelContext camelContext;

  @Mock
  private MessageService messageService;

  @Mock
  private TracerService tracerService;

  @Mock
  private Config config;

  @InjectMocks
  private TracerController tracerController;

  private static final String TEST_ROUTE_ID_1 = "route1";
  private static final String TEST_ROUTE_ID_2 = "route2";

  @BeforeEach
  void setUp() {
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR, "Test Vendor");
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VERSION, "11.0.1");
  }

  @Test
  void getMessagesShouldReturnMessageListWithDifferentEventTypes() {
    // Arrange
    List<Message> mockMessages = Arrays.asList(
        new Message("id1", MessageEventType.CREATED, "body1", "headers1", TEST_ROUTE_ID_1, "endpoint1", "endpointId1", MessageType.REQUEST, null),
        new Message("id2", MessageEventType.SENDING, "body2", "headers2", TEST_ROUTE_ID_1, "endpoint2", "endpointId2", MessageType.RESPONSE, null),
        new Message("id3", MessageEventType.SENT, "body3", "headers3", TEST_ROUTE_ID_2, "endpoint3", "endpointId3", MessageType.REQUEST, null),
        new Message("id4", MessageEventType.COMPLETED, "body4", "headers4", TEST_ROUTE_ID_2, "endpoint4", "endpointId4", MessageType.RESPONSE, null)
    );
    MessageListInfo messageListInfo = new MessageListInfo(1, 0, 0, Instant.now(), Instant.now());

    MessageListWithInfo messageListWithInfo = new MessageListWithInfo(mockMessages, messageListInfo);

    when(messageService.getMessagesFrom(0, 0, 0)).thenReturn(messageListWithInfo);

    // Act
    Response response = tracerController.getMessages(0, 0, 0);

    // Assert
    assertEquals(200, response.getStatus());
    MessageListWithInfo messageList = (MessageListWithInfo) response.getEntity();
    assertNotNull(messageList);
    assertEquals(4, messageList.getMessages().size());

    // Verify different message event types
    assertEquals(MessageEventType.CREATED, messageList.getMessages().get(0).getExchangeEventType());
    assertEquals(MessageEventType.SENDING, messageList.getMessages().get(1).getExchangeEventType());
    assertEquals(MessageEventType.SENT, messageList.getMessages().get(2).getExchangeEventType());
    assertEquals(MessageEventType.COMPLETED, messageList.getMessages().get(3).getExchangeEventType());

    // Verify other message properties
    Message firstMessage = messageList.getMessages().get(0);
    assertEquals("id1", firstMessage.getExchangeId());
    assertEquals("body1", firstMessage.getMessageBody());
    assertEquals("headers1", firstMessage.getHeaders());
    assertEquals(TEST_ROUTE_ID_1, firstMessage.getRouteId());
    assertEquals("endpoint1", firstMessage.getEndpoint());
    assertEquals("endpointId1", firstMessage.getEndpointId());
    assertEquals(MessageType.REQUEST, firstMessage.getMessageType());
    assertNull(firstMessage.getException());
    assertNotNull(firstMessage.getTimeStamp());
  }

  @Test
  void getMessagesShouldHandleMessagesWithNullEventType() {
    // Arrange
    List<Message> mockMessages = Arrays.asList(
        new Message("id1", null, "body1", "headers1", TEST_ROUTE_ID_1, "endpoint1", "endpointId1", MessageType.REQUEST, null)
    );
    MessageListInfo messageListInfo = new MessageListInfo(1, 0, 0, Instant.now(), Instant.now());

    MessageListWithInfo messageListWithInfo = new MessageListWithInfo(mockMessages, messageListInfo);

    when(messageService.getMessagesFrom(0, 0, 0)).thenReturn(messageListWithInfo);

    // Act
    Response response = tracerController.getMessages(0, 0, 0);

    // Assert
    assertEquals(200, response.getStatus());
    MessageListWithInfo messageList = (MessageListWithInfo) response.getEntity();
    assertNotNull(messageList);
    assertEquals(1, messageList.getMessages().size());
    assertNull(messageList.getMessages().get(0).getExchangeEventType());
  }

  @Test
  void getMessagesShouldReturnEmptyListWhenNoMessages() {
    // Arrange
    MessageListInfo messageListInfo = new MessageListInfo(1, 1, 1, Instant.now(), Instant.now());
    MessageListWithInfo messageListWithInfo = new MessageListWithInfo(new ArrayList<>(), messageListInfo);
    when(messageService.getMessagesFrom(0, 0, 0)).thenReturn(messageListWithInfo);

    // Act
    Response response = tracerController.getMessages(0, 0, 0);

    // Assert
    assertEquals(200, response.getStatus());
    MessageListWithInfo messageList = (MessageListWithInfo) response.getEntity();
    assertNotNull(messageList);
    assertTrue(messageList.getMessages().isEmpty());
  }

  @Test
  void deleteMessagesShouldResetMessageService() {
    // Act
    Response response = tracerController.deleteMessages();

    // Assert
    assertEquals(200, response.getStatus());
    assertEquals("deleted.", response.getEntity());
    verify(messageService).reset();
  }

}
