package org.camelbee.security.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.camelbee.security.routes.cache.JwksCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@TestProfile(FetchJwksRouteTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FetchJwksRouteTest extends CamelQuarkusTestSupport {

  @Produce("direct:fetchJWKS")
  protected ProducerTemplate producerTemplate;

  @EndpointInject("mock:jwks")
  protected MockEndpoint mockJwksEndpoint;

  @Inject
  private JwksCache jwksCache;

  @Inject
  private CamelContext camelContext;

  private Exchange exchange;
  private RSAKey rsaKey;
  private String jwksResponse;

  private boolean isSetUp = false;

  @BeforeAll
  public void setUp() throws Exception {

    if (!isSetUp) {

      rsaKey = new RSAKeyGenerator(2048)
          .keyID("123")
          .generate();

      JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
      jwksResponse = jwkSet.toString();

      exchange = ExchangeBuilder.anExchange(camelContext).build();

      mockJwksEndpoint.whenAnyExchangeReceived(
          e -> {
            e.getIn().setBody(jwksResponse, String.class);
          }
      );

      AdviceWith.adviceWith(camelContext, "jwks-retrieval",
          advice -> {
            advice.weaveById("invokeJwksUrlEnpoint")
                .replace()
                .to("mock:jwks");
          });

      isSetUp = true;
    }

  }

  @Test
  @Order(1)
  void testSuccessfulJwksFetch() throws Exception {

    mockJwksEndpoint.expectedMessageCount(1);
    producerTemplate.send(exchange);
    mockJwksEndpoint.assertIsSatisfied();

    JWKSet cachedJwks = jwksCache.getCurrentJwkSet();
    assertThat(cachedJwks).isNotNull();
    assertThat(cachedJwks.getKeys()).hasSize(1);
    assertThat(cachedJwks.getKeyByKeyId("123")).isNotNull();
  }

  @Test
  @Order(2)
  void testCacheReuse() throws Exception {
    mockJwksEndpoint.reset();
    // The JWKS cache was populated by testSuccessfulJwksFetch()
    mockJwksEndpoint.expectedMessageCount(0);
    producerTemplate.send(exchange);
    mockJwksEndpoint.assertIsSatisfied();
  }

  @Test
  @Order(3)
  void testCacheExpiry() throws Exception {

    Thread.sleep(3000); // Wait just over cache duration
    mockJwksEndpoint.reset();
    mockJwksEndpoint.expectedMessageCount(1);
    producerTemplate.send(exchange);
    mockJwksEndpoint.assertIsSatisfied();
  }

  @Override
  public String getConfigProfile() {
    return "test";
  }

}
