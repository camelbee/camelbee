package org.camelbee.logging;

import static org.camelbee.logging.LoggingAttribute.ENDPOINT;
import static org.camelbee.logging.LoggingAttribute.EXCEPTION;
import static org.camelbee.logging.LoggingAttribute.EXCHANGE_EVENT_TYPE;
import static org.camelbee.logging.LoggingAttribute.EXCHANGE_ID;
import static org.camelbee.logging.LoggingAttribute.HEADERS;
import static org.camelbee.logging.LoggingAttribute.MESSAGE_BODY;
import static org.camelbee.logging.LoggingAttribute.MESSAGE_TYPE;
import static org.camelbee.logging.LoggingAttribute.ROUTE_ID;
import static org.camelbee.logging.LoggingAttribute.SIZE;
import static org.camelbee.logging.LoggingAttribute.TIMESTAMP;

import org.camelbee.debugger.model.exchange.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for structured logging of message events with MDC context management.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
public class LoggingService {

  private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);

  private static final LoggingAttribute[] MDC_ATTRIBUTES = {
      EXCHANGE_ID,
      EXCHANGE_EVENT_TYPE,
      MESSAGE_BODY,
      HEADERS,
      ROUTE_ID,
      ENDPOINT,
      MESSAGE_TYPE,
      EXCEPTION,
      TIMESTAMP,
      SIZE
  };

  /**
   * Logs a message with MDC context information.
   *
   * @param message    The message to be logged
   * @param logMessage Custom log message (optional)
   * @param clearMdc   Whether to clear MDC context after logging
   * @throws IllegalArgumentException if message is null
   */
  public void logMessage(Message message, String logMessage, boolean clearMdc) {

    if (message == null || message.getEndpoint() == null || message.getEndpoint().startsWith("direct:")) {
      return;
    }

    try {
      setMdcContext(message);
      logger.info(determineLogMessage(logMessage));
    } catch (Exception e) {
      handleLoggingError(message, e);
    } finally {
      if (clearMdc) {
        clearMdcContext();
      }
    }
  }

  private void setMdcContext(Message message) {
    MdcContext.set(EXCHANGE_ID, message.getExchangeId());
    MdcContext.set(EXCHANGE_EVENT_TYPE, message.getExchangeEventType());
    if (logger.isDebugEnabled()) {
      MdcContext.set(MESSAGE_BODY, message.getMessageBody());
      MdcContext.set(HEADERS, message.getHeaders());
      if (message.getMessageBody() != null) {
        MdcContext.set(SIZE, message.getMessageBody().length());
      }
    }
    MdcContext.set(ROUTE_ID, message.getRouteId());
    MdcContext.set(ENDPOINT, message.getEndpoint());
    MdcContext.set(MESSAGE_TYPE, message.getMessageType().toString());
    MdcContext.set(EXCEPTION, message.getException());
    MdcContext.set(TIMESTAMP, message.getTimeStamp());
  }

  private String determineLogMessage(String logMessage) {
    return logMessage != null ? logMessage : "Message processed";
  }

  private void handleLoggingError(Message message, Exception e) {
    logger.error("Failed to log message. Message: {}, Error: {}",
        message, e.getMessage(), e);
  }

  private void clearMdcContext() {
    MdcContext.clear(MDC_ATTRIBUTES);
  }
}
