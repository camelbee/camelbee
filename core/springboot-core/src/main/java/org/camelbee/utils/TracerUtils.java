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

package org.camelbee.utils;

import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_LINEAGE_ROOT;
import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_NODE_ID;
import static org.camelbee.constants.CamelBeeConstants.CAMEL_FAILED_EVENT_ENDPOINT;
import static org.camelbee.constants.CamelBeeConstants.CAMEL_FAILED_EVENT_IDENTITY_HASHCODE;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.camelbee.debugger.model.exchange.Message;

/**
 * TracerUtils.
 */
public class TracerUtils {

  private TracerUtils() {
    // Private constructor
  }

  /**
   * Resolves the id of the node that initiated a send.
   *
   * <p>Prefers Camel's own history node id and only falls back to the id stamped by
   * {@code NodeIdInterceptStrategy}, so the reported value is unchanged wherever Camel already
   * supplies one. The fallback covers the two cases Camel leaves null: sends performed inside an
   * EIP (enrich, wireTap, recipientList, multicast), whose exchange copies do not carry the history
   * node id, and redelivered attempts, for which the node advices do not run again.
   *
   * @param exchange      the exchange being traced.
   * @param historyNodeId Camel's history node id, possibly null.
   * @return the node id, or null when neither source has one.
   */
  public static String resolveNodeId(Exchange exchange, String historyNodeId) {
    if (historyNodeId != null) {
      return historyNodeId;
    }
    return exchange.getProperty(CAMELBEE_NODE_ID, String.class);
  }

  /**
   * Records on the message which exchange this one was copied from, when it was a copy.
   *
   * @param message  the message being built, never null.
   * @param exchange the exchange being traced.
   * @return the same message, for use as {@code return stampParentExchangeId(new Message(...), ex)}.
   */
  public static Message stampParentExchangeId(Message message, Exchange exchange) {
    message.setParentExchangeId(resolveParentExchangeId(exchange));
    return message;
  }

  /**
   * Resolves which exchange this one was copied from, or null when it was not a copy.
   *
   * <p>Two sources, in order:
   *
   * <ol>
   * <li>Camel's {@code ExchangePropertyKey.CORRELATION_ID}, set by
   * {@code ExchangeHelper.createCorrelatedCopy} for enrich, multicast, split, recipientList and
   * routingSlip. It names the <em>immediate</em> parent, and {@code MulticastProcessor} removes and
   * restores it around result aggregation, so it survives the merge back into the original.</li>
   * <li>{@link org.camelbee.constants.CamelBeeConstants#CAMELBEE_LINEAGE_ROOT}, our own write-once
   * property, for wireTap - the one EIP that deletes Camel's correlation id from the copy - and for
   * async handoffs such as seda.</li>
   * </ol>
   *
   * <p>Both are checked against the current exchange id before being believed. That matters for the
   * first: once the aggregating EIPs have merged their branches back, the original exchange carries
   * a correlation id equal to its <em>own</em> id, and treating that as a parent would make the
   * root its own ancestor.
   *
   * @param exchange the exchange being traced.
   * @return the parent exchange id, or null when this exchange started its own lineage.
   */
  public static String resolveParentExchangeId(Exchange exchange) {
    final String self = exchange.getExchangeId();

    final Object correlation = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID);

    if (correlation != null && !self.equals(correlation.toString())) {
      return correlation.toString();
    }

    final String lineageRoot = exchange.getProperty(CAMELBEE_LINEAGE_ROOT, String.class);

    if (lineageRoot == null) {
      /*
       first time this exchange is traced and nothing was inherited, so it starts its own lineage.
       Written once and never rewritten - see the constant's javadoc for why rewriting it would
       reintroduce the corruption this replaced.
       */
      exchange.setProperty(CAMELBEE_LINEAGE_ROOT, self);
      return null;
    }

    return self.equals(lineageRoot) ? null : lineageRoot;
  }

  /**
   * handleError in response tracers.
   *
   * @param exchange           The exchange.
   * @param currentEndpointUri The endpoint URI of the hop currently being traced (the target of a
   *                           SENDING, or the endpoint whose send just completed for a SENT).
   * @return The error message.
   */
  public static String handleError(Exchange exchange, String currentEndpointUri) {

    Exception caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
    Exception cause = caught;

    if (cause == null) {
      cause = exchange.getException();
    } else {
      /*
       Both sources are set. Exchange.EXCEPTION_CAUGHT is set by the error handler's own
       bookkeeping, which - under DeadLetterChannel redelivery - still holds the PREVIOUS
       attempt's exception at the moment the CURRENT attempt's SENT event fires; it only catches
       up by the next SENDING. exchange.getException() is fresh immediately, so it is the right
       source for a same-hop redelivery retry.

       But exchange.getException() is also fresh for an entirely unrelated, later failure on a
       DIFFERENT hop (e.g. a doTry/doCatch boundary), while EXCEPTION_CAUGHT may still be
       carrying a stale, already-reported exception left over from an earlier redelivery
       elsewhere in the same exchange - Camel never clears it once the retry loop concludes. Only
       prefer the fresh exception when this event is for the SAME endpoint that last reported an
       error, i.e. a genuine same-hop retry; otherwise keep EXCEPTION_CAUGHT, so an unrelated
       failure elsewhere is left for whichever event Camel actually attaches it to.
       */
      Object lastFailedEndpoint = exchange.getProperty(CAMEL_FAILED_EVENT_ENDPOINT);

      if (currentEndpointUri != null && currentEndpointUri.equals(lastFailedEndpoint)) {
        Exception thrown = exchange.getException();

        if (thrown != null) {
          cause = thrown;
        }
      }
    }

    String errorMessage = null;

    if (cause != null) {

      /*
      check if this is the first time we are tracing this error
      */
      Integer eventIdentityHashCode = System.identityHashCode(cause);

      Object previousEventIdentityHashCode = exchange
          .getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE);

      if (!eventIdentityHashCode.equals(previousEventIdentityHashCode)) {
        exchange.setProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE, eventIdentityHashCode);
        exchange.setProperty(CAMEL_FAILED_EVENT_ENDPOINT, currentEndpointUri);

        errorMessage = cause.getLocalizedMessage();
      }

    }
    return errorMessage;
  }

}
