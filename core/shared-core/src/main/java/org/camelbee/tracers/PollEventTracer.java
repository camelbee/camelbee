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

package org.camelbee.tracers;

import java.util.List;
import org.apache.camel.Exchange;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.utils.ExchangeUtils;
import org.camelbee.utils.TracerUtils;

/**
 * Builds the traced messages for a {@code poll()} or {@code pollEnrich()} hop.
 *
 * <p>These two are the only EIPs CamelBee cannot trace from Camel events. {@code SendProcessor}
 * notifies {@code ExchangeSendingEvent} and {@code ExchangeSentEvent}; {@code PollProcessor} and
 * {@code PollEnricher} call {@code PollingConsumer.receive(...)} and notify nothing, because a poll
 * is a receive rather than a send. There is no {@code ExchangeCreatedEvent} to fall back on either -
 * the polled exchange is built inside the consumer, below the event layer, and its message is merged
 * into the existing exchange, which keeps its id.
 *
 * <p>So the hop is reconstructed by {@link PollInterceptStrategy}, which wraps the node itself. What
 * that can observe is deliberately limited, and this class asserts nothing beyond it:
 *
 * <ul>
 * <li>that the hop happened, and against which endpoint - {@code Exchange.TO_ENDPOINT} is set to
 * the polled URI by the time the node returns, including when it timed out;</li>
 * <li>how long it waited;</li>
 * <li>the body before and after. For {@code poll()} the polled message replaces the body, so the
 * response body is what was received. For {@code pollEnrich()} the user's aggregation strategy
 * decides, and one that keeps the original leaves the exchange untouched - so an unchanged
 * body here does NOT mean nothing was received, and this class does not imply that it does.
 * Distinguishing the two would require wrapping the polling consumer.</li>
 * </ul>
 */
public class PollEventTracer {

  /**
   * Builds the request/response pair describing one poll.
   *
   * <p>Both messages are built after the node returns, not one on each side, because
   * {@code Exchange.TO_ENDPOINT} only names the polled endpoint once the poll has run - before it
   * still holds the previous one. Message timestamps itself on construction, so the request carries
   * the completion time rather than the moment the poll began; the real start is
   * {@code timeStamp - timeTaken}. Nothing orders by that field today (the timeline uses arrival
   * order) and a poll produces no nested traffic, so the pair cannot be split by other messages.
   *
   * @param exchange    the exchange that performed the poll.
   * @param nodeId      the id of the poll/pollEnrich node.
   * @param callerRoute the route the exchange was in when it reached the node.
   * @param requestBody the body as it was before the poll.
   * @param timeTaken   how long the node took, in milliseconds.
   * @return the messages to record, never null.
   */
  public List<Message> traceEvent(Exchange exchange, String nodeId, String callerRoute,
      String requestBody, long timeTaken) {

    final String polledEndpoint = exchange.getProperty(Exchange.TO_ENDPOINT, String.class);

    if (polledEndpoint == null) {
      // nothing identifies what was polled, so recording a hop would be guesswork
      return List.of();
    }

    final String responseBody = ExchangeUtils.readBodyAsString(exchange, false);
    final String headers = ExchangeUtils.getHeaders(exchange);
    final String errorMessage = TracerUtils.handleError(exchange, polledEndpoint);

    Message request = TracerUtils.stampParentExchangeId(
        new Message(exchange.getExchangeId(), MessageEventType.SENDING, requestBody,
            headers, callerRoute, polledEndpoint, nodeId, MessageType.REQUEST, null), exchange);

    Message response = TracerUtils.stampParentExchangeId(
        new Message(exchange.getExchangeId(), MessageEventType.SENT, responseBody,
            headers, callerRoute, polledEndpoint, nodeId,
            errorMessage != null ? MessageType.ERROR_RESPONSE : MessageType.RESPONSE, errorMessage,
            timeTaken), exchange);

    return List.of(request, response);
  }
}
