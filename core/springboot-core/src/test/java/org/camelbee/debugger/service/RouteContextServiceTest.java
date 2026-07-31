package org.camelbee.debugger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit test for RouteContextService driven by a real (non-Spring) CamelContext.
 */
class RouteContextServiceTest {

  private DefaultCamelContext camelContext;
  private RouteContextService service;

  @BeforeEach
  void setUp() throws Exception {
    camelContext = new DefaultCamelContext();
    camelContext.addRoutes(new RouteBuilder() {

      @Override
      public void configure() {
        from("direct:start").routeId("mainRoute")
            .errorHandler(deadLetterChannel("mock:dlq"))
            .to("mock:out")
            .toD("mock:dynamic")
            .wireTap("mock:tap")
            .enrich("mock:enrich")
            .pollEnrich("mock:pollenrich")
            .recipientList(constant("mock:r1,mock:r2"))
            .routingSlip(constant("mock:slip"))
            // header is absent at runtime, so the router stops immediately
            .dynamicRouter(header("nextEndpoint"));

        from("direct:second").routeId("secondRoute")
            .to("mock:second-out");

        from("direct:third").routeId("thirdRoute").description("Handles the third widget")
            .to("mock:third").id("toThird").description("Sends the widget onward")
            .poll("mock:polled");
      }
    });
    camelContext.start();

    service = new RouteContextService();
    service.camelContext = camelContext;
    service.env = new MockEnvironment();
  }

  @AfterEach
  void tearDown() {
    camelContext.stop();
  }

  @Test
  void getCamelRoutes_extractsRoutesAndAllOutputTypes() {
    List<CamelRoute> routes = service.getCamelRoutes();

    assertThat(routes).hasSize(3);

    CamelRoute main = routes.stream()
        .filter(r -> r.getId().equals("mainRoute"))
        .findFirst()
        .orElseThrow();

    assertThat(main.getInput()).contains("direct:start");
    assertThat(main.getErrorHandler()).isEqualTo("mock:dlq");

    // To, ToDynamic, Enrich, PollEnrich, RecipientList, RoutingSlip all extracted.
    List<String> descriptions = main.getOutputs().stream()
        .map(CamelRouteOutput::getDescription)
        .toList();
    assertThat(descriptions).anyMatch(d -> d.contains("mock:out"));
    assertThat(descriptions).anyMatch(d -> d.toLowerCase().contains("dynamic"));
    assertThat(descriptions).anyMatch(d -> d.toLowerCase().contains("enrich"));
    assertThat(main.getOutputs()).isNotEmpty();
  }

  @Test
  void getCamelRoutes_isCachedOnSecondCall() {
    List<CamelRoute> first = service.getCamelRoutes();
    List<CamelRoute> second = service.getCamelRoutes();
    assertThat(second).isSameAs(first);
  }

  /**
   * T0 characterization (see README-camel421-notes.md, FINAL ROADMAP v2):
   * pins the EXACT wire format of {@code CamelRouteOutput} as produced today.
   * The description strings are Camel's {@code toString()} output — NOT a
   * stable contract — so this test is the tripwire that catches format drift
   * on Camel version bumps. If it fails after a bump, the UI's string
   * matching (endpointParser.ts) must be re-checked against the new formats.
   */
  @Test
  void getCamelRoutes_characterization_exactOutputSerialization() {
    CamelRoute main = service.getCamelRoutes().stream()
        .filter(r -> r.getId().equals("mainRoute"))
        .findFirst()
        .orElseThrow();

    // Pass order is fixed by extractOutputs: To, ToDynamic(+WireTap),
    // Enrich, PollEnrich, RecipientList, RoutingSlip, DynamicRouter.
    assertThat(main.getOutputs())
        .extracting(CamelRouteOutput::getDescription)
        .containsExactly(
            // NOTE: lowercase "to[…]" — Camel's actual recipe (SendDefinition
            // getLabel), unlike the capitalized DynamicTo/WireTap wrappers.
            // The UI matches case-insensitively, so this is safe — but any
            // NEW matching code must never assume "To[".
            "to[mock:out]",
            "DynamicTo[toD[mock:dynamic]]",
            "WireTap[mock:tap]",
            "Enrich[constant{mock:enrich}]",
            "PollEnrich[constant{mock:pollenrich}]",
            "RecipientList[constant{mock:r1,mock:r2}]",
            "RoutingSlip[constant{mock:slip}]",
            // A dynamicRouter draws no static edge - its targets are computed per exchange - but is
            // collected so the traced messages' node id can be resolved back to this route.
            "DynamicRouter[header{nextEndpoint}]");

    // wireTap is collected by the ToDynamicDefinition pass (WireTapDefinition
    // is its subclass). A dedicated WireTapDefinition pass must NEVER be
    // added — it would double-collect every wireTap (roadmap: deleted #2).
    CamelRouteOutput wireTap = main.getOutputs().stream()
        .filter(o -> o.getDescription().startsWith("WireTap["))
        .findFirst()
        .orElseThrow();
    assertThat(wireTap.getType()).isEqualTo("org.apache.camel.model.WireTapDefinition");
    assertThat(main.getOutputs())
        .filteredOn(o -> o.getDescription().startsWith("WireTap["))
        .hasSize(1);

    // The model's delimiter is NULL unless set explicitly in the DSL — the
    // "," default is applied at runtime by the reifier, not stored in the
    // definition. The UI compensates with `output.delimiter ?? ','`
    // (endpointParser.ts) — that fallback is load-bearing, do not remove.
    assertThat(main.getOutputs())
        .filteredOn(o -> o.getDescription().startsWith("RecipientList["))
        .extracting(CamelRouteOutput::getDelimiter)
        .containsOnlyNulls();

    // Nested outputs are always null today (roadmap #7: leave as is).
    assertThat(main.getOutputs())
        .extracting(CamelRouteOutput::getOutputs)
        .containsOnlyNulls();

    // Every output has a non-blank id (the UI's primary message-match key).
    assertThat(main.getOutputs())
        .extracting(CamelRouteOutput::getId)
        .allSatisfy(id -> assertThat(id).isNotBlank());
  }

  /**
   * Roadmap #1+15 (route descriptions): {@code <description>} text is populated
   * from {@code getDescriptionText()} on both the route and its outputs, via the
   * new add-only constructor overloads (README-camel421-notes.md, FINAL ROADMAP v2).
   */
  @Test
  void getCamelRoutes_populatesRouteAndNodeDescriptions() {
    CamelRoute third = service.getCamelRoutes().stream()
        .filter(r -> r.getId().equals("thirdRoute"))
        .findFirst()
        .orElseThrow();

    assertThat(third.getRouteDescription()).isEqualTo("Handles the third widget");

    CamelRouteOutput toThird = third.getOutputs().stream()
        .filter(o -> o.getId().equals("toThird"))
        .findFirst()
        .orElseThrow();
    assertThat(toThird.getNodeDescription()).isEqualTo("Sends the widget onward");

    // Routes/outputs without an explicit <description> stay null, not empty string.
    CamelRoute main = service.getCamelRoutes().stream()
        .filter(r -> r.getId().equals("mainRoute"))
        .findFirst()
        .orElseThrow();
    assertThat(main.getRouteDescription()).isNull();
    assertThat(main.getOutputs()).extracting(CamelRouteOutput::getNodeDescription).containsOnlyNulls();
  }

  /**
   * Roadmap #22 (poll() extraction): PollDefinition is a genuine coverage gap —
   * Camel's own RouteTopologyDumper collects it (it implements
   * EndpointRequiredDefinition) but CamelBee did not, before this change.
   * PollDefinition extends NoOutputDefinition (NOT ToDynamicDefinition), so this
   * is a new pass, not a duplicate of the WireTap situation (roadmap: deleted #2).
   */
  @Test
  void getCamelRoutes_extractsPollOutputs() {
    CamelRoute third = service.getCamelRoutes().stream()
        .filter(r -> r.getId().equals("thirdRoute"))
        .findFirst()
        .orElseThrow();

    assertThat(third.getOutputs())
        .extracting(CamelRouteOutput::getDescription)
        .anySatisfy(d -> assertThat(d).isEqualTo("Poll[mock:polled]"));

    CamelRouteOutput poll = third.getOutputs().stream()
        .filter(o -> o.getDescription().startsWith("Poll["))
        .findFirst()
        .orElseThrow();
    assertThat(poll.getType()).isEqualTo("org.apache.camel.model.PollDefinition");
    // Never collected twice via any other filter pass.
    assertThat(third.getOutputs())
        .filteredOn(o -> o.getDescription().startsWith("Poll["))
        .hasSize(1);
  }

  /**
   * Roadmap #3 (query-param-proof edge matching): adjustRestInputRoutes previously
   * matched a REST-openapi route's synthetic "From[direct:opId]" output description
   * against a real route's raw input string with plain {@code .equals()}. A real
   * route's {@code from()} commonly carries query params (bridgeErrorHandler,
   * timeout, ...) that the synthetic, query-free description never has, so the
   * exact-equality check silently failed to flag the route as REST.
   */
  @Test
  void adjustRestInputRoutes_matchesDespiteQueryParamsOnTheRealRouteInput() {
    CamelRoute restRoute = new CamelRoute("restRoute", "From[rest-openapi:///openapi.json]",
        List.of(new CamelRouteOutput("", "From[direct:orderOp]", null, null, null)), true, null);
    CamelRoute targetRoute = new CamelRoute("orderRoute",
        "From[direct:orderOp?bridgeErrorHandler=true]", List.of(), false, null);

    service.adjustRestInputRoutes(List.of(restRoute), List.of(targetRoute));

    assertThat(targetRoute.getRest()).isTrue();
  }

  @Test
  void adjustRestInputRoutes_isCaseInsensitive() {
    CamelRoute restRoute = new CamelRoute("restRoute", "From[rest-openapi:///openapi.json]",
        List.of(new CamelRouteOutput("", "FROM[Direct:OrderOp]", null, null, null)), true, null);
    CamelRoute targetRoute = new CamelRoute("orderRoute", "From[direct:orderOp]", List.of(), false, null);

    service.adjustRestInputRoutes(List.of(restRoute), List.of(targetRoute));

    assertThat(targetRoute.getRest()).isTrue();
  }

  @Test
  void adjustRestInputRoutes_doesNotFlagUnrelatedRoutes() {
    CamelRoute restRoute = new CamelRoute("restRoute", "From[rest-openapi:///openapi.json]",
        List.of(new CamelRouteOutput("", "From[direct:orderOp]", null, null, null)), true, null);
    CamelRoute unrelatedRoute = new CamelRoute("otherRoute", "From[direct:otherOp]", List.of(), false, null);

    service.adjustRestInputRoutes(List.of(restRoute), List.of(unrelatedRoute));

    assertThat(unrelatedRoute.getRest()).isFalse();
  }

  /**
   * A dynamicRouter picks its targets at runtime, so it contributes no static edge. It is still
   * collected because the traced messages carry its node id, and that is what lets the UI attribute
   * those runtime hops to the route that owns the router rather than to whichever route happened to
   * be called just before it.
   */
  @Test
  void getCamelRoutes_extractsDynamicRouterOutputs() {
    CamelRoute main = service.getCamelRoutes().stream()
        .filter(r -> "mainRoute".equals(r.getId()))
        .findFirst()
        .orElseThrow();

    CamelRouteOutput dynamicRouter = main.getOutputs().stream()
        .filter(o -> o.getType().equals("org.apache.camel.model.DynamicRouterDefinition"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no dynamicRouter output; have "
            + main.getOutputs().stream().map(CamelRouteOutput::getType).toList()));

    assertThat(dynamicRouter.getId()).startsWith("dynamicRouter");
    assertThat(dynamicRouter.getDescription()).startsWith("DynamicRouter[");

    // collected exactly once - DynamicRouterDefinition extends ExpressionNode, so no earlier pass
    // picks it up as well
    assertThat(main.getOutputs())
        .filteredOn(o -> o.getType().equals("org.apache.camel.model.DynamicRouterDefinition"))
        .hasSize(1);
  }
}
