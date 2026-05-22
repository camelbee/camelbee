package org.camelbee.logging;

import java.util.Arrays;
import java.util.Optional;

/**
 * Defines standard attributes for structured logging in MDC context.
 * These attributes represent exchange and message information in the Camel context.
 */
public enum LoggingAttribute {

  // Exchange Information
  REQUEST_ID("requestId"), TRANSACTION_ID("transactionId"), EXCHANGE_ID("exchangeId"), EXCHANGE_EVENT_TYPE("exchangeEventType"), MESSAGE_BODY(
      "messageBody"), HEADERS("headers"), ROUTE_ID("routeId"), ENDPOINT("endpoint"), ENDPOINT_ID("endpointId"), MESSAGE_TYPE("messageType"), EXCEPTION(
          "exception"), TIMESTAMP("timeStamp"), SIZE("size");

  private final String attributeName;

  /**
   * Creates a new logging attribute with the specified name.
   *
   * @param attributeName the name used in structured logs
   */
  LoggingAttribute(String attributeName) {
    this.attributeName = attributeName;
  }

  /**
   * Gets the attribute name as it appears in logs.
   *
   * @return the logging attribute name
   */
  public String getAttributeName() {
    return attributeName;
  }

  /**
   * Checks if the given key matches this attribute's name.
   *
   * @param key the key to check
   * @return true if the key matches this attribute's name (case-insensitive)
   */
  public boolean matches(String key) {
    return key != null && key.equalsIgnoreCase(attributeName);
  }

  /**
   * Finds a logging attribute by its name.
   *
   * @param name the attribute name to find
   * @return an Optional containing the matching attribute, or empty if not found
   */
  public static Optional<LoggingAttribute> findByName(String name) {
    return Optional.ofNullable(name)
        .flatMap(key -> Arrays.stream(values())
            .filter(attr -> attr.matches(key))
            .findFirst());
  }
}