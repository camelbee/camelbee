package org.camelbee.security.routes.cache;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A component that manages caching of JSON Web Key Sets (JWKS).
 * This cache implements a time-based invalidation strategy and provides
 * methods to manage and query the JWKS cache state.
 */
@Component
@ConditionalOnProperty(value = "camelbee.security.enabled", havingValue = "true")
public class JwksCache {

  /** Cache to store JWKSet with their identifiers. */
  private static final ConcurrentHashMap<String, JWKSet> jwksCache = new ConcurrentHashMap<>();

  /** Cache duration in milliseconds. */
  @Value("${camelbee.security.jwks-cache-duration:3600000}")
  private long cacheDuration;

  /** Timestamp of the last cache update. */
  private long lastFetchTime = 0;

  /**
   * Updates the cache with a new JWKSet.
   * This method stores the provided JWKSet in the cache and updates the last fetch timestamp.
   *
   * @param jwkSet The JWKSet to be cached
   */
  public void updateCache(JWKSet jwkSet) {
    jwksCache.put("current", jwkSet);
    lastFetchTime = System.currentTimeMillis();
  }

  /**
   * Retrieves the current JWKSet from the cache.
   *
   * @return The currently cached JWKSet, or null if no JWKSet is cached
   */
  public JWKSet getCurrentJwkSet() {
    return jwksCache.get("current");
  }

  /**
   * Determines whether the cache needs to be refreshed based on cache emptiness
   * and time elapsed since last fetch.
   *
   * @return true if the cache is empty or has expired, false otherwise
   */
  public boolean shouldRefresh() {
    return jwksCache.isEmpty() || System.currentTimeMillis() - lastFetchTime > cacheDuration;
  }

  /**
   * Checks if the cache contains a valid JWKSet.
   *
   * @return true if the cache contains a current JWKSet, false otherwise
   */
  public boolean hasValidCache() {
    return jwksCache.containsKey("current");
  }
}