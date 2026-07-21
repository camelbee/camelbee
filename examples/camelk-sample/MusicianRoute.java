// camel-k: dependency=mvn:io.camelbee:camelbee-quarkus-core:3.3.1
// camel-k: dependency=mvn:org.apache.camel.quarkus:camel-quarkus-rest
// camel-k: dependency=mvn:org.apache.camel.quarkus:camel-quarkus-jackson
// camel-k: dependency=mvn:io.quarkus:quarkus-resteasy-jackson
// camel-k: build-property=camelbee.context-enabled=true
// camel-k: build-property=camelbee.tracer-enabled=true
// camel-k: property=camelbee.tracer-enabled=true
// camel-k: trait=service.enabled=true

import org.apache.camel.builder.RouteBuilder;

/**
 * CamelBee on Camel K.
 *
 * Camel K runs integrations on the Camel Quarkus runtime, so CamelBee's Quarkus core works as-is.
 * The modeline above adds the camelbee-quarkus-core dependency (its CDI beans are auto-discovered
 * because the jar ships a Jandex index) together with the REST extensions its endpoints need, and
 * exposes the HTTP port via the service trait.
 *
 * Camel K auto-detects the timer/direct/log components from the route URIs below, so they do not
 * need to be declared.
 */
public class MusicianRoute extends RouteBuilder {

    @Override
    public void configure() {

        from("timer:tick?period=5000").routeId("timerRoute")
            .setBody(constant("timerTestMessage"))
            .to("direct:process");

        from("direct:process").routeId("processRoute")
            .multicast()
                .to("direct:invokeMockA")
                .to("direct:invokeMockB")
            .end()
            .to("log:result");

        from("direct:invokeMockA").routeId("invokeMockARoute")
            .setBody(constant("invokedMockABody"))
            .to("log:a");

        from("direct:invokeMockB").routeId("invokeMockBRoute")
            .setBody(constant("invokedMockBBody"))
            .to("log:b");
    }
}
