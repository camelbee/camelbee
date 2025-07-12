package org.camelbee.security.routes.routes;

import static org.apache.camel.Exchange.CONTENT_TYPE;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.camelbee.security.routes.config.SecurityProperties;
import org.camelbee.security.routes.constant.Constants;
import org.camelbee.security.routes.exception.AuthenticationFailedException;
import org.camelbee.security.routes.exception.InvalidTokenException;
import org.camelbee.security.routes.exception.TokenExpiredException;
import org.camelbee.security.routes.exception.TokenValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel route for JWT validation.
 * Validates JWTs using JWKS and extracts roles and scopes based on configured claim paths.
 */
@ApplicationScoped
@RequiredArgsConstructor
@IfBuildProperty(name = "camelbee.security.enabled", stringValue = "true")
public class JwtValidationRoute extends RouteBuilder {

  private static final Logger log = LoggerFactory.getLogger(JwtValidationRoute.class);

  /**
   * Security configuration properties.
   */
  private final SecurityProperties securityProperties;

  /**
   * Configures the JWT validation route.
   * This route expects a JWT token in the Authorization header and validates it against
   * configured JWKS. After successful validation, it extracts roles and scopes from the
   * token claims and stores them in exchange properties for downstream processing.
   *
   * @throws Exception if route configuration fails
   */
  @Override
  public void configure() throws Exception {
    from("direct:validateJWT")
        .id("jwt-validation")
        .errorHandler(noErrorHandler())
        .setProperty(Constants.ORIGINAL_BODY, body())
        .setProperty(Constants.ORIGINAL_CONTENT_TYPE, header(CONTENT_TYPE))
        .setProperty(Constants.ORIGINAL_ACCEPT_CONTENT_TYPE, header("Accept"))
        .to("direct:fetchJWKS").id("fetchJWKSEndpoint")
        .process(exchange -> {
          String token = extractToken(exchange);
          JWKSet jwkSet = exchange.getProperty("jwkSet", JWKSet.class);
          if (jwkSet == null) {
            throw new AuthenticationFailedException("ERROR-AUTH002", "JWKS not available");
          }

          // Validate token using Nimbus
          ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
          JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
              JWSAlgorithm.parse(securityProperties.algorithm()),
              new ImmutableJWKSet<>(jwkSet)
          );
          jwtProcessor.setJWSKeySelector(keySelector);

          JWTClaimsSet claims;
          try {
            claims = jwtProcessor.process(token, null);
          } catch (Exception e) {
            throw new TokenValidationException("ERROR-AUTH007", "Token validation failed", e);
          }

          validateClaims(claims);

          // Store claims
          exchange.setProperty("jwt.claims", claims);
          exchange.setProperty("jwt.iss", claims.getIssuer());
          exchange.setProperty("jwt.sub", claims.getSubject());
          exchange.setProperty("jwt.exp", claims.getExpirationTime());
          exchange.setProperty("jwt.iat", claims.getIssueTime());
          exchange.setProperty("jwt.nbf", claims.getNotBeforeTime());
          exchange.setProperty("jwt.jti", claims.getJWTID());

          // Extract roles and scopes
          List<String> roles = extractRoles(claims);
          List<String> scopes = extractScopes(claims);

          exchange.setProperty("jwt.roles", roles);
          exchange.setProperty("jwt.scopes", scopes);
          exchange.setProperty("jwt.validated", true);

          log.debug("JWT validated successfully. Subject: {}, Roles: {}, Scopes: {}",
              claims.getSubject(), roles, scopes);

          // Optionally return a success flag
          exchange.getIn().setBody(true);
        })
        .setBody(exchangeProperty(Constants.ORIGINAL_BODY))
        .setHeader(CONTENT_TYPE, exchangeProperty(Constants.ORIGINAL_CONTENT_TYPE))
        .setHeader("Accept", exchangeProperty(Constants.ORIGINAL_ACCEPT_CONTENT_TYPE));
  }

  /**
   * Extracts the JWT token from the Authorization header.
   *
   * @param exchange the Camel exchange
   * @return the extracted JWT token
   * @throws InvalidTokenException if the Authorization header is missing or malformed
   */
  private String extractToken(Exchange exchange) {
    String token = exchange.getIn().getHeader("Authorization", String.class);
    if (token == null) {
      throw new InvalidTokenException("ERROR-AUTH001", "Authorization header is missing");
    }
    if (!token.startsWith("Bearer ")) {
      throw new InvalidTokenException("ERROR-AUTH001", "Authorization header is malformed");
    }
    return token.substring(7);
  }

  /**
   * Validates the JWT claims against configured expectations.
   *
   * @param claims the JWT claims set to validate
   * @throws TokenValidationException if the token fails validation
   * @throws TokenExpiredException    if the token has expired
   */
  private void validateClaims(JWTClaimsSet claims) {
    long now = System.currentTimeMillis();
    long skewMillis = securityProperties.clockSkew() * 1000L;

    // Validate issuer
    if (!securityProperties.issuer().equals(claims.getIssuer())) {
      throw new TokenValidationException("ERROR-AUTH003", "Invalid token issuer");
    }

    // Validate audience
    List<String> audience = claims.getAudience();
    if (audience == null || audience.isEmpty() || !securityProperties.audience().equals(audience.get(0))) {
      throw new TokenValidationException("ERROR-AUTH004", "Invalid token audience");
    }

    // Validate expiration with skew
    Date exp = claims.getExpirationTime();
    if (exp != null && now > (exp.getTime() + skewMillis)) {
      throw new TokenExpiredException("ERROR-AUTH005", "Token has expired");
    }

    // Validate not-before with skew
    Date nbf = claims.getNotBeforeTime();
    if (nbf != null && now < (nbf.getTime() - skewMillis)) {
      throw new TokenValidationException("ERROR-AUTH006", "Token not yet valid");
    }
  }

  /**
   * Extracts role information from JWT claims based on configured claim paths.
   *
   * @param claims the JWT claims set
   * @return list of extracted roles
   */
  private List<String> extractRoles(JWTClaimsSet claims) {
    Map<String, Object> allClaims = claims.getClaims();
    Set<String> roles = new HashSet<>();

    log.debug("Extracting roles from claims with paths: {}", securityProperties.roleClaims());

    for (String path : securityProperties.roleClaims()) {
      Object value = getNestedClaimValue(allClaims, path);

      if (value != null) {
        log.debug("Found value for role path '{}': {}", path, value);

        if (value instanceof List<?>) {
          for (Object item : (List<?>) value) {
            roles.add(String.valueOf(item));
          }
        } else if (value instanceof String) {
          roles.addAll(Arrays.asList(((String) value).split(" ")));
        } else if (value instanceof Map<?, ?> map) {
          Object nested = map.get("roles");
          if (nested instanceof List<?>) {
            for (Object r : (List<?>) nested) {
              roles.add(String.valueOf(r));
            }
          }
        }
      }
    }

    log.debug("Extracted roles: {}", roles);
    return new ArrayList<>(roles);
  }

  /**
   * Extracts scope information from JWT claims based on configured claim paths.
   *
   * @param claims the JWT claims set
   * @return list of extracted scopes
   */
  private List<String> extractScopes(JWTClaimsSet claims) {
    Map<String, Object> allClaims = claims.getClaims();
    Set<String> scopes = new HashSet<>();

    log.debug("Extracting scopes from claims with paths: {}", securityProperties.scopeClaims());

    for (String path : securityProperties.scopeClaims()) {
      Object value = getNestedClaimValue(allClaims, path);

      if (value != null) {
        log.debug("Found value for scope path '{}': {}", path, value);

        if (value instanceof String str) {
          scopes.addAll(Arrays.asList(str.split(" ")));
        } else if (value instanceof List<?>) {
          for (Object item : (List<?>) value) {
            scopes.add(String.valueOf(item));
          }
        }
      }
    }

    log.debug("Extracted scopes: {}", scopes);
    return new ArrayList<>(scopes);
  }

  /**
   * Retrieves a nested value from a claims map using a dotted path notation.
   *
   * @param claims the claims map
   * @param path   the path to the nested value using dot notation (e.g., "realm_access.roles")
   * @return the retrieved value, or null if not found
   */
  private Object getNestedClaimValue(Map<String, Object> claims, String path) {
    String[] parts = path.split("\\.");
    Object current = claims;

    for (String part : parts) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(part);
      if (current == null) {
        return null;
      }
    }

    return current;
  }
}
