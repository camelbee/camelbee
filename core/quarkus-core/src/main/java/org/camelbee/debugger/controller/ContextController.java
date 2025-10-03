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

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.service.RouteContextService;
import org.eclipse.microprofile.config.Config;

/**
 * ContextController exposes routes topology and messages.
 */
@Path("/")
@IfBuildProperty(name = "camelbee.context-enabled", stringValue = "true")
public class ContextController {

  @Inject
  CamelContext camelContext;

  @Inject
  RouteContextService routeContextService;

  @Inject
  Config config;

  /**
   * Returns the Routes list of the camelContext and their outputs.
   *
   * @return CamelBeeContext The routes topology.
   */
  @GET
  @Consumes("application/json")
  @Produces("application/json")
  @Path("/camelbee/routes")
  public Response getWidgets() {

    List<CamelRoute> routes = routeContextService.getCamelRoutes();

    String name = camelContext.getName();

    String jvm = "%s - %s".formatted(System.getProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR),
        System.getProperty(CamelBeeConstants.SYSTEM_JVM_VERSION));

    String framework = "%s - %s".formatted(CamelBeeConstants.FRAMEWORK,
        io.quarkus.runtime.Quarkus.class.getPackage().getImplementationVersion());

    String camelVersion = camelContext.getVersion();

    String jvmInputParameters = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .collect(Collectors.joining(", "));

    String garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName)
        .collect(Collectors.joining(", "));

    return Response
        .ok(new CamelBeeContext(routes, name, jvm, jvmInputParameters, garbageCollectors, framework, camelVersion))
        .build();
  }

}
