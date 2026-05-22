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

package org.camelbee.debugger.model.exchange;

/**
 * Message.
 */
public class Message {

  private final String exchangeId;

  private final MessageEventType exchangeEventType;

  private final String messageBody;

  private final String headers;

  private String routeId;

  private final String endpoint;

  private final String endpointId;

  private final MessageType messageType;

  private final String exception;

  private final String timeStamp;

  /**
   * Message Constructor.
   *
   * @param exchangeId  The exchangeId.
   * @param messageBody The messageBody.
   * @param headers     The headers.
   * @param routeId     The routeId.
   * @param endpoint    The endpoint.
   * @param messageType The messageType.
   * @param exception   The exception.
   */
  @SuppressWarnings("java:S107")
  public Message(String exchangeId, MessageEventType exchangeEventType, String messageBody, String headers, String routeId, String endpoint,
      String endpointId, MessageType messageType, String exception) {
    this.exchangeId = exchangeId;
    this.exchangeEventType = exchangeEventType;
    this.messageBody = messageBody;
    this.headers = headers;
    this.routeId = routeId;
    this.endpoint = endpoint;
    this.endpointId = endpointId;
    this.messageType = messageType;
    this.exception = exception;
    this.timeStamp = "%d".formatted(System.currentTimeMillis());
  }

  public String getExchangeId() {
    return exchangeId;
  }

  public MessageEventType getExchangeEventType() {
    return exchangeEventType;
  }

  public String getMessageBody() {
    return messageBody;
  }

  public String getHeaders() {
    return headers;
  }

  public String getRouteId() {
    return routeId;
  }

  public void setRouteId(String routeId) {
    this.routeId = routeId;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getEndpointId() {
    return endpointId;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public String getException() {
    return exception;
  }

  public String getTimeStamp() {
    return timeStamp;
  }
}
