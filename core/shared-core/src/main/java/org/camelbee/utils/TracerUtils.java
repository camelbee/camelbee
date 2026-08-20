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
   * @param exchange The exchange.
   * @return The error message.
   */
  public static String handleError(Exchange exchange) {

    /*
     Two sources, and which one is authoritative comes down to what each actually means.

     exchange.getException() is the exchange's CURRENT failure state: non-null exactly while a
     failure is in flight and not yet handled. It is always fresh - under DeadLetterChannel
     redelivery it already holds the current attempt's exception when that attempt's SENT event
     fires, and it holds a newly thrown exception the moment a hop fails.

     Exchange.EXCEPTION_CAUGHT is the error handler's bookkeeping. It lags (mid-redelivery it
     still names the PREVIOUS attempt until the next SENDING) and, more importantly, Camel never
     clears it once a retry loop concludes - so it can still be naming a long-finished, already
     reported failure while a completely different hop fails later in the same exchange.

     So the live exception wins whenever there is one, and EXCEPTION_CAUGHT is the fallback for
     the case it exists to cover: the error handler has handled the failure and cleared
     getException(), leaving only its own record of what happened. Preferring EXCEPTION_CAUGHT
     instead would attribute a fresh failure to whatever failed earlier - and, because the
     identity-based dedup below has already reported that earlier exception, would silently drop
     the new one, leaving the hop that actually threw looking successful and pinning the error on
     the later hop that merely caught it.
     */
    Exception thrown = exchange.getException();
    Exception cause = thrown != null
        ? thrown
        : exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);

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

        errorMessage = cause.getLocalizedMessage();
      }

    }
    return errorMessage;
  }

}
