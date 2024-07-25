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

package org.camelbee.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

/**
 * The route configurer which sets all listeners, interceptors and the MDCUnitOfWork.
 */
@ApplicationScoped
public class CamelBeeRouteConfigurer {

  /**
   * Configures a route for a CamelBee enabled Camel application.
   *
   * @param routeBuilder The routebuilder to be configured.
   */
  public void configureRoute(RouteBuilder routeBuilder) {

    routeBuilder.getContext().setStreamCaching(true);
    routeBuilder.getContext().setUseMDCLogging(true);
  }

}
