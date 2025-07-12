package org.camelbee.security.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.camelbee.security.routes.cache.JwksCache;
import org.camelbee.security.routes.config.SecurityProperties;
import org.camelbee.security.routes.routes.FetchJwksRoute;
import org.camelbee.security.routes.routes.JwtValidationRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CamelSpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@SpringBootTest(
    properties = {
        "camelbee.security.enabled=true",
        "camelbee.security.jwks-url=http://test-auth-server/.well-known/jwks.json",
        "camelbee.security.issuer=test-issuer",
        "camelbee.security.audience=test-audience",
        "camelbee.security.jwks-cache-duration=2000",
        "camelbee.security.role-claims=roles,resource_access.account.roles",
        "camelbee.security.scope-claims=scope,scp,scopes",
        "camelbee.security.clock-skew=30"
    },
    classes = {
        JwksCache.class,
        FetchJwksRoute.class,
        JwtValidationRoute.class,
        SecurityProperties.class
    }
)
@UseAdviceWith
class FetchJwksRouteTest {

  @Produce("direct:fetchJWKS")
  protected ProducerTemplate producerTemplate;

  @EndpointInject("mock:jwks")
  protected MockEndpoint mockJwksEndpoint;

  @Autowired
  private JwksCache jwksCache;

  @Autowired
  private CamelContext camelContext;

  private Exchange exchange;
  private RSAKey rsaKey;
  private String jwksResponse;

  private static boolean isSetUp = false;

  @BeforeEach
  void setUp() throws Exception {

    rsaKey = new RSAKeyGenerator(2048)
        .keyID("123")
        .generate();

    JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
    jwksResponse = jwkSet.toString();

    if (!isSetUp) {
      AdviceWith.adviceWith(camelContext, "jwks-retrieval",
          advice -> {
            advice.weaveById("invokeJwksUrlEnpoint")
                .replace()
                .to("mock:jwks");
          });
      camelContext.start();

      isSetUp = true;
    }

    exchange = ExchangeBuilder.anExchange(camelContext).build();

    mockJwksEndpoint.whenAnyExchangeReceived(
        e -> e.getIn().setBody(jwksResponse, String.class)
    );

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
}
