package org.camelbee.security.routes.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Configuration mapping for JWT security settings in Quarkus.
 * This interface centralizes all security-related configuration for the JWT validation process
 * using Quarkus' type-safe configuration mapping approach.
 */
@ConfigMapping(prefix = "camelbee.security")
@ApplicationScoped
public interface SecurityProperties {

  /**
   * Flag to enable or disable security features.
   */
  @WithDefault("true")
  boolean enabled();

  /**
   * The expected issuer of the JWT token. Must be configured explicitly.
   */
  String issuer();

  /**
   * The expected audience of the JWT token. Must be configured explicitly.
   */
  String audience();

  /**
   * The URL to fetch JWKS (JSON Web Key Set) from.
   */
  String jwksUrl();

  /**
   * JWKS cache duration in milliseconds.
   */
  @WithName("jwks-cache-duration")
  @WithDefault("2000")
  int jwksCacheDuration();

  /**
   * List of JWT claim paths to extract role information.
   */
  @WithName("role-claims")
  List<String> roleClaims();

  /**
   * List of JWT claim paths to extract scope information.
   */
  @WithName("scope-claims")
  List<String> scopeClaims();

  /**
   * The cryptographic algorithm used for JWT signature verification.
   */
  @WithDefault("RS256")
  String algorithm();

  /**
   * Clock skew tolerance in seconds for token time validation.
   */
  @WithName("clock-skew")
  @WithDefault("30")
  int clockSkew();
}