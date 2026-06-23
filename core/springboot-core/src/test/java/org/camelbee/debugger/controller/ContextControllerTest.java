package org.camelbee.debugger.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.camel.CamelContext;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.service.RouteContextService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ContextControllerTest {

  @Test
  void getRoutes_returnsContextWithRoutesAndMetadata() {
    ContextController controller = new ContextController();
    controller.camelContext = mock(CamelContext.class);
    controller.routeContextService = mock(RouteContextService.class);

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
}
