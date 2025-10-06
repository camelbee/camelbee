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
package org.camelbee.debugger.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.camel.CamelContext;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextControllerTest {

  @Mock
  private CamelContext camelContext;

  @Mock
  private MessageService messageService;

  @Mock
  private RouteContextService routeContextService;

  @Mock
  private Config config;

  @InjectMocks
  private ContextController contextController;

  private static final String TEST_ROUTE_ID_1 = "route1";
  private static final String TEST_ROUTE_ID_2 = "route2";

  @BeforeEach
  void setUp() {
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR, "Test Vendor");
    System.setProperty(CamelBeeConstants.SYSTEM_JVM_VERSION, "11.0.1");
  }

  @Test
  void getWidgetsShouldReturnRoutesAndSystemInfo() {
    // Arrange
    List<CamelRouteOutput> nestedOutputs = Arrays.asList(
        new CamelRouteOutput("nested1", "Nested Output 1", ",", "log", null),
        new CamelRouteOutput("nested2", "Nested Output 2", ";", "mock", null)
    );

    List<CamelRouteOutput> outputs1 = Arrays.asList(
        new CamelRouteOutput("output1", "First Output", "|", "direct", nestedOutputs),
        new CamelRouteOutput("output2", "Second Output", ",", "seda", null)
    );
    List<CamelRouteOutput> outputs2 = Arrays.asList(
        new CamelRouteOutput("output3", "Third Output", "-", "vm", null)
    );

    List<CamelRoute> mockRoutes = Arrays.asList(
        new CamelRoute(TEST_ROUTE_ID_1, "direct:start1", outputs1, false, "direct:error1"),
        new CamelRoute(TEST_ROUTE_ID_2, "direct:start2", outputs2, true, "direct:error2")
    );

    when(routeContextService.getCamelRoutes()).thenReturn(mockRoutes);
    when(camelContext.getName()).thenReturn("TestContext");
    when(camelContext.getVersion()).thenReturn("3.18.0");

    // Act
    Response response = contextController.getWidgets();

    // Assert
    assertEquals(200, response.getStatus());
    CamelBeeContext context = (CamelBeeContext) response.getEntity();
    assertNotNull(context);
    assertEquals("TestContext", context.getName());
    assertEquals(mockRoutes, context.getRoutes());
    assertEquals("Test Vendor - 11.0.1", context.getJvm());
    assertEquals("3.18.0", context.getCamelVersion());
    assertTrue(context.getFramework().startsWith(CamelBeeConstants.FRAMEWORK));
    assertNotNull(context.getJvmInputParameters());
    assertNotNull(context.getGarbageCollectors());

    List<CamelRoute> routes = context.getRoutes();
    assertEquals(2, routes.size());

    CamelRoute route1 = routes.get(0);
    assertEquals(TEST_ROUTE_ID_1, route1.getId());
    assertEquals("direct:start1", route1.getInput());
    assertEquals(2, route1.getOutputs().size());
    assertFalse(route1.getRest());
    assertEquals("direct:error1", route1.getErrorHandler());

    // Verify first route outputs in detail
    CamelRouteOutput firstOutput = route1.getOutputs().get(0);
    assertEquals("output1", firstOutput.getId());
    assertEquals("First Output", firstOutput.getDescription());
    assertEquals("|", firstOutput.getDelimiter());
    assertEquals("direct", firstOutput.getType());
    assertNotNull(firstOutput.getOutputs());
    assertEquals(2, firstOutput.getOutputs().size());

    // Verify nested outputs
    CamelRouteOutput nestedOutput = firstOutput.getOutputs().get(0);
    assertEquals("nested1", nestedOutput.getId());
    assertEquals("Nested Output 1", nestedOutput.getDescription());
    assertEquals(",", nestedOutput.getDelimiter());
    assertEquals("log", nestedOutput.getType());
    assertNull(nestedOutput.getOutputs());

    CamelRoute route2 = routes.get(1);
    assertEquals(TEST_ROUTE_ID_2, route2.getId());
    assertEquals("direct:start2", route2.getInput());
    assertEquals(1, route2.getOutputs().size());
    assertTrue(route2.getRest());
    assertEquals("direct:error2", route2.getErrorHandler());

    // Verify second route output
    CamelRouteOutput route2Output = route2.getOutputs().get(0);
    assertEquals("output3", route2Output.getId());
    assertEquals("Third Output", route2Output.getDescription());
    assertEquals("-", route2Output.getDelimiter());
    assertEquals("vm", route2Output.getType());
    assertNull(route2Output.getOutputs());
  }

  @Test
  void getWidgetsShouldHandleEmptyRoutes() {
    // Arrange
    when(routeContextService.getCamelRoutes()).thenReturn(new ArrayList<>());
    when(camelContext.getName()).thenReturn("TestContext");
    when(camelContext.getVersion()).thenReturn("3.18.0");

    // Act
    Response response = contextController.getWidgets();

    // Assert
    assertEquals(200, response.getStatus());
    CamelBeeContext context = (CamelBeeContext) response.getEntity();
    assertNotNull(context);
    assertTrue(context.getRoutes().isEmpty());
  }

  @Test
  void getWidgetsShouldHandleNullRouteOutputs() {
    // Arrange
    List<CamelRoute> mockRoutes = Arrays.asList(
        new CamelRoute(TEST_ROUTE_ID_1, "direct:start1", null, false, "direct:error1")
    );

    when(routeContextService.getCamelRoutes()).thenReturn(mockRoutes);
    when(camelContext.getName()).thenReturn("TestContext");
    when(camelContext.getVersion()).thenReturn("3.18.0");

    // Act
    Response response = contextController.getWidgets();

    // Assert
    assertEquals(200, response.getStatus());
    CamelBeeContext context = (CamelBeeContext) response.getEntity();
    assertNotNull(context);
    assertEquals(1, context.getRoutes().size());
    assertNull(context.getRoutes().get(0).getOutputs());
  }

  @Test
  void getWidgetsShouldIncludeSystemProperties() {
    // Arrange
    when(routeContextService.getCamelRoutes()).thenReturn(new ArrayList<>());
    when(camelContext.getName()).thenReturn("TestContext");
    when(camelContext.getVersion()).thenReturn("3.18.0");

    // Act
    Response response = contextController.getWidgets();

    // Assert
    assertEquals(200, response.getStatus());
    CamelBeeContext context = (CamelBeeContext) response.getEntity();
    assertNotNull(context);

  }
}
