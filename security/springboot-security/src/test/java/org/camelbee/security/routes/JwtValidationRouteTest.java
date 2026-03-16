package org.camelbee.security.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * Unit Tester For Camel Routes.
 */
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
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class JwtValidationRouteTest {

  @Produce("direct:validateJWT")
  protected ProducerTemplate producerTemplate;

  @EndpointInject("mock:result")
  protected MockEndpoint resultEndpoint;

  @EndpointInject("mock:fetchJWKS")
  protected MockEndpoint mockFetchJWKS;

  @Autowired
  private JwksCache jwksCache;

  @Autowired
  private CamelContext camelContext;

  private Exchange exchange;
  private Map<String, Object> headers;
  private RSAKey rsaKey;
  private String validToken;
  private JWKSet jwkSet;
  private static final String TEST_ISSUER = "test-issuer";
  private static final String TEST_AUDIENCE = "test-audience";

  @BeforeEach
  public void setUp() throws Exception {
    rsaKey = new RSAKeyGenerator(2048).keyID("123").generate();
    jwkSet = new JWKSet(rsaKey.toPublicJWK());
    jwksCache.updateCache(jwkSet);

    JWSSigner signer = new RSASSASigner(rsaKey);

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("user123")
        .issuer(TEST_ISSUER)
        .audience(TEST_AUDIENCE)
        .expirationTime(new Date(System.currentTimeMillis() + 60000))
        .claim("scope", "read write")
        .claim("roles", List.of("admin", "editor"))
        .build();

    SignedJWT signedJWT = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );

    signedJWT.sign(signer);
    validToken = signedJWT.serialize();

    AdviceWith.adviceWith(camelContext, "jwt-validation", advice -> {
      advice.weaveAddLast().to("mock:result");
      advice.weaveById("fetchJWKSEndpoint").replace().to("mock:fetchJWKS");
    });

    camelContext.start();
  }

  private void resetMockedEndpoints() {
    headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + validToken);

    exchange = ExchangeBuilder.anExchange(camelContext).build();
    exchange.getIn().setHeaders(headers);

    mockFetchJWKS.reset();
    resultEndpoint.reset();

    mockFetchJWKS.whenAnyExchangeReceived(e -> e.setProperty("jwkSet", jwkSet));
  }

  @Test
  void testValidJwtToken() throws Exception {
    resetMockedEndpoints();

    resultEndpoint.expectedMessageCount(1);
    mockFetchJWKS.expectedMessageCount(1);

    producerTemplate.send(exchange);

    mockFetchJWKS.assertIsSatisfied();
    resultEndpoint.assertIsSatisfied();

    assertThat(exchange.getProperty("jwt.validated", Boolean.class)).isTrue();
    assertThat(exchange.getProperty("jwt.sub", String.class)).isEqualTo("user123");

    List<String> scopes = exchange.getProperty("jwt.scopes", List.class);
    List<String> roles = exchange.getProperty("jwt.roles", List.class);
    assertThat(scopes).contains("read", "write");
    assertThat(roles).contains("admin", "editor");
  }

  @Test
  void testInvalidJwtToken() throws Exception {
    resetMockedEndpoints();
    headers.put(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here");
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);

    assertThat(response.getException()).hasMessageContaining("Token validation failed");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testMissingAuthorizationHeader() throws Exception {
    resetMockedEndpoints();
    headers.remove(HttpHeaders.AUTHORIZATION);
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);

    assertThat(response.getException()).hasMessageContaining("Authorization header is missing");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testExpiredToken() throws Exception {
    resetMockedEndpoints();

    JWSSigner signer = new RSASSASigner(rsaKey);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("user123")
        .issuer(TEST_ISSUER)
        .audience(TEST_AUDIENCE)
        .expirationTime(new Date(System.currentTimeMillis() - 60000))
        .build();

    SignedJWT expiredJWT = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );
    expiredJWT.sign(signer);

    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJWT.serialize());
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);
    assertThat(response.getException()).hasMessageContaining("Token validation failed");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testInvalidIssuer() throws Exception {
    resetMockedEndpoints();

    JWSSigner signer = new RSASSASigner(rsaKey);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("user123")
        .issuer("wrong-issuer")
        .audience(TEST_AUDIENCE)
        .expirationTime(new Date(System.currentTimeMillis() + 60000))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );
    jwt.sign(signer);

    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);
    assertThat(response.getException()).hasMessageContaining("Invalid token issuer");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testInvalidAudience() throws Exception {
    resetMockedEndpoints();

    JWSSigner signer = new RSASSASigner(rsaKey);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("user123")
        .issuer(TEST_ISSUER)
        .audience("wrong-audience")
        .expirationTime(new Date(System.currentTimeMillis() + 60000))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );
    jwt.sign(signer);

    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);
    assertThat(response.getException()).hasMessageContaining("Invalid token audience");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testMissingJwks() throws Exception {
    resetMockedEndpoints();

    mockFetchJWKS.whenAnyExchangeReceived(e -> e.setProperty("jwkSet", null));
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);
    assertThat(response.getException()).hasMessageContaining("JWKS not available");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testTokenNotYetValid() throws Exception {
    resetMockedEndpoints();

    JWSSigner signer = new RSASSASigner(rsaKey);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("user123")
        .issuer(TEST_ISSUER)
        .audience(TEST_AUDIENCE)
        .expirationTime(new Date(System.currentTimeMillis() + 60000))
        .notBeforeTime(new Date(System.currentTimeMillis() + 60000))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );
    jwt.sign(signer);

    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);
    assertThat(response.getException()).hasMessageContaining("Token not yet valid");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testTokenWithoutExpirationShouldBeRejected() throws Exception {
    resetMockedEndpoints();

    JWSSigner signer = new RSASSASigner(rsaKey);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("user123")
        .issuer(TEST_ISSUER)
        .audience(TEST_AUDIENCE)
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );
    jwt.sign(signer);

    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);
    assertThat(response.getException()).hasMessageContaining("Token must have an expiration time");
    mockFetchJWKS.assertIsSatisfied();
  }

  @Test
  void testMultiAudienceTokenWithMatchingAudience() throws Exception {
    resetMockedEndpoints();

    JWSSigner signer = new RSASSASigner(rsaKey);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("user123")
        .issuer(TEST_ISSUER)
        .audience(Arrays.asList("other-audience", TEST_AUDIENCE, "another-audience"))
        .expirationTime(new Date(System.currentTimeMillis() + 60000))
        .claim("scope", "read")
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );
    jwt.sign(signer);

    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);
    resultEndpoint.expectedMessageCount(1);

    producerTemplate.send(exchange);

    mockFetchJWKS.assertIsSatisfied();
    resultEndpoint.assertIsSatisfied();
    assertThat(exchange.getProperty("jwt.validated", Boolean.class)).isTrue();
  }

  @Test
  void testNestedRolesClaim() throws Exception {
    resetMockedEndpoints();

    JWSSigner signer = new RSASSASigner(rsaKey);

    // Create the structure that matches your configuration
    Map<String, Object> accountRoles = new HashMap<>();
    accountRoles.put("roles", Arrays.asList("viewer", "uploader"));

    Map<String, Object> resourceAccess = new HashMap<>();
    resourceAccess.put("account", accountRoles);

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("nesteduser")
        .issuer(TEST_ISSUER)
        .audience(TEST_AUDIENCE)
        .expirationTime(new Date(System.currentTimeMillis() + 60000))
        .claim("resource_access", resourceAccess)  // Changed from realm_access to resource_access
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
        claims
    );
    jwt.sign(signer);

    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
    exchange.getIn().setHeaders(headers);
    mockFetchJWKS.expectedMessageCount(1);

    Exchange response = producerTemplate.send(exchange);
    mockFetchJWKS.assertIsSatisfied();

    assertThat(response.getProperty("jwt.validated", Boolean.class)).isTrue();
    List<String> roles = response.getProperty("jwt.roles", List.class);
    assertThat(roles).containsExactlyInAnyOrder("viewer", "uploader");
  }
}
