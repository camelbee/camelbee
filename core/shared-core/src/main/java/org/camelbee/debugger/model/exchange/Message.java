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

import org.camelbee.utils.ExchangeUtils;
import org.camelbee.utils.UriSanitizer;

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

  private final long timeTaken;

  private String parentExchangeId;

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
    this(exchangeId, exchangeEventType, messageBody, headers, routeId, endpoint, endpointId, messageType, exception, 0L);
  }

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
   * @param timeTaken   Elapsed time in milliseconds from {@code ExchangeSentEvent.getTimeTaken()},
   *                    or 0 for message types that don't carry it (CREATED/SENDING/COMPLETED).
   */
  @SuppressWarnings("java:S107")
  public Message(String exchangeId, MessageEventType exchangeEventType, String messageBody, String headers, String routeId, String endpoint,
      String endpointId, MessageType messageType, String exception, long timeTaken) {
    this.exchangeId = exchangeId;
    this.exchangeEventType = exchangeEventType;
    this.messageBody = messageBody;
    this.headers = headers;
    /*
     Both of these can hold an endpoint URI - routeId falls back to the URI for a consumer-started
     route - and a URI can carry credentials in its query or its user-info. Redacted here rather
     than at the tracers' several construction sites, for the same reason bodies and headers are
     redacted inside ExchangeUtils: a Message is served over HTTP and written to the log, so there
     must be no way to build one that was not redacted.

     The topology applies this same method, which matters beyond redaction: the UI matches a
     message's endpoint against the topology's URIs and some of those comparisons are exact, so
     sanitizing one side only would silently break edge matching.
     */
    this.routeId = UriSanitizer.sanitize(routeId, ExchangeUtils.getMasker());
    this.endpoint = UriSanitizer.sanitize(endpoint, ExchangeUtils.getMasker());
    this.endpointId = endpointId;
    this.messageType = messageType;
    this.exception = exception;
    this.timeStamp = "%d".formatted(System.currentTimeMillis());
    this.timeTaken = timeTaken;
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

  /** Sanitized like the constructor's - this setter must not be a way around the redaction. */
  public void setRouteId(String routeId) {
    this.routeId = UriSanitizer.sanitize(routeId, ExchangeUtils.getMasker());
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

  public long getTimeTaken() {
    return timeTaken;
  }

  /**
   * Id of the exchange this one was copied from, or null when the exchange was not a copy - which is
   * the common case. Set once, before the message is published to the message list.
   *
   * <p>Non-null for the children of wireTap, multicast, split, recipientList and async handoffs,
   * whose copies each get a fresh exchange id and would otherwise appear unrelated to the exchange
   * that spawned them.
   *
   * @return the parent exchange id, or null.
   */
  public String getParentExchangeId() {
    return parentExchangeId;
  }

  public void setParentExchangeId(String parentExchangeId) {
    this.parentExchangeId = parentExchangeId;
  }
}
