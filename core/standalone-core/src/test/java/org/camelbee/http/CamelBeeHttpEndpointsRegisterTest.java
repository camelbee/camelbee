package org.camelbee.http;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.Test;

/**
 * Covers the live registration path of {@link CamelBeeHttpEndpoints#register}: when the management
 * HTTP server (and its router) are present, the API and UI routes are wired onto the router. The
 * Vert.x router is a deep-stub mock so the fluent route/handler chains resolve without standing up a
 * real server.
 */
class CamelBeeHttpEndpointsRegisterTest {

  private CamelBeeHttpEndpoints endpoints(CamelContext context) {
    return new CamelBeeHttpEndpoints(context, mock(TracerService.class),
        mock(MessageService.class), mock(RouteContextService.class));
  }

  @Test
  void register_wiresApiAndUiRoutesOnManagementRouter() {
    CamelContext context = mock(CamelContext.class);
    ManagementHttpServer management = mock(ManagementHttpServer.class);
    VertxPlatformHttpRouter router = mock(VertxPlatformHttpRouter.class, RETURNS_DEEP_STUBS);

    when(context.hasService(ManagementHttpServer.class)).thenReturn(management);
    when(management.getRouter()).thenReturn(router);
    when(management.getPort()).thenReturn(8081);

    assertThatNoException().isThrownBy(() -> endpoints(context).register(context));
  }

  @Test
  void register_skipsWhenRouterMissing() {
    CamelContext context = mock(CamelContext.class);
    ManagementHttpServer management = mock(ManagementHttpServer.class);

    when(context.hasService(ManagementHttpServer.class)).thenReturn(management);
    when(management.getRouter()).thenReturn(null);

    assertThatNoException().isThrownBy(() -> endpoints(context).register(context));
  }
}
