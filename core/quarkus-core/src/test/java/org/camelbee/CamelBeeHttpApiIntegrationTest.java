/*
 * Copyright 2023 Rahmi Ege Karaosmanoglu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camelbee;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives the CamelBee REST API over real HTTP against a real Quarkus application.
 *
 * <p>Every other test in this module exercises a class or a bean. This one exists for the thing
 * those cannot see: that the whole chain is actually wired together under CDI - JAX-RS resources
 * registered and reachable at their paths, {@code camelbee.*} resolved through MicroProfile Config,
 * the notifier attached to the Camel context, and masking configured before anything is traced. Each
 * of those is wired differently in each runtime, so a change that works on standalone-core can be
 * broken here and nothing else would notice until someone ran the sample by hand.
 *
 * <p>Deliberately in the core rather than in the sample application: the core is what is published
 * to Maven Central, and the samples need MongoDB to start, which makes them a poor gate for a
 * release.
 *
 * <p>Mirrors {@code springboot-core}'s test of the same name, assertion for assertion, so a
 * behavioural difference between the two runtimes shows up as one of them failing.
 */
@QuarkusTest
@TestProfile(CamelBeeHttpApiIntegrationTest.ApiEnabledProfile.class)
class CamelBeeHttpApiIntegrationTest extends CamelQuarkusTestSupport {

  /**
   * The controllers are gated by {@code @IfBuildProperty}, which is evaluated at BUILD time - the
   * shared {@code src/test/resources/application.properties} cannot enable them just for this test,
   * and must not enable them globally because
   * {@code CamelBeeRouteConfigurerTest.shouldNotRegisterContextControllerWhenContextEnabledIsUnset}
   * asserts the opposite. A profile is the only lever that re-evaluates a build-time condition,
   * because Quarkus restarts the application for it.
   */
  public static class ApiEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "camelbee.context-enabled", "true",
          "camelbee.tracer-enabled", "true");
    }
  }

  private static final String SECRET = "must-not-be-traced";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();

  /** Quarkus picks a random test port; this is how the test learns it. */
  @TestHTTPResource
  URI baseUri;

  @Inject
  ProducerTemplate producerTemplate;

  @Override
  protected RouteBuilder createRouteBuilder() {
    return new RouteBuilder() {

      @Override
      public void configure() {
        // a wireTap so the traced data has a spawned exchange to link back, which is what
        // parentExchangeId exists for
        from("direct:apiTest").routeId("apiTestRoute")
            .wireTap("direct:apiTapped").id("apiTap")
            .to("mock:apiTarget").id("apiTarget");

        from("direct:apiTapped").routeId("apiTappedRoute")
            .to("mock:apiTapped").id("apiTappedTarget");
      }
    };
  }

  private URI url(String path) {
    return baseUri.resolve(path);
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return send(HttpRequest.newBuilder(url(path)).GET().build());
  }

  private void post(String path, String contentType, String body) throws Exception {
    send(HttpRequest.newBuilder(url(path))
        .header("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build());
  }

  private void delete(String path) throws Exception {
    send(HttpRequest.newBuilder(url(path)).DELETE().build());
  }

  private void setTracer(String status) throws Exception {
    post("/camelbee/tracer/status", "application/json", "\"" + status + "\"");
  }

  private void setCaptureFilter(String filter) throws Exception {
    post("/camelbee/tracer/filter", "text/plain", filter);
  }

  private List<JsonNode> tracedMessages() throws Exception {
    HttpResponse<String> response = get("/camelbee/messages?index=0&addVersion=-1&resetVersion=-1");
    assertThat(response.statusCode()).isEqualTo(200);

    List<JsonNode> messages = new ArrayList<>();
    MAPPER.readTree(response.body()).get("messages").forEach(messages::add);
    return messages;
  }

  @BeforeEach
  void resetTracer() throws Exception {
    delete("/camelbee/messages");
    setCaptureFilter("");
    setTracer("ACTIVE");
  }

  @Test
  @DisplayName("serves the route topology")
  void servesTopology() throws Exception {
    HttpResponse<String> response = get("/camelbee/routes");

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode topology = MAPPER.readTree(response.body());

    assertThat(topology.get("framework").asText()).isNotBlank();
    assertThat(topology.get("camelVersion").asText()).isNotBlank();
    assertThat(topology.toString()).contains("apiTestRoute").contains("apiTappedRoute");
  }

  @Test
  @DisplayName("traces messages end to end, and links the wireTap branch to its parent")
  void tracesAndLinksSpawnedExchanges() throws Exception {
    producerTemplate.sendBody("direct:apiTest", "{\"id\":\"order-1\"}");
    Thread.sleep(400);

    List<JsonNode> messages = tracedMessages();
    assertThat(messages).isNotEmpty();

    // the field only exists if the whole tracer chain ran under Spring's wiring
    assertThat(messages).anySatisfy(m -> {
      JsonNode parent = m.get("parentExchangeId");
      assertThat(parent != null && !parent.isNull()).isTrue();
    });

    // an exchange is never its own parent, which is how the correlation bug showed up
    messages.forEach(m -> {
      JsonNode parent = m.get("parentExchangeId");
      if (parent != null && !parent.isNull()) {
        assertThat(parent.asText()).isNotEqualTo(m.get("exchangeId").asText());
      }
    });
  }

  @Test
  @DisplayName("redacts sensitive values before they reach the API")
  void redactsSensitiveValues() throws Exception {
    producerTemplate.sendBodyAndHeader("direct:apiTest",
        "{\"user\":\"ege\",\"password\":\"" + SECRET + "\"}",
        "Authorization", "Bearer " + SECRET);
    Thread.sleep(400);

    String served = tracedMessages().toString();

    assertThat(served).doesNotContain(SECRET);
    // positive check, so this cannot pass just because nothing was traced
    assertThat(served).contains("***").contains("ege");
  }

  @Test
  @DisplayName("records only the flow matching the capture filter")
  void appliesCaptureFilter() throws Exception {
    setCaptureFilter("order-wanted");

    producerTemplate.sendBody("direct:apiTest", "{\"id\":\"order-wanted\"}");
    producerTemplate.sendBody("direct:apiTest", "{\"id\":\"order-ignored\"}");
    Thread.sleep(400);

    String served = tracedMessages().toString();

    assertThat(served).contains("order-wanted");
    assertThat(served).doesNotContain("order-ignored");
  }

  @Test
  @DisplayName("clears traced messages")
  void clearsMessages() throws Exception {
    producerTemplate.sendBody("direct:apiTest", "{\"id\":\"order-1\"}");
    Thread.sleep(400);
    assertThat(tracedMessages()).isNotEmpty();

    delete("/camelbee/messages");

    assertThat(tracedMessages()).isEmpty();
  }

  @Test
  @DisplayName("stops collecting once tracing is switched off")
  void stopsCollectingWhenInactive() throws Exception {
    setTracer("INACTIVE");
    delete("/camelbee/messages");

    producerTemplate.sendBody("direct:apiTest", "{\"id\":\"order-1\"}");
    Thread.sleep(400);

    assertThat(tracedMessages()).isEmpty();
  }

  @Test
  @DisplayName("serves the embedded UI, so a broken bundle is caught before release")
  void servesEmbeddedUi() throws Exception {
    // the UI is copied into the jar at build time, and a packaging change can silently stop that
    // without failing anything - the page just 404s for whoever adds the dependency
    HttpResponse<String> response = get("/camelbee/index.html");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("<div id=\"root\"");
  }
}
