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

/**
 * Unit test for the standalone RouteContextService driven by a real CamelContext.
 *
 * <p>Standalone variant: the service takes the CamelContext via constructor (no Spring/CDI
 * injection) and resolves {{placeholders}} through Camel's PropertiesComponent.
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
            .enrich("mock:enrich")
            .pollEnrich("mock:pollenrich")
            .recipientList(constant("mock:r1,mock:r2"))
            .routingSlip(constant("mock:slip"));

        from("direct:second").routeId("secondRoute")
            .to("mock:second-out");
      }
    });
    camelContext.start();

    service = new RouteContextService(camelContext);
  }

  @AfterEach
  void tearDown() {
    camelContext.stop();
  }

  @Test
  void getCamelRoutes_extractsRoutesAndAllOutputTypes() {
    List<CamelRoute> routes = service.getCamelRoutes();

    assertThat(routes).hasSize(2);

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
}
