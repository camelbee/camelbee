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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.constants.CamelBeeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracerUtilsTest {

  @Mock
  private Exchange exchange;

  @Test
  void handleErrorShouldReturnNullWhenNoException() {
    // Arrange
    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(null);
    when(exchange.getException()).thenReturn(null);

    // Act
    String result = TracerUtils.handleError(exchange);

    // Assert
    assertNull(result);
  }

  @Test
  void handleErrorShouldReturnMessageForNewException() {
    // Arrange: the error handler has handled the failure and cleared exchange.getException(),
    // leaving only its own EXCEPTION_CAUGHT record - the case that property exists to cover.
    Exception testException = new RuntimeException("Test error message");

    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(testException);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE)).thenReturn(null);

    // Act
    String result = TracerUtils.handleError(exchange);

    // Assert
    assertEquals("Test error message", result);
    verify(exchange).setProperty(eq(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE), anyInt());
  }

  @Test
  void handleErrorShouldReturnNullForPreviouslyTracedException() {
    // Arrange
    Exception testException = new RuntimeException("Test error message");
    int exceptionHashCode = System.identityHashCode(testException);

    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(testException);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE)).thenReturn(exceptionHashCode);

    // Act
    String result = TracerUtils.handleError(exchange);

    // Assert
    assertNull(result);
  }

  @Test
  void handleErrorPrefersTheLiveExceptionOverTheErrorHandlersRecord() {
    // Arrange: a failure is in flight (getException() non-null). That is the exchange's current
    // state, so it wins outright - EXCEPTION_CAUGHT is not consulted at all, which is why it is
    // stubbed leniently here rather than being read.
    Exception live = new RuntimeException("Test error message");
    Exception handlerRecord = new RuntimeException("older, already handled");

    lenient().when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
        .thenReturn(handlerRecord);
    when(exchange.getException()).thenReturn(live);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE)).thenReturn(null);

    // Act
    String result = TracerUtils.handleError(exchange);

    // Assert
    assertEquals("Test error message", result);
    verify(exchange).setProperty(eq(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE), anyInt());
  }

  @Test
  void handleErrorPrefersFreshExceptionOnSameHopRedeliveryRetry() {
    // Arrange: mid-DeadLetterChannel-redelivery, EXCEPTION_CAUGHT still names the previous
    // (already-reported) attempt while getException() has already moved on to the current one.
    // Reporting the lagging record would dedup against its own earlier report and lose this
    // attempt entirely, so the live exception has to win.
    Exception previousAttempt = new RuntimeException("attempt 1");
    Exception currentAttempt = new RuntimeException("attempt 2");

    lenient().when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
        .thenReturn(previousAttempt);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE))
        .thenReturn(System.identityHashCode(previousAttempt));
    when(exchange.getException()).thenReturn(currentAttempt);

    // Act
    String result = TracerUtils.handleError(exchange);

    // Assert
    assertEquals("attempt 2", result);
  }

  @Test
  void handleErrorReportsAFreshFailureOnADifferentHopRatherThanAStaleCaughtOne() {
    // Arrange: an earlier failure elsewhere in this exchange (a redelivery that has since
    // concluded) left EXCEPTION_CAUGHT set - Camel never clears it - and it has already been
    // reported. Now a DIFFERENT hop throws, so getException() is freshly non-null.
    //
    // The stale record must not win here. It would dedup against its own earlier report and
    // return null, leaving the hop that actually threw looking successful; the exception would
    // then surface one hop later, on whichever boundary catches it. That is precisely what made
    // direct:boom render as a success while direct:invokeAlwaysFails - which merely caught it -
    // was flagged as the failure.
    Exception stale = new RuntimeException("earlier failure");
    Exception freshUnrelated = new RuntimeException("boom");

    lenient().when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
        .thenReturn(stale);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE))
        .thenReturn(System.identityHashCode(stale));
    when(exchange.getException()).thenReturn(freshUnrelated);

    // Act
    String result = TracerUtils.handleError(exchange);

    // Assert: reported against direct:boom, the hop that actually threw
    assertEquals("boom", result);
  }

  @Test
  void resolveNodeId_prefersCamelsOwnHistoryNodeId() {
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    exchange.setProperty(CamelBeeConstants.CAMELBEE_NODE_ID, "stamped");

    assertEquals("camelNode", TracerUtils.resolveNodeId(exchange, "camelNode"));
  }

  @Test
  void resolveNodeId_fallsBackToTheStampedNodeId() {
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    exchange.setProperty(CamelBeeConstants.CAMELBEE_NODE_ID, "stamped");

    assertEquals("stamped", TracerUtils.resolveNodeId(exchange, null));
  }

  @Test
  void resolveNodeId_isNullWhenNeitherSourceHasOne() {
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    assertNull(TracerUtils.resolveNodeId(exchange, null));
  }
}
