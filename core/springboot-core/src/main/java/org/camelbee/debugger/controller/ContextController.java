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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.service.RouteContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ContextController exposes routes topology and messages with version control.
 */
@RestController
@CrossOrigin(origins = {"https://www.camelbee.io", "http://localhost:8083"})
@ConditionalOnProperty(value = "camelbee.context-enabled", havingValue = "true")
public class ContextController {

  @Autowired
  CamelContext camelContext;

  @Autowired
  RouteContextService routeContextService;

  /**
   * Returns the Routes list of the camelContext and their outputs.
   *
   * @return CamelBeeContext The routes topology.
   */
  @GetMapping(value = "/camelbee/routes")
  public ResponseEntity<CamelBeeContext> getRoutes() {

    List<CamelRoute> routes = routeContextService.getCamelRoutes();

    String name = camelContext.getName();

    String jvm = "%s - %s".formatted(System.getProperty(CamelBeeConstants.SYSTEM_JVM_VENDOR),
        System.getProperty(CamelBeeConstants.SYSTEM_JVM_VERSION));

    String framework = "%s - %s".formatted(CamelBeeConstants.FRAMEWORK,
        org.springframework.boot.SpringBootVersion.getVersion());

    String camelVersion = camelContext.getVersion();

    String jvmInputParameters = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .collect(Collectors.joining(", "));

    String garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName)
        .collect(Collectors.joining(", "));

    return ResponseEntity.ok(new CamelBeeContext(routes, name, jvm, jvmInputParameters, garbageCollectors, framework, camelVersion));
  }

}
