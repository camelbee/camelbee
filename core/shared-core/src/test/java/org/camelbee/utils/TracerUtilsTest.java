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

import static org.camelbee.constants.CamelBeeConstants.CAMEL_FAILED_EVENT_ENDPOINT;
import static org.camelbee.constants.CamelBeeConstants.CAMEL_FAILED_EVENT_IDENTITY_HASHCODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
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
    String result = TracerUtils.handleError(exchange, "direct:test");

    // Assert
    assertNull(result);
  }

  @Test
  void handleErrorShouldReturnMessageForNewException() {
    // Arrange
    Exception testException = new RuntimeException("Test error message");

    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(testException);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE)).thenReturn(null);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_ENDPOINT)).thenReturn(null);

    // Act
    String result = TracerUtils.handleError(exchange, "direct:test");

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
    when(exchange.getProperty(CAMEL_FAILED_EVENT_ENDPOINT)).thenReturn(null);

    // Act
    String result = TracerUtils.handleError(exchange, "direct:test");

    // Assert
    assertNull(result);
  }

  @Test
  void handleErrorShouldCheckExchangeExceptionWhenExceptionCaughtIsNull() {
    // Arrange
    Exception testException = new RuntimeException("Test error message");

    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(null);
    when(exchange.getException()).thenReturn(testException);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE)).thenReturn(null);

    // Act
    String result = TracerUtils.handleError(exchange, "direct:test");

    // Assert
    assertEquals("Test error message", result);
    verify(exchange).setProperty(eq(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE), anyInt());
  }

  @Test
  void handleErrorPrefersFreshExceptionOnSameHopRedeliveryRetry() {
    // Arrange: EXCEPTION_CAUGHT still holds the previous attempt's (already-reported) exception,
    // as it does mid-DeadLetterChannel-redelivery, but this SENT is for the same endpoint that
    // reported it.
    Exception previousAttempt = new RuntimeException("attempt 1");
    Exception currentAttempt = new RuntimeException("attempt 2");

    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(previousAttempt);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE))
        .thenReturn(System.identityHashCode(previousAttempt));
    when(exchange.getProperty(CAMEL_FAILED_EVENT_ENDPOINT)).thenReturn("direct:flakyTarget");
    when(exchange.getException()).thenReturn(currentAttempt);

    // Act
    String result = TracerUtils.handleError(exchange, "direct:flakyTarget");

    // Assert
    assertEquals("attempt 2", result);
  }

  @Test
  void handleErrorKeepsExceptionCaughtForAnUnrelatedFailureOnADifferentHop() {
    // Arrange: EXCEPTION_CAUGHT is stale/already-reported from an earlier failure on a DIFFERENT
    // endpoint (e.g. a redelivery elsewhere in the same exchange), and exchange.getException() is
    // fresh for THIS hop - but this is not a same-hop retry, so EXCEPTION_CAUGHT should still win,
    // deferring to whichever event Camel actually attaches the caught exception to.
    Exception stale = new RuntimeException("earlier failure");
    Exception freshUnrelated = new RuntimeException("boom");

    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(stale);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE))
        .thenReturn(System.identityHashCode(stale));
    when(exchange.getProperty(CAMEL_FAILED_EVENT_ENDPOINT)).thenReturn("direct:flakyTarget");

    // Act
    String result = TracerUtils.handleError(exchange, "direct:boom");

    // Assert
    assertNull(result);
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
