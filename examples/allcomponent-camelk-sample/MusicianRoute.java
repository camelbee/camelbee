// camel-k: dependency=mvn:io.camelbee:camelbee-quarkus-core-camelk:3.3.1
// camel-k: dependency=camel:http
// camel-k: dependency=camel:mock
// camel-k: dependency=camel:seda
// camel-k: dependency=camel:file
// camel-k: dependency=camel:timer
// camel-k: dependency=camel:direct
// camel-k: dependency=camel:log
// camel-k: dependency=camel:bean
// camel-k: dependency=mvn:io.quarkus:quarkus-micrometer-registry-prometheus
// camel-k: build-property=camelbee.context-enabled=true
// camel-k: build-property=camelbee.tracer-enabled=true
// camel-k: build-property=quarkus.smallrye-health.root-path=/health
// camel-k: build-property=quarkus.micrometer.export.prometheus.path=/metrics
// camel-k: property=camelbee.tracer-enabled=true
// camel-k: property=camelbee.auth-enabled=false
// camel-k: property=camelbee.masking-enabled=true
// camel-k: property=camelbee.tracer-body-enabled=true
// camel-k: property=camelbee.sample.self-url=http://localhost:8080
// camel-k: property=camelbee.sample.timer-period=10000
// camel-k: property=camelbee.sample.timer-delay=5000
// camel-k: trait=service.enabled=true

import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.builder.RouteBuilder;
import org.camelbee.config.CamelBeeRouteConfigurer;

/**
 * CamelBee on Camel K - EIP-rich, infra-free demo topology.
 *
 * <p>Camel K runs integrations on the Camel Quarkus runtime, but pins an older Camel than CamelBee's
 * main build, so the modeline above uses camelbee-quarkus-core-camelk - the same sources built
 * against Camel K's baseline (see core/quarkus-core/pom-camelk.xml and this sample's README). Its
 * CDI beans are auto-discovered because the jar ships a Jandex index, and it brings the REST/Jackson
 * stack transitively. The service trait exposes the HTTP port.
 *
 * <p>This mirrors the shape of the allcomponent-standalone-sample (multicast, wireTap, both enrich
 * forms, poll, pollEnrich, recipientList, routingSlip, dynamicRouter, toD, bean, timer, file, seda,
 * http, REST, a dead-letter channel with redelivery and a caught failure) but using only components
 * that need no external brokers or databases, so the integration runs on a cluster with nothing else
 * installed. The point is to give the CamelBee UI a meaningful graph and real traced traffic.
 *
 * <p>Everything lives in this single file - Camel K compiles one public class whose name matches the
 * file name - so the helper beans of the other samples are methods on this route builder instead,
 * invoked with {@code bean(this, "...")}.
 *
 * <p>Every component is declared in the modeline rather than left to Camel K's auto-detection.
 * Detection reads URIs at the from(...)/to(...) call site, so the ones built from the constants
 * below (SOUTHBOUND_QUEUE, INPUT_DIR, OUTPUT_DIR) are invisible to it - and a missed component only
 * surfaces at runtime, as NoSuchEndpointException, long after the kit has built successfully.
 */
public class MusicianRoute extends RouteBuilder {

    private static final String MUSICIAN_PROCESSOR_ROUTE = "direct:musicianProcessor";

    /** Body of the incoming exchange, kept so later steps can restore it. */
    private static final String ORIGINAL_BODY = "originalBody";

    /** Attempt counter property, kept on the exchange so it survives redelivery. */
    private static final String ATTEMPTS_PROPERTY = "flakyAttempts";

    /** How many attempts fail before the flaky call succeeds. */
    private static final int FAILING_ATTEMPTS = 2;

    /**
     * Internal queue used as a pollable endpoint: fed by the wireTap, drained by {@code pollEnrich}
     * and {@code poll}. Gives the topology a non-direct internal endpoint without a real broker.
     */
    private static final String SOUTHBOUND_QUEUE = "seda:southbound";

    /** Writable inside the integration container, unlike the working directory. */
    private static final String INPUT_DIR = "file:/tmp/camelbee/inputdir?delete=true&autoCreate=true";

    private static final String OUTPUT_DIR = "file:/tmp/camelbee/outputdir?autoCreate=true";

    @Override
    public void configure() {

        // On Quarkus the node-id and poll intercept strategies are not installed automatically -
        // the application's route builders opt in. Without this, endpoint ids and the poll() /
        // pollEnrich() hops are missing from the traced messages. Looked up from the registry
        // rather than injected because Camel K compiles this file outside CDI.
        CamelBeeRouteConfigurer camelBeeRouteConfigurer =
            getContext().getRegistry().findSingleByType(CamelBeeRouteConfigurer.class);
        if (camelBeeRouteConfigurer != null) {
            camelBeeRouteConfigurer.configureRoute(this);
        }

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
            .to("direct:postMusician")
            .get("/musicians").id("getMusician").description("Get a musician")
            .to("direct:getMusician")
            .get("/health").id("healthCheck").description("Target of the self-directed http producer")
            .to("direct:health");

        from("direct:postMusician").routeId("postMusicianRoute")
            .description("REST entry point for creating a musician")
            .to(MUSICIAN_PROCESSOR_ROUTE);

        from("direct:getMusician").routeId("getMusicianRoute")
            .description("REST entry point for reading a musician")
            .setBody(constant("{\"name\":\"Miles\",\"instrument\":\"Trumpet\"}"))
            .to(MUSICIAN_PROCESSOR_ROUTE);

        // Deliberately does NOT feed the processing pipeline: it is the target of the http producer
        // below, and routing it onward would make the integration call itself in a loop.
        from("direct:health").routeId("healthRoute")
            .description("Static health payload served over http")
            .setBody(constant("{\"status\":\"UP\"}"));

        // period/delay are properties so they can be tuned per run with kamel run -p
        from("timer://foo?period={{camelbee.sample.timer-period}}&delay={{camelbee.sample.timer-delay}}")
            .routeId("timerRoute")
            .description("Periodic traffic generator so the UI has something to show")
            .setBody(constant("timerTestMessage"))
            .to(MUSICIAN_PROCESSOR_ROUTE);

        from(INPUT_DIR).routeId("fileListenerRoute")
            .description("Picks up files dropped into the input directory")
            .to(MUSICIAN_PROCESSOR_ROUTE);

        from(MUSICIAN_PROCESSOR_ROUTE).routeId("musicianProcessorRoute")
            .description("Central pipeline: fan-out, enrichment, dynamic routing and error handling")
            .setProperty(ORIGINAL_BODY, body())
            .setProperty("mockTarget", constant("D"))
            .bean(this, "processMusician")
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

        // Remote producer: calls this integration's own HTTP port, so the topology gets a real http
        // hop (and a real remote exchange) with nothing external to install. The {{...}} placeholder
        // is resolved by CamelBee when it renders the topology.
        from("direct:invokeHttp").routeId("invokeHttpRoute")
            .description("Remote http hop, pointed back at this integration")
            .removeHeaders("*")
            .setBody(constant(""))
            .setHeader(Exchange.HTTP_METHOD, constant("GET"))
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
            .setBody(exchangeProperty(ORIGINAL_BODY))
            .convertBodyTo(String.class)
            .to(OUTPUT_DIR).id("fileEndpoint");

        // Transient failure: the caller's dead-letter channel redelivers this send twice before it
        // succeeds, so one exchange produces three request/response pairs on the same edge.
        from("direct:invokeFlaky").routeId("invokeFlakyRoute")
            .description("Succeeds on the third attempt - exercises redelivery tracing")
            .to("direct:flakyTarget?block=true").id("flakyEndpoint");

        from("direct:flakyTarget").routeId("flakyTargetRoute")
            // no error handler here, so the failure propagates to the caller and IT redelivers the send
            .errorHandler(noErrorHandler())
            .bean(this, "maybeFail")
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
     * Demo bean invoked via the Camel bean component - kept trivial so the demo needs no infra.
     *
     * @param body the incoming body.
     * @return the (unchanged) body.
     */
    public Object processMusician(Object body) {
        return body;
    }

    /**
     * Throws on the first two invocations for a given exchange, then passes. The attempt counter
     * lives on the exchange, which survives redelivery, so every top-level exchange fails exactly
     * twice and then succeeds.
     *
     * @param exchange the current exchange.
     */
    public void maybeFail(Exchange exchange) {
        int attempts = exchange.getProperty(ATTEMPTS_PROPERTY, 0, Integer.class) + 1;
        exchange.setProperty(ATTEMPTS_PROPERTY, attempts);

        if (attempts <= FAILING_ATTEMPTS) {
            throw new IllegalStateException("simulated transient failure, attempt " + attempts);
        }
    }

    /**
     * Dynamic router target computation - routes twice then terminates.
     *
     * @param properties the exchange properties.
     * @return the next endpoint, or null to stop.
     */
    public String computeEndpoint(@ExchangeProperties Map<String, Object> properties) {
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
