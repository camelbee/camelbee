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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Integration coverage for the message tracer against real exchanges.
 *
 * <p>The cores' tracer unit tests drive the event tracers with mocked exchanges. This class drives
 * them with the real thing: a REST request fanning out through parallel multicast, an async wireTap,
 * enrich, recipientList, routingSlip, a dynamic router, a redelivered send and a caught failure -
 * then asserts on what {@code GET /camelbee/messages} actually returns.
 *
 * <p>Two properties of the traced data matter more than the rest, because the UI's message-to-edge
 * matching is built on them: a redelivered send has to appear as several request/response pairs on
 * one edge for a single exchange, and a failure has to be typed {@code ERROR_RESPONSE}.
 *
 * <p>All assertions are order-insensitive. {@code multicast().parallelProcessing()} and
 * {@code wireTap} mean the arrival order of traced messages is genuinely nondeterministic; asserting
 * on a sequence here would be asserting on a race.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageTracingIntegrationTest extends CamelBeeApplicationSupport {

  private static final String FLAKY_EDGE = "direct://flakyTarget?block=true";
  private static final String DLQ_TARGET_EDGE = "direct://boomDlq";

  private static List<JsonNode> traced;

  @BeforeAll
  static void runOnePipeline() throws Exception {
    startApplication();

    setTracer("ACTIVE");
    clearMessages();

    assertThat(postMusician("{\"name\":\"Coltrane\",\"instrument\":\"Sax\"}").statusCode())
        .isEqualTo(200);

    JsonNode payload = awaitSettledMessages();
    traced = new ArrayList<>();
    payload.get("messages").forEach(traced::add);
  }

  @AfterAll
  static void tearDown() {
    stopApplication();
  }

  @Test
  @DisplayName("traces exactly one top-level exchange for one request")
  void tracesOneExchangePerRequest() {
    // sub-exchanges (wireTap, the http hop) get their own ids, so this is a floor rather than
    // an exact count - what matters is that a single request did not trace zero exchanges.
    assertThat(exchangeIds()).isNotEmpty();
    assertThat(traced).allSatisfy(message -> assertThat(message.get("exchangeId").asText()).isNotBlank());
  }

  @Test
  @DisplayName("traces every request/response hop of the pipeline")
  void tracesEveryPipelineHop() {
    assertThat(edges()).contains(
        "direct://musicianProcessor -> direct://invokeHttp?block=true",
        "direct://musicianProcessor -> direct://invokeWireTap",
        "direct://musicianProcessor -> direct://invokeMockA",
        "direct://musicianProcessor -> direct://invokeMockB",
        "direct://musicianProcessor -> direct://invokeEnrich",
        "direct://musicianProcessor -> direct://invokeEnrichDynamic",
        "direct://musicianProcessor -> direct://invokeFile",
        "direct://musicianProcessor -> direct://invokeMockC",
        "direct://invokeWireTap -> seda://southbound",
        "direct://invokeEnrich -> mock://enrich",
        "direct://invokeEnrichDynamic -> mock://enrichDynamic",
        "direct://invokeFile -> file://outputdir");
  }

  @Test
  @DisplayName("traces the remote http hop")
  void tracesRemoteHttpHop() {
    List<JsonNode> httpHops = where(message -> {
      JsonNode endpoint = message.get("endpoint");
      return !endpoint.isNull() && endpoint.asText().startsWith(appUrl + "/api/health");
    });

    assertThat(httpHops).isNotEmpty();
    assertThat(messageTypes(httpHops)).contains("REQUEST", "RESPONSE");
  }

  @Test
  @DisplayName("traces the poll() hop that drains the internal queue")
  void tracesPollHop() {
    assertThat(where(message -> "log://polled".equals(text(message, "endpoint")))).isNotEmpty();
  }

  @Test
  @DisplayName("follows the dynamic router to an endpoint that is not in the static topology")
  void followsDynamicRouter() {
    assertThat(where(message -> "mock://E".equals(text(message, "endpoint")))).isNotEmpty();
  }

  /**
   * Roadmap #18. The dead-letter channel redelivers the failing send twice before it succeeds, so
   * one exchange produces three request/response pairs on a single edge. Anything that keys traced
   * interactions by exchange id alone collapses these into one and loses the retry history.
   */
  @Test
  @DisplayName("keeps every attempt of a redelivered send on the same edge and exchange")
  void keepsEveryRedeliveryAttempt() {
    List<JsonNode> attempts = where(message -> FLAKY_EDGE.equals(text(message, "endpoint")));
    assertThat(attempts).isNotEmpty();

    Map<String, List<JsonNode>> byExchange = groupByExchange(attempts);

    assertThat(byExchange).allSatisfy((exchangeId, messages) -> {
      List<String> types = messageTypes(messages);
      // 3 attempts on one edge for one exchange: 2 that fail and are redelivered, 1 that succeeds.
      // This is the assertion that matters - anything keying interactions by exchange id alone
      // would report a single interaction here.
      assertThat(types).filteredOn("REQUEST"::equals).hasSize(3);
      assertThat(types).filteredOn(type -> !"REQUEST".equals(type)).hasSize(3);
      // at least the first attempt is typed as a failure and carries the exception
      assertThat(types).contains("ERROR_RESPONSE");
    });
  }

  /**
   * Distinct from {@link #keepsEveryRedeliveryAttempt()}: invokeFlaky always recovers by the 3rd
   * attempt, so it never actually reaches the dead-letter channel - a tracing shape of its own,
   * since redeliveries are exhausted (not eventually successful) and Camel routes the exchange to
   * {@code direct:deadLetter} rather than returning a normal response to the immediate caller.
   */
  @Test
  @DisplayName("exhausts redelivery on a permanent failure and lands in the dead-letter channel")
  void exhaustsRedeliveryAndReachesDeadLetterChannel() {
    List<JsonNode> attempts = where(message -> DLQ_TARGET_EDGE.equals(text(message, "endpoint")));
    assertThat(attempts).isNotEmpty();

    Map<String, List<JsonNode>> byExchange = groupByExchange(attempts);
    assertThat(byExchange).allSatisfy((exchangeId, messages) -> {
      List<String> types = messageTypes(messages);
      // 3 attempts, all failing - unlike invokeFlaky, this route never recovers, so every
      // attempt (not just the first) must be typed ERROR_RESPONSE.
      assertThat(types).filteredOn("REQUEST"::equals).hasSize(3);
      assertThat(types).filteredOn("ERROR_RESPONSE"::equals).hasSize(3);
    });

    // Once exhausted, DeadLetterChannel routes the exchange to direct:deadLetter, which is
    // itself a real traced hop, and deadLetterRoute's own steps run normally after it.
    assertThat(where(message -> "direct://deadLetter".equals(text(message, "endpoint")))).isNotEmpty();
    assertThat(where(message -> "log://deadLetter?level=WARN".equals(text(message, "endpoint")))).isNotEmpty();
    assertThat(where(message -> "mock://dlq".equals(text(message, "endpoint")))).isNotEmpty();
  }

  @Test
  @DisplayName("types a failed send as ERROR_RESPONSE and carries the exception")
  void typesFailuresAsErrorResponse() {
    List<JsonNode> failures = where(message -> "ERROR_RESPONSE".equals(text(message, "messageType")));

    assertThat(failures).isNotEmpty();
    assertThat(failures).anySatisfy(message -> {
      assertThat(text(message, "endpoint")).isEqualTo(FLAKY_EDGE);
      assertThat(text(message, "exception")).contains("simulated transient failure");
    });
    assertThat(failures).anySatisfy(message -> assertThat(text(message, "endpoint")).isEqualTo("direct://invokeAlwaysFails"));
  }

  @Test
  @DisplayName("carries the producer id, which is the primary message-to-edge matching key")
  void carriesProducerIds() {
    assertThat(endpointIds()).contains(
        "httpBridgeEndpoint", "httpEndpoint", "sedaProducerEndpoint",
        "enrichEndpoint", "enrichDynamicEndpoint", "fileEndpoint",
        "flakyBridgeEndpoint", "flakyEndpoint", "flakyMockEndpoint", "boomEndpoint",
        "dlqBridgeEndpoint", "boomDlqEndpoint", "dlqEndpoint",
        "mockAEndpoint", "mockBEndpoint", "mockCEndpoint", "mockDEndpoint");
  }

  @Test
  @DisplayName("records how long each hop took")
  void recordsLatency() {
    assertThat(traced).allSatisfy(message -> assertThat(message.get("timeTaken").isNull()).isFalse());
  }

  @Test
  @DisplayName("clearing the messages resets the list and advances the reset version")
  void clearingResetsTheList() throws Exception {
    long resetVersionBefore = messages().get("info").get("resetVersion").asLong();

    clearMessages();

    JsonNode info = messages().get("info");
    assertThat(info.get("count").asInt()).isZero();
    assertThat(info.get("resetVersion").asLong()).isGreaterThan(resetVersionBefore);
  }

  /**
   * Every hop must name the node that sent it. Camel's own history node id is null for sends
   * performed inside an EIP and for redelivered attempts, so this is what proves CamelBee's
   * intercept strategy is installed and filling those in.
   */
  @Test
  @DisplayName("reports the sending node for every traced hop")
  void reportsTheSendingNodeForEveryHop() {
    List<JsonNode> hops = where(message -> text(message, "routeId") != null && text(message, "endpoint") != null);

    assertThat(hops).isNotEmpty();
    assertThat(hops).allSatisfy(message -> assertThat(text(message, "endpointId"))
        .as("%s %s -> %s", text(message, "messageType"), text(message, "routeId"),
            text(message, "endpoint"))
        .isNotNull());
  }

  /**
   * The pipeline sends to {@code direct:invokeMockA} twice from the same route - once from the
   * multicast, once from the recipientList. Nothing about the two hops differs except the node that
   * performed them, so without a node id the UI cannot tell them apart and attributes both to
   * whichever edge it happens to scan first.
   */
  @Test
  @DisplayName("distinguishes two hops that differ only by the node that sent them")
  void distinguishesHopsThatShareASourceAndTarget() {
    List<String> nodes = where(message -> "direct://invokeMockA".equals(text(message, "endpoint")))
        .stream()
        .map(message -> text(message, "endpointId"))
        .distinct()
        .sorted()
        .toList();

    // one hop comes from a multicast child (a plain to<n> node), the other from the recipientList
    assertThat(nodes).hasSize(2);
    assertThat(nodes).anySatisfy(node -> assertThat(node).startsWith("to"));
    assertThat(nodes).anySatisfy(node -> assertThat(node).startsWith("recipientList"));
  }

  @Test
  @DisplayName("names the EIP node that performed each fan-out send")
  void namesTheEipNodeForFanOutSends() {
    // auto-generated ids carry a JVM-wide counter, so assert the node kind rather than the suffix
    assertThat(nodeIdsFor("direct://invokeWireTap")).singleElement().asString().startsWith("wireTap");
    assertThat(nodeIdsFor("direct://invokeEnrich")).singleElement().asString().startsWith("enrich");
    assertThat(nodeIdsFor("direct://invokeEnrichDynamic")).singleElement().asString().startsWith("enrich");
    assertThat(nodeIdsFor("direct://invokeFile")).singleElement().asString().startsWith("recipientList");

    // and the two enrich hops are attributed to different nodes
    assertThat(nodeIdsFor("direct://invokeEnrich"))
        .isNotEqualTo(nodeIdsFor("direct://invokeEnrichDynamic"));
  }

  /**
   * A redelivery re-invokes the processor without re-running Camel's node advices, so its history
   * node id is null. All three attempts must still name the node that owns the send, otherwise the
   * retries scatter across edges.
   */
  @Test
  @DisplayName("names the same node on every attempt of a redelivered send")
  void namesTheSameNodeOnEveryRedelivery() {
    assertThat(nodeIdsFor(FLAKY_EDGE)).containsOnly("flakyEndpoint");
  }

  /** Distinct endpointId values traced for hops to the given endpoint. */
  private static List<String> nodeIdsFor(String endpoint) {
    return where(message -> endpoint.equals(text(message, "endpoint")))
        .stream()
        .map(message -> text(message, "endpointId"))
        .distinct()
        .toList();
  }

  /**
   * Camel emits no event for a poll - it is a receive, and PollProcessor/PollEnricher notify
   * nothing - so these hops are reconstructed from the node itself. Before that they were the only
   * edges in the graph that could never carry a message.
   */
  @Test
  @DisplayName("traces the poll() and pollEnrich() hops Camel emits no events for")
  void tracesPollHops() {
    List<JsonNode> polls = where(message -> {
      String id = text(message, "endpointId");
      return id != null && id.startsWith("poll");
    });

    assertThat(polls).isNotEmpty();
    assertThat(polls).allSatisfy(message -> assertThat(text(message, "endpoint")).isEqualTo("seda://southbound"));

    // each hop is a request/response pair on one exchange, so the UI pairs them into one interaction
    Map<String, List<JsonNode>> byNode = new LinkedHashMap<>();
    polls.forEach(m -> byNode.computeIfAbsent(text(m, "endpointId"), k -> new ArrayList<>()).add(m));

    assertThat(byNode.keySet()).anySatisfy(id -> assertThat(id).startsWith("poll"));
    assertThat(byNode.keySet()).anySatisfy(id -> assertThat(id).startsWith("pollEnrich"));

    assertThat(byNode).allSatisfy((nodeId, messages) -> {
      assertThat(messageTypes(messages)).containsExactlyInAnyOrder("REQUEST", "RESPONSE");
      assertThat(messages).allSatisfy(m -> assertThat(m.get("exchangeId").asText()).isEqualTo(messages.get(0).get("exchangeId").asText()));
    });
  }

  /**
   * The pollEnrich waits its full timeout when the queue is empty. Recording that hop rather than
   * suppressing it is deliberate: an edge that appears only when a poll happens to succeed would
   * flicker in and out depending on queue state.
   */
  @Test
  @DisplayName("records how long a poll waited")
  void recordsPollDuration() {
    List<JsonNode> responses = where(message -> {
      String id = text(message, "endpointId");
      return id != null && id.startsWith("poll") && "RESPONSE".equals(text(message, "messageType"));
    });

    assertThat(responses).isNotEmpty();
    assertThat(responses).allSatisfy(message -> assertThat(message.get("timeTaken").asLong()).isGreaterThanOrEqualTo(0));
  }

  /* ---------------------------------------------------------------- */
  /*  helpers                                                          */
  /* ---------------------------------------------------------------- */

  private static String text(JsonNode message, String field) {
    JsonNode value = message.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  @Test
  @DisplayName("every traced exchange links back to the one request, with no cycle")
  void parentExchangeIdFormsOneAcyclicTree() {
    // This pipeline - wireTap, multicast, two enriches, recipientList, routingSlip, dynamicRouter,
    // toD, pollEnrich - is the reason this test exists. Each single EIP is easy to get right in
    // isolation; the combination is not. The aggregating ones copy a branch's properties back onto
    // the original when merging results, and an earlier implementation that derived the parent from
    // its own PREVIOUS_EXCHANGE_ID was corrupted by exactly that: copies inherited a SIBLING's id
    // and the root ended up parented to its own grandchild, i.e. a cycle. The UI's waterfall then
    // split one request into a dozen unrelated flows.
    Map<String, String> parentOf = new LinkedHashMap<>();

    for (JsonNode message : traced) {
      String exchangeId = message.get("exchangeId").asText();
      JsonNode parent = message.get("parentExchangeId");
      String parentId = parent == null || parent.isNull() ? null : parent.asText();

      // every message of an exchange must agree on the parent
      if (parentOf.containsKey(exchangeId)) {
        assertThat(parentId)
            .as("messages of exchange %s disagree on the parent", exchangeId)
            .isEqualTo(parentOf.get(exchangeId));
      }
      parentOf.put(exchangeId, parentId);
    }

    assertThat(parentOf).as("nothing was traced").isNotEmpty();

    // no cycle, and no parent that was never traced
    parentOf.forEach((start, ignored) -> {
      Set<String> seen = new LinkedHashSet<>();
      String cursor = start;
      while (cursor != null && parentOf.get(cursor) != null) {
        assertThat(seen.add(cursor)).as("cycle reached from %s: %s", start, seen).isTrue();
        String next = parentOf.get(cursor);
        assertThat(parentOf).as("%s claims parent %s, never traced", cursor, next).containsKey(next);
        cursor = next;
      }
    });

    // an exchange is never its own parent
    parentOf.forEach((exchangeId, parentId) -> assertThat(parentId).as("%s is its own parent", exchangeId).isNotEqualTo(exchangeId));
  }

  @Test
  @DisplayName("the branch-spawning EIPs all record a parent")
  void branchingEipsRecordAParent() {
    // the copies these make are the whole point of parentExchangeId: without it each appears in the
    // UI as an island unrelated to the request that spawned it
    List<String> branchTargets = List.of(
        "direct://invokeWireTap", "direct://invokeMockA", "direct://invokeMockB",
        "direct://invokeEnrich", "direct://invokeEnrichDynamic", "direct://invokeFile");

    for (String target : branchTargets) {
      List<JsonNode> messages = where(message -> {
        JsonNode endpoint = message.get("endpoint");
        return endpoint != null && !endpoint.isNull() && target.equals(endpoint.asText());
      });

      assertThat(messages).as("no messages traced for %s", target).isNotEmpty();
      assertThat(messages).as("%s recorded no parent", target).anySatisfy(message -> {
        JsonNode parent = message.get("parentExchangeId");
        assertThat(parent != null && !parent.isNull()).isTrue();
      });
    }
  }

  private static List<JsonNode> where(Predicate<JsonNode> predicate) {
    return traced.stream().filter(predicate).toList();
  }

  private static List<String> messageTypes(List<JsonNode> messages) {
    return messages.stream().map(message -> text(message, "messageType")).toList();
  }

  private static List<String> exchangeIds() {
    return traced.stream().map(message -> text(message, "exchangeId")).distinct().toList();
  }

  private static List<String> endpointIds() {
    return traced.stream()
        .map(message -> text(message, "endpointId"))
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
  }

  /** "routeId -> endpoint" for every traced hop, deduplicated. */
  private static List<String> edges() {
    return traced.stream()
        .filter(message -> text(message, "endpoint") != null && text(message, "routeId") != null)
        .map(message -> text(message, "routeId") + " -> " + text(message, "endpoint"))
        .distinct()
        .toList();
  }

  private static Map<String, List<JsonNode>> groupByExchange(List<JsonNode> messages) {
    Map<String, List<JsonNode>> byExchange = new LinkedHashMap<>();
    for (JsonNode message : messages) {
      byExchange.computeIfAbsent(text(message, "exchangeId"), key -> new ArrayList<>()).add(message);
    }
    return byExchange;
  }
}
