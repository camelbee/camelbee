package org.camelbee.utils;

import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling UUID resolution and generation.
 * Provides methods to safely parse UUID strings and generate new UUIDs when needed.
 */
public final class UuidResolver {

  private static final Logger logger = LoggerFactory.getLogger(UuidResolver.class);

  private UuidResolver() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Resolves a UUID from a string value, generating a new UUID if the input is invalid.
   *
   * @param value the string to parse as UUID
   * @return a valid UUID, either parsed from input or newly generated
   */
  public static UUID resolveOrGenerate(String value) {
    return parseUuid(value)
        .orElseGet(() -> {
          UUID generated = UUID.randomUUID();
          logger.debug("Generated new UUID: {}", generated);
          return generated;
        });
  }

  /**
   * Attempts to parse a string into a UUID.
   *
   * @param value the string to parse
   * @return an Optional containing the parsed UUID, or empty if parsing failed
   */
  public static Optional<UUID> parseUuid(String value) {
    if (StringUtils.isBlank(value)) {
      logger.trace("Input value is blank, no UUID to parse");
      return Optional.empty();
    }

    try {
      UUID uuid = UUID.fromString(value);
      logger.trace("Successfully parsed UUID: {}", uuid);
      return Optional.of(uuid);
    } catch (IllegalArgumentException e) {
      logger.warn("Failed to parse UUID from value: {}", value);
      return Optional.empty();
    }
  }

  /**
   * Generates a new random UUID.
   *
   * @return a newly generated UUID
   */
  public static UUID generate() {
    UUID uuid = UUID.randomUUID();
    logger.trace("Generated new UUID: {}", uuid);
    return uuid;
  }
}