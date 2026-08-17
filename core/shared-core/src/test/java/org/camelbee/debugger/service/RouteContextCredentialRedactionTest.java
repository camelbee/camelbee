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

package org.camelbee.debugger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end guard for the disclosure that motivated {@code UriSanitizer}.
 *
 * <p>{@code UriSanitizerTest} covers the redaction in isolation. This one covers the path that
 * actually leaked: a route that keeps its password in a property - the careful way to write it -
 * published through {@code GET /camelbee/routes}, where the topology resolves {@code {{...}}} so the
 * UI can show the real endpoint and, in doing so, resolves the password too.
 *
 * <p>Worth stating plainly because it is easy to under-rate: this happens with
 * {@code camelbee.tracer-enabled=false}. It is not part of tracing, and none of the tracer's
 * safeguards - masking of bodies and headers, the capture filter, auto-disarm - touch it.
 */
class RouteContextCredentialRedactionTest {

  private static final String SECRET = "hunter2-do-not-publish";

  private DefaultCamelContext camelContext;
  private RouteContextService service;

  @BeforeEach
  void setUp() throws Exception {
    camelContext = new DefaultCamelContext();

    Properties properties = new Properties();
    properties.setProperty("backend.password", SECRET);
    properties.setProperty("backend.host", "api.internal");
    camelContext.getPropertiesComponent().setInitialProperties(properties);

    camelContext.addRoutes(new RouteBuilder() {

      @Override
      public void configure() {
        from("direct:start").routeId("credentialRoute")
            /*
             The careful way to write it - the secret is not in the source. Sent with toD rather
             than to so the endpoint is resolved at runtime: the http component is not on this
             module's test classpath, and the topology reads the route MODEL either way, which is
             what is under test.
             */
            .toD("http://{{backend.host}}/api?authMethod=Basic&authPassword={{backend.password}}")
            // and the same secret reached through a recipientList, where a naive sanitizer both
            // leaks and destroys the sibling recipient
            .recipientList(constant("http://a/x?password=" + SECRET + ",http://b/y?foo=1"));
      }
    });
    camelContext.start();

    service = new RouteContextService(camelContext);
  }

  @AfterEach
  void tearDown() {
    camelContext.stop();
  }

  @Test
  @DisplayName("a password held in a property is not published by the topology")
  void passwordFromPropertyIsNotPublished() {
    String topology = renderTopology();

    assertThat(topology)
        .as("the resolved property value must not appear anywhere in the published topology")
        .doesNotContain(SECRET);
  }

  @Test
  @DisplayName("the host placeholder is still resolved - redaction must not disable the feature")
  void nonSecretPlaceholdersStillResolve() {
    // The whole point of resolving placeholders is that the UI shows the endpoint an application
    // really talks to. Redaction must remove the credential and nothing else.
    assertThat(renderTopology()).contains("api.internal");
  }

  @Test
  @DisplayName("ordinary endpoint configuration survives")
  void ordinaryConfigurationSurvives() {
    // authMethod contains "auth", which CamelBee's header defaults would match. Over-redacting here
    // would hide how the endpoint is configured while protecting nothing extra - Camel's URI keyword
    // list already covers the real secrets.
    assertThat(renderTopology()).contains("authMethod=Basic");
  }

  @Test
  @DisplayName("a recipientList keeps every recipient")
  void recipientListSurvivesRedaction() {
    // Camel's sanitizer alone deletes everything after the secret, taking the second recipient with
    // it. Losing topology is a correctness bug, not a cosmetic one.
    assertThat(renderTopology()).contains("http://b/y?foo=1");
  }

  private String renderTopology() {
    CamelRoute route = service.getCamelRoutes().stream()
        .filter(r -> "credentialRoute".equals(r.getId()))
        .findFirst()
        .orElseThrow();

    StringBuilder rendered = new StringBuilder(route.getInput());
    route.getOutputs().stream()
        .map(CamelRouteOutput::getDescription)
        .forEach(description -> rendered.append('\n').append(description));

    return rendered.toString();
  }
}
