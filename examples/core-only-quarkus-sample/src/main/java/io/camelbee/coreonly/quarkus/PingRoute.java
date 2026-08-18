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

package io.camelbee.coreonly.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

/**
 * Two small routes, enough to give CamelBee a topology with a hop in it. Infrastructure-free on
 * purpose so the sample runs anywhere.
 */
@ApplicationScoped
public class PingRoute extends RouteBuilder {

  @Override
  public void configure() {
    from("timer:ping?period={{coreonly.timer-period:60000}}&delay={{coreonly.timer-delay:3600000}}")
        .routeId("pingRoute")
        .setBody(constant("ping"))
        .to("direct:handle");

    from("direct:handle")
        .routeId("handleRoute")
        .log("handled ${body}");
  }
}
