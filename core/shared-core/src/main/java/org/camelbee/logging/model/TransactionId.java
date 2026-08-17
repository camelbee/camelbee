package org.camelbee.logging.model;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local storage for transaction IDs.
 */
public final class TransactionId {

  private static final ThreadLocal<UUID> TRANSACTION_ID = new ThreadLocal<>();

  private TransactionId() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Sets the transaction ID for the current thread.
   *
   * @param transactionId the transaction ID to set
   */
  public static void set(UUID transactionId) {
    TRANSACTION_ID.set(transactionId);
  }

  /**
   * Gets the transaction ID for the current thread.
   *
   * @return the transaction ID as a string
   * @throws IllegalStateException if no transaction ID is set
   */
  public static String get() {
    return Optional.ofNullable(TRANSACTION_ID.get())
        .map(UUID::toString)
        .orElseThrow(() -> new IllegalStateException("No transaction ID set for current thread"));
  }

  /**
   * Removes the transaction ID from the current thread.
   */
  public static void remove() {
    TRANSACTION_ID.remove();
  }
}