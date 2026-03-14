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
   * handleError in response tracers.
   *
   * @param exchange The exchange.
   * @return The error message.
   */
  public static String handleError(Exchange exchange) {

    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);

    if (cause == null) {
      cause = exchange.getException();
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

        errorMessage = cause.getLocalizedMessage();
      }

    }
    return errorMessage;
  }

}
