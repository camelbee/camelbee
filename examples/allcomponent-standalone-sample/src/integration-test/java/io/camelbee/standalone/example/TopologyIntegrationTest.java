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

package io.camelbee.standalone.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Integration coverage for {@code GET /camelbee/routes} against a real, running CamelContext.
 *
 * <p>The cores' unit tests already assert topology extraction against a hand-built context. What
 * they cannot cover, and what this class exists for, is the whole chain: a real Camel 4.x runtime →
 * {@code RouteContextService} → JSON serialization → the HTTP handler → the wire format the UI
 * actually consumes.
 *
 * <p>It is therefore also the drift tripwire for Camel's {@code toString()} recipes ({@code to[…]},
 * {@code DynamicTo[toD[…]]}, {@code Poll[…]}, {@code Enrich[constant{…}]}, …). If a Camel upgrade
 * changes one of those strings, the UI's endpoint parser silently stops resolving edges - and these
 * assertions are what turns that into a build failure.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopologyIntegrationTest extends CamelBeeApplicationSupport {

  private static JsonNode topology;

  @BeforeAll
  static void startUp() throws Exception {
    startApplication();
    topology = topology();
  }

  @AfterAll
  static void tearDown() {
    stopApplication();
  }

  @Test
  @DisplayName("reports the runtime it is embedded in")
  void reportsRuntimeMetadata() {
    assertThat(topology.get("name").asText()).isEqualTo("camelbee-standalone-sample");
    assertThat(topology.get("framework").asText()).startsWith("Standalone - Camel ");
    assertThat(topology.get("camelVersion").asText()).isNotBlank();
    assertThat(topology.get("jvm").asText()).isNotBlank();
  }

  @Test
  @DisplayName("exposes every route in the sample")
  void exposesEveryRoute() {
    assertThat(routeIds()).contains(
        "postMusicianRoute", "getMusicianRoute", "healthRoute",
        "timerRoute", "fileListenerRoute", "musicianProcessorRoute",
        "invokeHttpRoute", "invokeWireTapRoute", "invokeEnrichRoute", "invokeEnrichDynamicRoute",
        "invokeSedaRoute", "invokeFileRoute",
        "invokeFlakyRoute", "flakyTargetRoute", "invokeAlwaysFailsRoute", "boomRoute",
        "deadLetterRoute",
        "invokeMockARoute", "invokeMockBRoute", "invokeMockCRoute", "invokeMockDRoute",
        "postMusician", "getMusician", "healthCheck");
  }

  @Test
  @DisplayName("flags the rest-dsl verbs, and only those, as rest routes")
  void flagsRestRoutes() {
    List<String> rest = new ArrayList<>();
    routes().forEach(route -> {
      JsonNode flag = route.get("rest");
      if (flag != null && flag.asBoolean(false)) {
        rest.add(route.get("id").asText());
      }
    });
    assertThat(rest).containsExactlyInAnyOrder("postMusician", "getMusician", "healthCheck");
  }

  /**
   * The exact Camel {@code toString()} recipes the UI's endpoint parser is written against. Pinning
   * them here is what makes a Camel upgrade that changes a recipe fail the build instead of quietly
   * emptying the graph.
   */
  @Test
  @DisplayName("serializes every EIP output shape the UI parses")
  void serializesEveryEipOutputShape() {
    Collection<String> pipeline = outputDescriptionsById("musicianProcessorRoute").values();

    assertThat(pipeline).contains(
        "WireTap[direct:invokeWireTap]",
        "DynamicTo[toD[direct:invokeSeda]]",
        "Enrich[constant{direct:invokeEnrich}]",
        "Enrich[constant{direct:invokeEnrichDynamic}]",
        "PollEnrich[constant{seda:southbound}]",
        "RecipientList[constant{direct:invokeMockA,direct:invokeMockB,direct:invokeFile}]",
        "RoutingSlip[constant{direct:invokeMockC,direct:invokeMockD}]");

    // poll() is a separate model type (PollDefinition, not ToDynamicDefinition) and is the one
    // output kind no other sample in this repository exercises.
    assertThat(outputDescriptionsById("invokeSedaRoute").values()).contains("Poll[seda:southbound]");
  }

  @Test
  @DisplayName("names each EIP output after the kind of node it is")
  void namesOutputsAfterTheirNodeKind() {
    assertThat(outputIdOf("musicianProcessorRoute", "WireTap[direct:invokeWireTap]")).startsWith("wireTap");
    assertThat(outputIdOf("musicianProcessorRoute", "Enrich[constant{direct:invokeEnrich}]")).startsWith("enrich");
    assertThat(outputIdOf("musicianProcessorRoute", "PollEnrich[constant{seda:southbound}]")).startsWith("pollEnrich");
    assertThat(outputIdOf("musicianProcessorRoute",
        "RecipientList[constant{direct:invokeMockA,direct:invokeMockB,direct:invokeFile}]"))
        .startsWith("recipientList");
    assertThat(outputIdOf("musicianProcessorRoute",
        "RoutingSlip[constant{direct:invokeMockC,direct:invokeMockD}]")).startsWith("routingSlip");
    assertThat(outputIdOf("invokeSedaRoute", "Poll[seda:southbound]")).startsWith("poll");

    // the two enrich nodes are distinct, which is what lets their hops be told apart
    assertThat(outputIdOf("musicianProcessorRoute", "Enrich[constant{direct:invokeEnrich}]"))
        .isNotEqualTo(outputIdOf("musicianProcessorRoute", "Enrich[constant{direct:invokeEnrichDynamic}]"));
  }

  @Test
  @DisplayName("keeps the query string on a direct: target so edge matching has to normalize it")
  void keepsQueryStringOnDirectTargets() {
    assertThat(outputDescriptionsById("musicianProcessorRoute"))
        .containsEntry("httpBridgeEndpoint", "to[direct:invokeHttp?block=true]");
    assertThat(outputDescriptionsById("invokeFlakyRoute"))
        .containsEntry("flakyEndpoint", "to[direct:flakyTarget?block=true]");

    // ... while the consumer side carries no query string at all. Matching one to the other is
    // exactly what the topology graph has to do to draw these edges.
    assertThat(routeInput("invokeHttpRoute")).isEqualTo("From[direct:invokeHttp]");
    assertThat(routeInput("flakyTargetRoute")).isEqualTo("From[direct:flakyTarget]");
  }

  @Test
  @DisplayName("keeps an unresolvable toD expression verbatim")
  void keepsDynamicToExpressionVerbatim() {
    assertThat(outputDescriptionsById("musicianProcessorRoute").values())
        .contains("DynamicTo[toD[direct:invokeMock${exchangeProperty.mockTarget}]]");
  }

  @Test
  @DisplayName("resolves {{...}} property placeholders in endpoint URIs")
  void resolvesPropertyPlaceholders() {
    String httpTarget = outputDescriptionsById("invokeHttpRoute").get("httpEndpoint");

    assertThat(httpTarget)
        .doesNotContain("{{")
        .isEqualTo("to[" + appUrl + "/api/health?bridgeEndpoint=true]");
  }

  @Test
  @DisplayName("reports the dead-letter channel for routes that inherit it")
  void reportsDeadLetterChannel() {
    assertThat(route("musicianProcessorRoute").get("errorHandler").asText())
        .isEqualTo("direct:deadLetter");

    // routes that opt out with noErrorHandler() report none
    assertThat(route("flakyTargetRoute").get("errorHandler").isNull()).isTrue();
    assertThat(route("boomRoute").get("errorHandler").isNull()).isTrue();
  }

  @Test
  @DisplayName("carries route and node description text")
  void carriesDescriptions() {
    assertThat(route("invokeSedaRoute").get("routeDescription").asText())
        .isEqualTo("Drains the internal queue with poll()");

    JsonNode httpBridge = output("musicianProcessorRoute", "httpBridgeEndpoint");
    assertThat(httpBridge.get("nodeDescription").asText())
        .isEqualTo("Calls the remote http endpoint");

    // routes and outputs without an explicit description stay null rather than empty string
    assertThat(route("boomRoute").get("routeDescription").isNull()).isTrue();
    assertThat(output("invokeMockARoute", "mockAEndpoint").get("nodeDescription").isNull()).isTrue();
  }

  /**
   * {@code RecipientListDefinition.getDelimiter()} is null unless set in the DSL - the "," default
   * is applied by the reifier at runtime, not stored in the model - whereas routingSlip does store
   * it. The UI relies on that asymmetry (it falls back to "," itself), so it is pinned here.
   */
  @Test
  @DisplayName("reports delimiters only where Camel actually stores them")
  void reportsDelimitersOnlyWhereStored() {
    String recipientList = outputIdOf("musicianProcessorRoute",
        "RecipientList[constant{direct:invokeMockA,direct:invokeMockB,direct:invokeFile}]");
    String routingSlip = outputIdOf("musicianProcessorRoute",
        "RoutingSlip[constant{direct:invokeMockC,direct:invokeMockD}]");

    assertThat(output("musicianProcessorRoute", recipientList).get("delimiter").isNull()).isTrue();
    assertThat(output("musicianProcessorRoute", routingSlip).get("delimiter").asText()).isEqualTo(",");
  }

  /* ---------------------------------------------------------------- */
  /*  helpers                                                          */
  /* ---------------------------------------------------------------- */

  private static Iterable<JsonNode> routes() {
    return topology.get("routes");
  }

  private static List<String> routeIds() {
    return StreamSupport.stream(routes().spliterator(), false)
        .map(route -> route.get("id").asText())
        .toList();
  }

  private static JsonNode route(String routeId) {
    for (JsonNode route : routes()) {
      if (routeId.equals(route.get("id").asText())) {
        return route;
      }
    }
    throw new AssertionError("no route '" + routeId + "' in topology; have " + routeIds());
  }

  private static String routeInput(String routeId) {
    return route(routeId).get("input").asText();
  }

  private static JsonNode output(String routeId, String outputId) {
    for (JsonNode output : route(routeId).get("outputs")) {
      if (outputId.equals(output.get("id").asText())) {
        return output;
      }
    }
    throw new AssertionError("no output '" + outputId + "' on route '" + routeId + "'");
  }

  /**
   * Id of the output with the given description.
   *
   * <p>Auto-generated node ids ({@code wireTap1}, {@code enrich2}, ...) carry a counter that is
   * JVM-wide, so it shifts depending on how many contexts were built before this one. Tests must
   * therefore look outputs up by their description - which is the thing worth pinning anyway - and
   * assert only the node-kind prefix of the id.
   */
  private static String outputIdOf(String routeId, String description) {
    for (JsonNode output : route(routeId).get("outputs")) {
      if (description.equals(output.get("description").asText())) {
        return output.get("id").asText();
      }
    }
    throw new AssertionError("no output described '" + description + "' on route '" + routeId
        + "'; have " + outputDescriptionsById(routeId).values());
  }

  private static Map<String, String> outputDescriptionsById(String routeId) {
    Map<String, String> byId = new java.util.LinkedHashMap<>();
    for (JsonNode output : route(routeId).get("outputs")) {
      byId.put(output.get("id").asText(), output.get("description").asText());
    }
    return byId;
  }
}
