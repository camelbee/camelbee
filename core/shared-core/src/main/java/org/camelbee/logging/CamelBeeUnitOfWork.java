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

package org.camelbee.logging;

import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.impl.engine.MDCUnitOfWork;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.logging.model.RequestId;
import org.camelbee.logging.model.TransactionId;
import org.camelbee.utils.UuidResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialized UnitOfWork for Camel exchanges that handles request and transaction IDs.
 * Manages the lifecycle of these IDs in the MDC context and exchange headers.
 */
public class CamelBeeUnitOfWork extends MDCUnitOfWork {

  private static final Logger logger = LoggerFactory.getLogger(CamelBeeUnitOfWork.class);

  /**
   * Creates a new CamelBeeUnitOfWork for the given exchange.
   * Initializes request and transaction IDs if this is the primary route.
   *
   * @param exchange the Camel exchange
   */
  public CamelBeeUnitOfWork(Exchange exchange) {
    super(exchange, exchange.getContext().getInflightRepository(), "", false, false);

    if (shouldSkipProcessing(exchange)) {
      return;
    }

    initializeIds(exchange);
  }

  private boolean shouldSkipProcessing(Exchange exchange) {
    // Skip for sub-messages (enricher, multicast, etc.)
    if (exchange.getProperty(ExchangePropertyKey.CORRELATION_ID) != null) {
      logger.trace("Skipping ID processing for sub-message");
      return true;
    }

    // Skip if already processed
    if (exchange.getProperty(CamelBeeConstants.MDC_UNITOFWORK_EXECUTED) != null) {
      logger.trace("Skipping ID processing - already executed");
      return true;
    }

    exchange.setProperty(CamelBeeConstants.MDC_UNITOFWORK_EXECUTED, "executed");
    return false;
  }

  private void initializeIds(Exchange exchange) {
    clearContext();
    initializeRequestId(exchange);
    initializeTransactionId(exchange);
  }

  private void clearContext() {
    RequestId.remove();
    TransactionId.remove();
    MdcContext.clearAll();
  }

  private void initializeRequestId(Exchange exchange) {
    UUID requestId = UUID.randomUUID();
    RequestId.set(requestId);
    MdcContext.set(LoggingAttribute.REQUEST_ID, requestId.toString());
    exchange.getMessage().setHeader(LoggingAttribute.REQUEST_ID.getAttributeName(), requestId.toString());
    logger.trace("Initialized request ID: {}", requestId);
  }

  private void initializeTransactionId(Exchange exchange) {
    String existingTxId = exchange.getMessage().getHeader(
        LoggingAttribute.TRANSACTION_ID.getAttributeName(),
        String.class
    );

    UUID transactionId = existingTxId != null
        ? UuidResolver.resolveOrGenerate(existingTxId)
        : UUID.randomUUID();

    TransactionId.set(transactionId);
    MdcContext.set(LoggingAttribute.TRANSACTION_ID, transactionId.toString());

    if (existingTxId == null) {
      exchange.getMessage().setHeader(
          LoggingAttribute.TRANSACTION_ID.getAttributeName(),
          transactionId.toString()
      );
    }

    logger.trace("Initialized transaction ID: {}", transactionId);
  }
}