package org.camelbee.debugger.model.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteModelTest {

  @Test
  void camelRouteOutput_exposesAllFields() {
    CamelRouteOutput child = new CamelRouteOutput("c1", "child", null, "type", null);
    CamelRouteOutput output = new CamelRouteOutput("o1", "To[kafka:x]", ",", "ToDefinition", List.of(child));

    assertThat(output.getId()).isEqualTo("o1");
    assertThat(output.getDescription()).isEqualTo("To[kafka:x]");
    assertThat(output.getDelimiter()).isEqualTo(",");
    assertThat(output.getType()).isEqualTo("ToDefinition");
    assertThat(output.getOutputs()).containsExactly(child);
  }

  @Test
  void camelRoute_exposesFieldsAndAllowsSettingRest() {
    CamelRouteOutput output = new CamelRouteOutput("o1", "desc", null, "type", null);
    CamelRoute route = new CamelRoute("route-1", "From[direct:in]", List.of(output), false, "direct:dlq");

    assertThat(route.getId()).isEqualTo("route-1");
    assertThat(route.getInput()).isEqualTo("From[direct:in]");
    assertThat(route.getOutputs()).containsExactly(output);
    assertThat(route.getRest()).isFalse();
    assertThat(route.getErrorHandler()).isEqualTo("direct:dlq");

    route.setRest(true);
    assertThat(route.getRest()).isTrue();
  }

  @Test
  void camelBeeContext_exposesAllFields() {
    CamelRoute route = new CamelRoute("r", "in", List.of(), false, null);
    CamelBeeContext ctx = new CamelBeeContext(
        List.of(route), "svc", "jvm-21", "-Xmx512m", "G1", "Spring Boot", "4.20.0");

    assertThat(ctx.getRoutes()).containsExactly(route);
    assertThat(ctx.getName()).isEqualTo("svc");
    assertThat(ctx.getJvm()).isEqualTo("jvm-21");
    assertThat(ctx.getJvmInputParameters()).isEqualTo("-Xmx512m");
    assertThat(ctx.getGarbageCollectors()).isEqualTo("G1");
    assertThat(ctx.getFramework()).isEqualTo("Spring Boot");
    assertThat(ctx.getCamelVersion()).isEqualTo("4.20.0");
  }
}
