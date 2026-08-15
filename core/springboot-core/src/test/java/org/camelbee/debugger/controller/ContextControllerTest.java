package org.camelbee.debugger.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.camel.CamelContext;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.camelbee.debugger.service.RouteContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ContextControllerTest {

  private static final String TEST_ROUTE_ID_1 = "route1";
  private static final String TEST_ROUTE_ID_2 = "route2";

  private ContextController controller;

  @BeforeEach
  void setUp() {
    controller = new ContextController();
    controller.camelContext = mock(CamelContext.class);
    controller.routeContextService = mock(RouteContextService.class);
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR, "Test Vendor");
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VERSION, "11.0.1");
  }

  @Test
  void getRoutes_returnsContextWithRoutesAndMetadata() {
    List<CamelRoute> routes = List.of(new CamelRoute("r1", "From[direct:a]", List.of(), false, null));
    when(controller.routeContextService.getCamelRoutes()).thenReturn(routes);
    when(controller.camelContext.getName()).thenReturn("test-context");
    when(controller.camelContext.getVersion()).thenReturn("4.20.0");

    ResponseEntity<CamelBeeContext> response = controller.getRoutes();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    CamelBeeContext body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getName()).isEqualTo("test-context");
    assertThat(body.getCamelVersion()).isEqualTo("4.20.0");
    assertThat(body.getRoutes()).hasSize(1);
    assertThat(body.getFramework()).isNotBlank();
    assertThat(body.getJvm()).isNotBlank();
  }

  /**
   * Proves the nested-outputs shape (an EIP like {@code choice} or {@code multicast} carrying its
   * own child outputs) survives the round trip through the controller into the response body, not
   * just the top-level route/output fields the other tests exercise.
   */
  @Test
  void getRoutes_carriesNestedOutputsAndPerRouteFields() {
    List<CamelRouteOutput> nestedOutputs = Arrays.asList(
        new CamelRouteOutput("nested1", "Nested Output 1", ",", "log", null),
        new CamelRouteOutput("nested2", "Nested Output 2", ";", "mock", null)
    );

    List<CamelRouteOutput> outputs1 = Arrays.asList(
        new CamelRouteOutput("output1", "First Output", "|", "direct", nestedOutputs),
        new CamelRouteOutput("output2", "Second Output", ",", "seda", null)
    );
    List<CamelRouteOutput> outputs2 = List.of(new CamelRouteOutput("output3", "Third Output", "-", "vm", null));

    List<CamelRoute> mockRoutes = Arrays.asList(
        new CamelRoute(TEST_ROUTE_ID_1, "direct:start1", outputs1, false, "direct:error1"),
        new CamelRoute(TEST_ROUTE_ID_2, "direct:start2", outputs2, true, "direct:error2")
    );

    when(controller.routeContextService.getCamelRoutes()).thenReturn(mockRoutes);
    when(controller.camelContext.getName()).thenReturn("TestContext");
    when(controller.camelContext.getVersion()).thenReturn("3.18.0");

    ResponseEntity<CamelBeeContext> response = controller.getRoutes();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    CamelBeeContext context = response.getBody();
    assertThat(context).isNotNull();
    assertThat(context.getJvmInputParameters()).isNotNull();
    assertThat(context.getGarbageCollectors()).isNotNull();

    List<CamelRoute> routes = context.getRoutes();
    assertThat(routes).hasSize(2);

    CamelRoute route1 = routes.get(0);
    assertThat(route1.getId()).isEqualTo(TEST_ROUTE_ID_1);
    assertThat(route1.getOutputs()).hasSize(2);
    assertThat(route1.getRest()).isFalse();
    assertThat(route1.getErrorHandler()).isEqualTo("direct:error1");

    CamelRouteOutput firstOutput = route1.getOutputs().get(0);
    assertThat(firstOutput.getOutputs()).hasSize(2);
    CamelRouteOutput nestedOutput = firstOutput.getOutputs().get(0);
    assertThat(nestedOutput.getId()).isEqualTo("nested1");
    assertThat(nestedOutput.getType()).isEqualTo("log");
    assertThat(nestedOutput.getOutputs()).isNull();

    CamelRoute route2 = routes.get(1);
    assertThat(route2.getRest()).isTrue();
    assertThat(route2.getOutputs()).hasSize(1);
  }

  @Test
  void getRoutes_handlesEmptyRoutes() {
    when(controller.routeContextService.getCamelRoutes()).thenReturn(new ArrayList<>());
    when(controller.camelContext.getName()).thenReturn("TestContext");
    when(controller.camelContext.getVersion()).thenReturn("3.18.0");

    ResponseEntity<CamelBeeContext> response = controller.getRoutes();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    CamelBeeContext context = response.getBody();
    assertThat(context).isNotNull();
    assertThat(context.getRoutes()).isEmpty();
  }

  @Test
  void getRoutes_handlesNullRouteOutputs() {
    List<CamelRoute> mockRoutes = List.of(new CamelRoute(TEST_ROUTE_ID_1, "direct:start1", null, false, "direct:error1"));

    when(controller.routeContextService.getCamelRoutes()).thenReturn(mockRoutes);
    when(controller.camelContext.getName()).thenReturn("TestContext");
    when(controller.camelContext.getVersion()).thenReturn("3.18.0");

    ResponseEntity<CamelBeeContext> response = controller.getRoutes();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    CamelBeeContext context = response.getBody();
    assertThat(context).isNotNull();
    assertThat(context.getRoutes()).hasSize(1);
    assertThat(context.getRoutes().get(0).getOutputs()).isNull();
  }
}
