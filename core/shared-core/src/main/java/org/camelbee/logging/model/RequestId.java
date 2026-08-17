package org.camelbee.logging.model;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local storage for request IDs.
 */
public final class RequestId {

  private static final ThreadLocal<UUID> REQUEST_ID = new ThreadLocal<>();

  private RequestId() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Sets the request ID for the current thread.
   *
   * @param requestId the request ID to set
   */
  public static void set(UUID requestId) {
    REQUEST_ID.set(requestId);
  }

  /**
   * Gets the request ID for the current thread.
   *
   * @return the request ID as a string
   * @throws IllegalStateException if no request ID is set
   */
  public static String get() {
    return Optional.ofNullable(REQUEST_ID.get())
        .map(UUID::toString)
        .orElseThrow(() -> new IllegalStateException("No request ID set for current thread"));
  }

  /**
   * Removes the request ID from the current thread.
   */
  public static void remove() {
    REQUEST_ID.remove();
  }
}
