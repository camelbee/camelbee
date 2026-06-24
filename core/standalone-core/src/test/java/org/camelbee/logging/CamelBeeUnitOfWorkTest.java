package org.camelbee.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.logging.model.RequestId;
import org.camelbee.logging.model.TransactionId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CamelBeeUnitOfWorkTest {

  private DefaultCamelContext camelContext;

  @BeforeAll
  void start() {
    camelContext = new DefaultCamelContext();
    camelContext.start();
  }

  @AfterAll
  void stop() {
    camelContext.stop();
  }

  @AfterEach
  void clearContext() {
    MdcContext.clearAll();
    RequestId.remove();
    TransactionId.remove();
  }

  private Exchange exchange() {
    return new DefaultExchange(camelContext);
  }

  private String txHeader() {
    return LoggingAttribute.TRANSACTION_ID.getAttributeName();
  }

  private String reqHeader() {
    return LoggingAttribute.REQUEST_ID.getAttributeName();
  }

  @Test
  void primaryRoute_initializesRequestAndTransactionIds() {
    Exchange exchange = exchange();

    new CamelBeeUnitOfWork(exchange);

    String requestId = exchange.getMessage().getHeader(reqHeader(), String.class);
    String transactionId = exchange.getMessage().getHeader(txHeader(), String.class);

    assertThat(requestId).isNotBlank();
    assertThat(transactionId).isNotBlank();
    assertThat(RequestId.get()).isEqualTo(requestId);
    assertThat(TransactionId.get()).isEqualTo(transactionId);
    assertThat(MdcContext.get(LoggingAttribute.REQUEST_ID)).isEqualTo(requestId);
    assertThat(MdcContext.get(LoggingAttribute.TRANSACTION_ID)).isEqualTo(transactionId);
    assertThat(exchange.getProperty(CamelBeeConstants.MDC_UNITOFWORK_EXECUTED)).isEqualTo("executed");
  }

  @Test
  void existingTransactionIdHeader_isPreserved() {
    Exchange exchange = exchange();
    UUID existing = UUID.randomUUID();
    exchange.getMessage().setHeader(txHeader(), existing.toString());

    new CamelBeeUnitOfWork(exchange);

    assertThat(exchange.getMessage().getHeader(txHeader(), String.class))
        .isEqualTo(existing.toString());
    assertThat(TransactionId.get()).isEqualTo(existing.toString());
  }

  @Test
  void subMessageWithCorrelationId_isSkipped() {
    Exchange exchange = exchange();
    exchange.setProperty(ExchangePropertyKey.CORRELATION_ID, "corr-1");

    new CamelBeeUnitOfWork(exchange);

    // Skipped: no request id header assigned and the executed marker is not set.
    assertThat(exchange.getMessage().getHeader(reqHeader(), String.class)).isNull();
    assertThat(exchange.getProperty(CamelBeeConstants.MDC_UNITOFWORK_EXECUTED)).isNull();
  }

  @Test
  void alreadyExecutedExchange_isSkipped() {
    Exchange exchange = exchange();
    exchange.setProperty(CamelBeeConstants.MDC_UNITOFWORK_EXECUTED, "executed");

    new CamelBeeUnitOfWork(exchange);

    assertThat(exchange.getMessage().getHeader(reqHeader(), String.class)).isNull();
  }
}
