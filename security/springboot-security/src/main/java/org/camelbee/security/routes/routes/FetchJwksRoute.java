package org.camelbee.security.routes.routes;

import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.camelbee.security.routes.cache.JwksCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Camel route for fetching and caching JSON Web Key Sets (JWKS).
 * This route manages the retrieval of JWKS from a configured endpoint and handles caching
 * to optimize performance and reduce external calls.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "camelbee.security.enabled", havingValue = "true")
public class FetchJwksRoute extends RouteBuilder {

  /**
   * Cache service for storing and managing JWKS.
   */
  private final JwksCache jwksCache;

  /**
   * Configures the JWKS fetching route.
   * The route performs the following steps:
   *
   * @throws Exception if route configuration fails
   */
  @Override
  public void configure() throws Exception {
    // JWKS retrieval route
    from("direct:fetchJWKS")
        .id("jwks-retrieval")
        .errorHandler(noErrorHandler())
        .choice()
        .when(this::shouldRefreshJwks)
        .removeHeader(Exchange.HTTP_PATH)
        .removeHeader(Exchange.HTTP_URL)
        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
        .to("http://{{camelbee.security.jwks-url}}?bridgeEndpoint=true").id("invokeJwksUrlEndpoint")
        .process(exchange -> {
          String jwksJson = exchange.getIn().getBody(String.class);
          JWKSet jwkSet = JWKSet.parse(jwksJson);
          jwksCache.updateCache(jwkSet);
          exchange.setProperty("jwkSet", jwkSet);
        })
        .endChoice()
        .end();
  }

  /**
   * Determines if JWKS should be refreshed based on cache state.
   * If valid cache exists, sets the cached JWKS in the exchange property.
   *
   * @param exchange The Camel exchange
   * @return true if JWKS should be refreshed, false otherwise
   */
  private boolean shouldRefreshJwks(final Exchange exchange) {
    if (jwksCache.hasValidCache()) {
      exchange.setProperty("jwkSet", jwksCache.getCurrentJwkSet());
    }
    return jwksCache.shouldRefresh();
  }
}
