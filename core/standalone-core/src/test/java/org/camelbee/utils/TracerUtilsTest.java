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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.camel.Exchange;
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
    // Arrange
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
  void handleErrorShouldCheckExchangeExceptionWhenExceptionCaughtIsNull() {
    // Arrange
    Exception testException = new RuntimeException("Test error message");

    when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).thenReturn(null);
    when(exchange.getException()).thenReturn(testException);
    when(exchange.getProperty(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE)).thenReturn(null);

    // Act
    String result = TracerUtils.handleError(exchange);

    // Assert
    assertEquals("Test error message", result);
    verify(exchange).setProperty(eq(CAMEL_FAILED_EVENT_IDENTITY_HASHCODE), anyInt());
  }
}
