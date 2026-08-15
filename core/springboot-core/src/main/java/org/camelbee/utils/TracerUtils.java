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

import static org.camelbee.constants.CamelBeeConstants.CAMELBEE_NODE_ID;
import static org.camelbee.constants.CamelBeeConstants.CAMEL_FAILED_EVENT_ENDPOINT;
import static org.camelbee.constants.CamelBeeConstants.CAMEL_FAILED_EVENT_IDENTITY_HASHCODE;

import org.apache.camel.Exchange;

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
