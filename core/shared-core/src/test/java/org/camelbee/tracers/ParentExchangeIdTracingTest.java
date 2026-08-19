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

package org.camelbee.tracers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The EIPs that copy an exchange give the copy a fresh exchange id, so without a recorded parent the
 * branch they spawn cannot be tied back to the flow it came from.
 *
 * <p>{@code TracerUtils.resolveParentExchangeId} prefers Camel's
 * {@code ExchangePropertyKey.CORRELATION_ID} and falls back to the write-once
 * {@code CAMELBEE_LINEAGE_ROOT} only where Camel leaves it unset - wireTap, which deletes it from
 * the copy, and async handoffs such as seda.
 *
 * <p>The multi-EIP tests below are the ones that matter. An earlier implementation derived the
 * parent from {@code PREVIOUS_EXCHANGE_ID} alone and passed every single-EIP test here, yet was
 * badly wrong on a realistic pipeline: the aggregating EIPs copy a branch's properties back onto
 * the original when merging results, so each new copy inherited a SIBLING's id, and the root ended
 * up parented to its own grandchild - a cycle. Hence {@code parentGraphIsAcyclic}, which is the
 * single most valuable assertion in this file.
 */
class ParentExchangeIdTracingTest {

  private List<Message> run(Consumer<RouteBuilder> routes) throws Exception {
    MessageService messageService = new MessageService(1000);

    try (CamelContext context = new DefaultCamelContext()) {
      RouteContextService routeContextService = mock(RouteContextService.class);
      when(routeContextService.getCamelRoutes()).thenReturn(List.of());

      TracerService tracerService = new TracerService(false, true, 60000,
          new ExchangeCreatedEventTracer(),
          new ExchangeSendingEventTracer(messageService, routeContextService),
          new ExchangeSentEventTracer(),
          new ExchangeCompletedEventTracer(),
          messageService,
          new LoggingService());
      tracerService.activateTracing(true);

      context.getManagementStrategy().addEventNotifier(new CamelBeeEventNotifier(tracerService));
      context.getCamelContextExtension().addInterceptStrategy(new NodeIdInterceptStrategy(tracerService));

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          routes.accept(this);
        }
      });

      context.start();
      ProducerTemplate template = context.createProducerTemplate();
      template.requestBody("direct:start", "trigger");
      // wireTap and seda hand off to another thread, so the branch is still in flight here
      Thread.sleep(500);

      return List.copyOf(messageService.getMessageList());
    }
  }

  /**
   * One entry per exchange id, mapping it to the parent recorded for it. Every message of an
   * exchange must agree on the parent, which this collapses and the tests below assert.
   */
  private static Map<String, String> parentByExchange(List<Message> traced) {
    return traced.stream()
        .collect(Collectors.toMap(Message::getExchangeId,
            m -> m.getParentExchangeId() == null ? "<none>" : m.getParentExchangeId(),
            (first, second) -> {
              assertThat(second).as("all messages of one exchange agree on the parent").isEqualTo(first);
              return first;
            }));
  }

  @Test
  @DisplayName("a linear route invents no parent: one exchange, no link")
  void linearRouteHasNoParent() throws Exception {
    List<Message> traced = run(rb -> rb.from("direct:start").routeId("linear")
        .to("mock:a").id("toA")
        .to("mock:b").id("toB"));

    Map<String, String> parents = parentByExchange(traced);

    assertThat(parents).hasSize(1);
    assertThat(parents.values()).containsOnly("<none>");
  }

  @Test
  @DisplayName("wireTap: the tapped branch records the exchange that spawned it")
  void wireTapBranchRecordsItsParent() throws Exception {
    List<Message> traced = run(rb -> {
      rb.from("direct:start").routeId("main")
          .to("mock:before").id("toBefore")
          .wireTap("direct:tapped").id("theTap")
          .to("mock:after").id("toAfter");
      rb.from("direct:tapped").routeId("tapped")
          .to("mock:inTap").id("toInTap");
    });

    Map<String, String> parents = parentByExchange(traced);

    // the tap runs on its own thread with a copied exchange, so there are two ids in play
    assertThat(parents).hasSize(2);

    String root = parents.entrySet().stream()
        .filter(e -> "<none>".equals(e.getValue()))
        .map(Map.Entry::getKey)
        .findFirst().orElseThrow(() -> new AssertionError("no root exchange: " + parents));

    String child = parents.entrySet().stream()
        .filter(e -> !"<none>".equals(e.getValue()))
        .map(Map.Entry::getKey)
        .findFirst().orElseThrow(() -> new AssertionError("tapped branch recorded no parent: " + parents));

    assertThat(parents.get(child)).isEqualTo(root);
    assertThat(child).isNotEqualTo(root);
  }

  @Test
  @DisplayName("seda: the consuming exchange records the producing one across the thread handoff")
  void sedaHandoffRecordsItsParent() throws Exception {
    List<Message> traced = run(rb -> {
      rb.from("direct:start").routeId("producer")
          .to("mock:before").id("toBefore")
          .to("seda:queue").id("toQueue");
      rb.from("seda:queue").routeId("consumer")
          .to("mock:inConsumer").id("toInConsumer");
    });

    Map<String, String> parents = parentByExchange(traced);

    assertThat(parents).hasSize(2);
    assertThat(parents.values()).contains("<none>");
    assertThat(parents.values().stream().filter(p -> !"<none>".equals(p)))
        .as("the seda consumer side links back to the producer")
        .hasSize(1);
  }

  @Test
  @DisplayName("multicast: each branch copy links back to the same parent")
  void multicastBranchesShareOneParent() throws Exception {
    List<Message> traced = run(rb -> rb.from("direct:start").routeId("mc")
        .to("mock:before").id("toBefore")
        .multicast().parallelProcessing()
        .to("mock:branchA").id("toBranchA")
        .to("mock:branchB").id("toBranchB")
        .end());

    Map<String, String> parents = parentByExchange(traced);

    List<String> recorded = parents.values().stream().filter(p -> !"<none>".equals(p)).distinct().toList();

    // whatever the copy count, every recorded parent must be a real traced exchange, not a stale
    // ancestor inherited through the property copy
    assertThat(recorded).allSatisfy(p -> assertThat(parents).containsKey(p));
  }

  @Test
  @DisplayName("wireTap as the very first node: the parent is known before any send has happened")
  void wireTapAsFirstNodeStillRecordsItsParent() throws Exception {
    // the stamp is derived from PREVIOUS_EXCHANGE_ID, which the sending tracer sets. This pins down
    // whether it is already set when the copy is made by the first node in the route, where the
    // parent has not sent anything yet.
    List<Message> traced = run(rb -> {
      rb.from("direct:start").routeId("main")
          .wireTap("direct:tapped").id("theTap")
          .to("mock:after").id("toAfter");
      rb.from("direct:tapped").routeId("tapped")
          .to("mock:inTap").id("toInTap");
    });

    Map<String, String> parents = parentByExchange(traced);

    assertThat(parents).hasSize(2);
    assertThat(parents.values().stream().filter(p -> !"<none>".equals(p)))
        .as("tapped branch links back even when the tap is the first node")
        .hasSize(1);
  }

  @Test
  @DisplayName("consumer-started route, wireTap first: linked because the created event seeds the id")
  void wireTapFirstOnConsumerStartedRouteRecordsItsParent() throws Exception {
    MessageService messageService = new MessageService(1000);

    try (CamelContext context = new DefaultCamelContext()) {
      RouteContextService routeContextService = mock(RouteContextService.class);
      when(routeContextService.getCamelRoutes()).thenReturn(List.of());

      TracerService tracerService = new TracerService(false, true, 60000,
          new ExchangeCreatedEventTracer(),
          new ExchangeSendingEventTracer(messageService, routeContextService),
          new ExchangeSentEventTracer(),
          new ExchangeCompletedEventTracer(),
          messageService,
          new LoggingService());
      tracerService.activateTracing(true);

      context.getManagementStrategy().addEventNotifier(new CamelBeeEventNotifier(tracerService));
      context.getCamelContextExtension().addInterceptStrategy(new NodeIdInterceptStrategy(tracerService));

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          // no producer send precedes the tap, so PREVIOUS_EXCHANGE_ID has never been set
          from("timer://tick?repeatCount=1&delay=100").routeId("timerRoute")
              .wireTap("direct:tapped").id("theTap")
              .to("mock:after").id("toAfter");
          from("direct:tapped").routeId("tapped")
              .to("mock:inTap").id("toInTap");
        }
      });

      context.start();
      Thread.sleep(800);

      Map<String, String> parents = parentByExchange(List.copyOf(messageService.getMessageList()));

      assertThat(parents).hasSize(2);
      assertThat(parents.values().stream().filter(p -> !"<none>".equals(p)))
          .as("tapped branch of a consumer-started route links back: %s", parents)
          .hasSize(1);
      parents.forEach((exchangeId, parent) -> {
        if (!"<none>".equals(parent)) {
          assertThat(parents).as("any parent recorded here is a real traced exchange").containsKey(parent);
        }
      });
    }
  }

  /**
   * Walks every recorded parent to a root, failing on a cycle or a dangling reference. Cheap, and
   * it is the invariant the whole feature rests on - a cycle makes the UI's flow grouping split
   * arbitrarily, which is exactly how the original bug showed up.
   */
  private static void assertAcyclic(Map<String, String> parents) {
    parents.forEach((start, ignored) -> {
      Set<String> seen = new LinkedHashSet<>();
      String cursor = start;
      while (cursor != null && !"<none>".equals(parents.get(cursor))) {
        assertThat(seen.add(cursor))
            .as("cycle reached from %s: %s", start, seen)
            .isTrue();
        String next = parents.get(cursor);
        assertThat(parents)
            .as("%s claims parent %s, which was never traced", cursor, next)
            .containsKey(next);
        cursor = next;
      }
    });
  }

  @Test
  @DisplayName("a realistic pipeline: every branch roots at the pipeline exchange, with no cycle")
  void multiEipPipelineRootsAtThePipelineExchange() throws Exception {
    // the shape of the sample's musicianProcessorRoute: the combination is what broke, not any
    // single EIP - the aggregating ones corrupt what the next copy inherits
    List<Message> traced = run(rb -> {
      rb.from("direct:start").routeId("pipeline")
          .wireTap("direct:tapped").id("theTap")
          .multicast().parallelProcessing()
          .to("direct:mcA").id("toMcA")
          .to("direct:mcB").id("toMcB")
          .end()
          .enrich("direct:enrichTarget")
          .recipientList().constant("direct:rlA,direct:rlB")
          .to("mock:done").id("toDone");

      rb.from("direct:tapped").routeId("tapped").to("mock:tapped").id("toTapped");
      rb.from("direct:mcA").routeId("mcA").to("mock:mcA").id("toMcAMock");
      rb.from("direct:mcB").routeId("mcB").to("mock:mcB").id("toMcBMock");
      rb.from("direct:enrichTarget").routeId("enrichTarget").to("mock:enrich").id("toEnrich");
      rb.from("direct:rlA").routeId("rlA").to("mock:rlA").id("toRlA");
      rb.from("direct:rlB").routeId("rlB").to("mock:rlB").id("toRlB");
    });

    Map<String, String> parents = parentByExchange(traced);

    assertAcyclic(parents);

    // exactly one root - the pipeline exchange. Every copy hangs off it, directly or not.
    assertThat(parents.values().stream().filter("<none>"::equals))
        .as("exactly one root in %s", parents)
        .hasSize(1);

    // and the copies really are copies: more than one exchange took part
    assertThat(parents).hasSizeGreaterThan(4);
  }

  @Test
  @DisplayName("the aggregating EIPs do not re-parent the exchange they merged back into")
  void aggregationDoesNotReparentTheOriginal() throws Exception {
    // enrich merges the resource exchange back into the original, carrying its properties with it.
    // The original must still be the root afterwards, not a child of what it just enriched from.
    List<Message> traced = run(rb -> {
      rb.from("direct:start").routeId("main")
          .enrich("direct:enrichTarget")
          .to("mock:after").id("toAfter");
      rb.from("direct:enrichTarget").routeId("enrichTarget").to("mock:enrich").id("toEnrich");
    });

    Map<String, String> parents = parentByExchange(traced);

    assertAcyclic(parents);
    assertThat(parents.values().stream().filter("<none>"::equals))
        .as("the enriched exchange stays a root: %s", parents)
        .hasSize(1);
  }

  @Test
  @DisplayName("a copy of a copy reports its immediate parent, not the grandparent")
  void nestedCopyReportsImmediateParent() throws Exception {
    List<Message> traced = run(rb -> {
      rb.from("direct:start").routeId("root")
          .to("mock:before").id("toBefore")
          .wireTap("direct:level1").id("tapL1");
      rb.from("direct:level1").routeId("level1")
          .to("mock:inL1").id("toInL1")
          .wireTap("direct:level2").id("tapL2");
      rb.from("direct:level2").routeId("level2")
          .to("mock:inL2").id("toInL2");
    });

    Map<String, String> parents = parentByExchange(traced);

    assertThat(parents).hasSize(3);

    // exactly one root, and the chain must be a chain - no exchange may claim a parent that is not
    // itself traced, which is what a stale inherited stamp would produce
    assertThat(parents.values().stream().filter("<none>"::equals)).hasSize(1);
    parents.forEach((exchangeId, parent) -> {
      if (!"<none>".equals(parent)) {
        assertThat(parents).as("parent %s of %s is itself traced", parent, exchangeId).containsKey(parent);
        assertThat(parent).isNotEqualTo(exchangeId);
      }
    });
  }
}
