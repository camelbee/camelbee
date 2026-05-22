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

package org.camelbee.debugger.model.route;

import java.util.List;

/**
 * CamelBeeContext.
 */
public class CamelBeeContext {

  private final List<CamelRoute> routes;

  private final String name;

  private final String jvm;

  private final String jvmInputParameters;

  private final String garbageCollectors;

  private final String framework;

  private final String camelVersion;

  /**
   * Constructor.
   *
   * @param routes             The routes.
   * @param name               The name.
   * @param jvm                The jvm.
   * @param jvmInputParameters The jvmInputParameters.
   * @param garbageCollectors  The garbageCollectors.
   * @param framework          The framework.
   * @param camelVersion       The camelVersion.
   */
  public CamelBeeContext(List<CamelRoute> routes, String name, String jvm, String jvmInputParameters,
      String garbageCollectors, String framework, String camelVersion) {
    this.routes = routes;
    this.name = name;
    this.jvm = jvm;
    this.jvmInputParameters = jvmInputParameters;
    this.garbageCollectors = garbageCollectors;
    this.framework = framework;
    this.camelVersion = camelVersion;
  }

  public List<CamelRoute> getRoutes() {
    return routes;
  }

  public String getName() {
    return name;
  }

  public String getJvm() {
    return jvm;
  }

  public String getJvmInputParameters() {
    return jvmInputParameters;
  }

  public String getGarbageCollectors() {
    return garbageCollectors;
  }

  public String getFramework() {
    return framework;
  }

  public String getCamelVersion() {
    return camelVersion;
  }
}
