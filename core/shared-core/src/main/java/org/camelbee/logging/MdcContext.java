package org.camelbee.logging;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

/**
 * Utility class for managing the Mapped Diagnostic Context (MDC) for structured logging.
 * Provides type-safe methods to set, get, and clear logging attributes in the MDC context.
 */
public final class MdcContext {

  private MdcContext() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Sets a logging attribute to an enum value.
   *
   * @param attribute the logging attribute to set
   * @param value     the enum value
   * @return the logging attribute for method chaining
   */
  public static LoggingAttribute set(LoggingAttribute attribute, Enum<?> value) {
    requireNonNull(attribute, "attribute cannot be null");
    requireNonNull(value, "value cannot be null");
    return set(attribute, value.toString());
  }

  /**
   * Sets a logging attribute to an integer value.
   *
   * @param attribute the logging attribute to set
   * @param value     the integer value
   * @return the logging attribute for method chaining
   */
  public static LoggingAttribute set(LoggingAttribute attribute, int value) {
    requireNonNull(attribute, "attribute cannot be null");
    return set(attribute, Integer.toString(value));
  }

  /**
   * Sets a logging attribute to a string value.
   *
   * @param attribute the logging attribute to set
   * @param value     the string value
   * @return the logging attribute for method chaining
   */
  public static LoggingAttribute set(LoggingAttribute attribute, String value) {
    requireNonNull(attribute, "attribute cannot be null");

    if (isBlank(value)) {
      clear(attribute);
      return attribute;
    }
    MDC.put(attribute.getAttributeName(), value);
    return attribute;
  }

  /**
   * Sets a logging attribute to a collection of strings.
   *
   * @param attribute the logging attribute to set
   * @param values    the collection of strings
   * @return the logging attribute for method chaining
   */
  public static LoggingAttribute set(LoggingAttribute attribute, Collection<String> values) {
    requireNonNull(attribute, "attribute cannot be null");

    if (values == null || values.isEmpty()) {
      return set(attribute, (String) null);
    }

    String value = values.stream()
        .map(s -> String.format("'%s'", s))
        .collect(Collectors.joining(", ", "[", "]"));
    return set(attribute, value);
  }

  /**
   * Gets the value of a logging attribute.
   *
   * @param attribute the logging attribute to get
   * @return the value, or null if not set
   */
  public static String get(LoggingAttribute attribute) {
    requireNonNull(attribute, "attribute cannot be null");
    return getOrDefault(attribute, null);
  }

  /**
   * Gets the value of a logging attribute with a default fallback.
   *
   * @param attribute    the logging attribute to get
   * @param defaultValue the default value if attribute is not set
   * @return the value, or defaultValue if not set
   */
  public static String getOrDefault(LoggingAttribute attribute, String defaultValue) {
    requireNonNull(attribute, "attribute cannot be null");
    return StringUtils.defaultIfEmpty(MDC.get(attribute.getAttributeName()), defaultValue);
  }

  /**
   * Removes a specific logging attribute.
   *
   * @param attribute the logging attribute to remove
   */
  public static void clear(LoggingAttribute attribute) {
    requireNonNull(attribute, "attribute cannot be null");
    MDC.remove(attribute.getAttributeName());
  }

  /**
   * Removes multiple logging attributes.
   *
   * @param attributes the logging attributes to remove
   */
  public static void clear(LoggingAttribute... attributes) {
    requireNonNull(attributes, "attributes cannot be null");
    for (LoggingAttribute attribute : attributes) {
      requireNonNull(attribute, "attribute in varargs cannot be null");
      MDC.remove(attribute.getAttributeName());
    }
  }

  /**
   * Removes all logging attributes from the MDC context.
   */
  public static void clearAll() {
    MDC.clear();
  }

  /**
   * Gets an immutable copy of the current MDC context map.
   *
   * @return a copy of the current context map
   */
  public static Map<String, String> getContextSnapshot() {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return contextMap != null ? Map.copyOf(contextMap) : Map.of();
  }
}