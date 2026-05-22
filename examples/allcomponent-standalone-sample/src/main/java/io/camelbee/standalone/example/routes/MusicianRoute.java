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

package io.camelbee.standalone.example.routes;

import io.camelbee.standalone.example.bean.MusicianProcessor;
import io.camelbee.standalone.example.constants.Constants;
import io.camelbee.standalone.example.model.Musician;
import java.util.Map;
import org.apache.camel.builder.RouteBuilder;

/**
 * EIP-rich, infra-free demo topology for the standalone runtime.
 *
 * <p>Mirrors the shape of the Spring Boot / Quarkus "allcomponent" samples (multicast, wireTap,
 * enrich, recipientList, routingSlip, dynamicRouter, bean, timer, file, REST) but using only
 * components that need no external brokers/databases, so it runs immediately. It exists to show
 * a meaningful graph and live message tracing in the CamelBee UI.
 *
 * <p>The platform-http REST configuration is provided by CamelBee itself (see CamelBeeRoutes),
 * so this builder only declares the {@code rest(...)} entry points.
 */
public class MusicianRoute extends RouteBuilder {

  private static final String MUSICIAN_PROCESSOR_ROUTE = "direct:musicianProcessor";

  @Override
  public void configure() {

    rest("/api")
        .post("/musicians").type(Musician.class).to("direct:postMusician")
        .get("/musicians").to("direct:getMusician");

    from("direct:postMusician").routeId("postMusicianRoute")
        .to(MUSICIAN_PROCESSOR_ROUTE);

    from("direct:getMusician").routeId("getMusicianRoute")
        .setBody(constant(new Musician("Miles", "Trumpet")))
        .to(MUSICIAN_PROCESSOR_ROUTE);

    from("timer://foo?period=10000").routeId("timerRoute")
        .setBody(constant("timerTestMessage"))
        .to(MUSICIAN_PROCESSOR_ROUTE);

    from("file://inputdir?delete=true").routeId("fileListenerRoute")
        .to(MUSICIAN_PROCESSOR_ROUTE);

    from(MUSICIAN_PROCESSOR_ROUTE).routeId("musicianProcessorRoute")
        .setProperty(Constants.ORIGINAL_BODY, body())
        .bean(MusicianProcessor.class, "process")
        .wireTap("direct:invokeWireTap")
        .multicast().parallelProcessing()
        .to("direct:invokeMockA")
        .to("direct:invokeMockB")
        .end()
        .enrich("direct:invokeEnrich")
        .recipientList().constant("direct:invokeMockA,direct:invokeMockB")
        .routingSlip().constant("direct:invokeMockC,direct:invokeMockD")
        .dynamicRouter(method(this, "computeEndpoint"))
        .to("log:result");

    from("direct:invokeWireTap").routeId("invokeWireTapRoute")
        .to("log:wiretap");

    from("direct:invokeEnrich").routeId("invokeEnrichRoute")
        .setBody(constant("enrichedData"))
        .to("mock:enrich");

    from("direct:invokeMockA").routeId("invokeMockARoute")
        .setBody(constant("invokedMockABody")).to("mock:A");

    from("direct:invokeMockB").routeId("invokeMockBRoute")
        .setBody(constant("invokedMockBBody")).to("mock:B");

    from("direct:invokeMockC").routeId("invokeMockCRoute")
        .setBody(constant("invokedMockCBody")).to("mock:C");

    from("direct:invokeMockD").routeId("invokeMockDRoute")
        .setBody(constant("invokedMockDBody")).to("mock:D");
  }

  /**
   * Dynamic router target computation - routes once then terminates.
   *
   * @param properties the exchange properties.
   * @return the next endpoint, or null to stop.
   */
  public String computeEndpoint(@org.apache.camel.ExchangeProperties Map<String, Object> properties) {
    Integer invocationCount = (Integer) properties.get("invocationCount");
    if (invocationCount == null) {
      invocationCount = 0;
    }
    invocationCount++;
    properties.put("invocationCount", invocationCount);

    if (invocationCount == 1) {
      return "direct:invokeMockC";
    }
    return null;
  }
}
