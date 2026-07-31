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

import io.camelbee.standalone.example.bean.FlakyProcessor;
import io.camelbee.standalone.example.bean.MusicianProcessor;
import io.camelbee.standalone.example.constants.Constants;
import io.camelbee.standalone.example.model.Musician;
import java.util.Map;
import org.apache.camel.builder.RouteBuilder;

/**
 * EIP-rich, infra-free demo topology for the standalone runtime.
 *
 * <p>Mirrors the shape of the Spring Boot / Quarkus "allcomponent" samples (multicast, wireTap,
 * enrich, pollEnrich, poll, recipientList, routingSlip, dynamicRouter, toD, bean, timer, file, seda,
 * http, REST) but using only components that need no external brokers/databases, so it runs
 * immediately. It exists to show a meaningful graph and live message tracing in the CamelBee UI,
 * and to give the integration tests every topology/tracing shape the CamelBee cores extract:
 *
 * <ul>
 * <li>query strings on {@code to("direct:...")} targets, which must still resolve to the
 * matching {@code from("direct:...")} node</li>
 * <li>{@code toD} with both a static and an expression-driven URI</li>
 * <li>producer {@code .id(...)} values, which are the primary message-to-edge matching key</li>
 * <li>route and output {@code .description(...)} text</li>
 * <li>a dead-letter channel plus redelivery, so both retried and failed exchanges are traced</li>
 * <li>a genuinely remote (http) producer, pointed at this application's own REST port so it
 * still needs no external infrastructure</li>
 * </ul>
 *
 * <p>The platform-http REST configuration is provided by CamelBee itself (see CamelBeeRoutes),
 * so this builder only declares the {@code rest(...)} entry points.
 */
public class MusicianRoute extends RouteBuilder {

  private static final String MUSICIAN_PROCESSOR_ROUTE = "direct:musicianProcessor";

  /**
   * Internal queue used as a pollable endpoint: fed by the wireTap, drained by {@code pollEnrich}
   * and {@code poll}. Gives the topology a non-direct internal endpoint without a real broker.
   */
  private static final String SOUTHBOUND_QUEUE = "seda:southbound";

  @Override
  public void configure() {

    // Dead-letter channel with redelivery. Populates CamelRoute.errorHandler for every route in
    // this builder, and makes failed sends retry - both are traced by CamelBee.
    errorHandler(deadLetterChannel("direct:deadLetter")
        .maximumRedeliveries(2)
        .redeliveryDelay(200));

    // Camel inlines a rest verb into the direct: route it calls by default, which collapses the
    // REST entry point into the route behind it and hides that hop from the topology graph.
    restConfiguration().inlineRoutes(false);

    rest("/api")
        .post("/musicians").id("postMusician").description("Save a musician")
        .type(Musician.class).to("direct:postMusician")
        .get("/musicians").id("getMusician").description("Get a musician")
        .to("direct:getMusician")
        .get("/health").id("healthCheck").description("Target of the self-directed http producer")
        .to("direct:health");

    from("direct:postMusician").routeId("postMusicianRoute")
        .description("REST entry point for creating a musician")
        .to(MUSICIAN_PROCESSOR_ROUTE);

    from("direct:getMusician").routeId("getMusicianRoute")
        .description("REST entry point for reading a musician")
        .setBody(constant(new Musician("Miles", "Trumpet")))
        .to(MUSICIAN_PROCESSOR_ROUTE);

    // Deliberately does NOT feed the processing pipeline: it is the target of the http producer
    // below, and routing it onward would make the application call itself in a loop.
    from("direct:health").routeId("healthRoute")
        .description("Static health payload served over http")
        .setBody(constant("{\"status\":\"UP\"}"));

    // period/delay are properties so a test run can silence this route - its traffic would
    // otherwise keep arriving while an assertion is inspecting the traced messages
    from("timer://foo?period={{camelbee.sample.timer-period}}&delay={{camelbee.sample.timer-delay}}")
        .routeId("timerRoute")
        .description("Periodic traffic generator so the UI has something to show")
        .setBody(constant("timerTestMessage"))
        .to(MUSICIAN_PROCESSOR_ROUTE);

    from("file://inputdir?delete=true").routeId("fileListenerRoute")
        .description("Picks up files dropped into inputdir")
        .to(MUSICIAN_PROCESSOR_ROUTE);

    from(MUSICIAN_PROCESSOR_ROUTE).routeId("musicianProcessorRoute")
        .description("Central pipeline: fan-out, enrichment, dynamic routing and error handling")
        .setProperty(Constants.ORIGINAL_BODY, body())
        .setProperty("mockTarget", constant("D"))
        .bean(MusicianProcessor.class, "process")
        // query string on a direct: target - must still resolve to from("direct:invokeHttp")
        .to("direct:invokeHttp?block=true").id("httpBridgeEndpoint")
        .description("Calls the remote http endpoint")
        .wireTap("direct:invokeWireTap")
        .multicast().parallelProcessing()
        .to("direct:invokeMockA")
        .to("direct:invokeMockB")
        .end()
        .enrich("direct:invokeEnrich")
        .enrich().constant("direct:invokeEnrichDynamic")
        .recipientList().constant("direct:invokeMockA,direct:invokeMockB,direct:invokeFile")
        .routingSlip().constant("direct:invokeMockC,direct:invokeMockD")
        .dynamicRouter(method(this, "computeEndpoint"))
        .removeHeaders("*")
        // static toD - resolves to a route node; expression toD - stays a dynamic endpoint
        .toD("direct:invokeSeda")
        .toD("direct:invokeMock${exchangeProperty.mockTarget}")
        .to("direct:invokeFlaky").id("flakyBridgeEndpoint")
        .pollEnrich(SOUTHBOUND_QUEUE, 200, (original, resource) -> original)
        .to("direct:invokeAlwaysFails")
        .to("log:result");

    // Remote producer: calls this application's own REST port, so the topology gets a real
    // http hop (and a real remote exchange) with nothing external to install. The {{...}}
    // placeholder is resolved by CamelBee when it renders the topology.
    from("direct:invokeHttp").routeId("invokeHttpRoute")
        .description("Remote http hop, pointed back at this application")
        .removeHeaders("*")
        .setBody(constant(""))
        .setHeader("CamelHttpMethod", constant("GET"))
        .to("{{camelbee.sample.self-url}}/api/health?bridgeEndpoint=true").id("httpEndpoint")
        .convertBodyTo(String.class);

    from("direct:invokeWireTap").routeId("invokeWireTapRoute")
        .description("Fire-and-forget copy, parked on the internal queue")
        .to("log:wiretap")
        .to(SOUTHBOUND_QUEUE).id("sedaProducerEndpoint");

    from("direct:invokeEnrich").routeId("invokeEnrichRoute")
        .setBody(constant("enrichedData"))
        .to("mock:enrich").id("enrichEndpoint");

    from("direct:invokeEnrichDynamic").routeId("invokeEnrichDynamicRoute")
        .description("Enrichment target addressed through an expression")
        .setBody(constant("dynamicallyEnrichedData"))
        .to("mock:enrichDynamic").id("enrichDynamicEndpoint");

    from("direct:invokeSeda").routeId("invokeSedaRoute")
        .description("Drains the internal queue with poll()")
        .poll(SOUTHBOUND_QUEUE, 200)
        .to("log:polled");

    from("direct:invokeFile").routeId("invokeFileRoute")
        .setBody(exchangeProperty(Constants.ORIGINAL_BODY))
        .convertBodyTo(String.class)
        .to("file://outputdir").id("fileEndpoint");

    // Transient failure: the caller's dead-letter channel redelivers this send twice before it
    // succeeds, so one exchange produces three request/response pairs on the same edge.
    from("direct:invokeFlaky").routeId("invokeFlakyRoute")
        .description("Succeeds on the third attempt - exercises redelivery tracing")
        .to("direct:flakyTarget?block=true").id("flakyEndpoint");

    from("direct:flakyTarget").routeId("flakyTargetRoute")
        // no error handler here, so the failure propagates to the caller and IT redelivers the send
        .errorHandler(noErrorHandler())
        .bean(FlakyProcessor.class, "maybeFail")
        .to("mock:flaky").id("flakyMockEndpoint");

    // Permanent failure, caught locally: produces an ERROR_RESPONSE trace without failing the
    // pipeline and without engaging the dead-letter channel.
    from("direct:invokeAlwaysFails").routeId("invokeAlwaysFailsRoute")
        .description("Always fails, recovers locally - produces a traced error response")
        .doTry()
        .to("direct:boom").id("boomEndpoint")
        .doCatch(Exception.class)
        .setBody(constant("recoveredFromFailure"))
        .endDoTry();

    from("direct:boom").routeId("boomRoute")
        .errorHandler(noErrorHandler())
        .throwException(new IllegalStateException("simulated permanent failure"));

    from("direct:deadLetter").routeId("deadLetterRoute")
        .description("Dead-letter channel target")
        .to("log:deadLetter?level=WARN")
        .to("mock:dlq").id("dlqEndpoint");

    from("direct:invokeMockA").routeId("invokeMockARoute")
        .setBody(constant("invokedMockABody")).to("mock:A").id("mockAEndpoint");

    from("direct:invokeMockB").routeId("invokeMockBRoute")
        .setBody(constant("invokedMockBBody")).to("mock:B").id("mockBEndpoint");

    from("direct:invokeMockC").routeId("invokeMockCRoute")
        .setBody(constant("invokedMockCBody")).to("mock:C").id("mockCEndpoint");

    from("direct:invokeMockD").routeId("invokeMockDRoute")
        .setBody(constant("invokedMockDBody")).to("mock:D").id("mockDEndpoint");
  }

  /**
   * Dynamic router target computation - routes twice then terminates.
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
    } else if (invocationCount == 2) {
      return "mock:E";
    }
    return null;
  }
}
