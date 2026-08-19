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

package io.camelbee.standalone.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camelbee.standalone.example.routes.MusicianRoute;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.camel.CamelContext;
import org.apache.camel.main.Main;
import org.camelbee.CamelBee;

/**
 * Boots the sample application once for the integration tests and exposes small helpers for talking
 * to it over HTTP.
 *
 * <p>Two things are deliberately different from a normal {@code exec:java} run:
 *
 * <ul>
 * <li><b>Ephemeral ports.</b> Both the application server and the management server (which hosts
 * the CamelBee API and UI) bind to a free port picked at startup, so the suite never collides
 * with a locally running sample or with a parallel build.</li>
 * <li><b>No background consumers.</b> The timer and file routes are stopped right after startup.
 * They would otherwise keep injecting exchanges into the tracer while a test is asserting on
 * it, which is the single biggest source of flakiness in a test like this.</li>
 * </ul>
 */
abstract class CamelBeeApplicationSupport {

  protected static final ObjectMapper MAPPER = new ObjectMapper();

  /** Routes that produce traffic on their own and would pollute the traced message list. */
  private static final String[] BACKGROUND_ROUTES = {"timerRoute", "fileListenerRoute"};

  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private static Main main;

  /** Base URL of the application's own REST API. */
  protected static String appUrl;

  /** Base URL of the CamelBee API on the management server. */
  protected static String camelBeeUrl;

  protected static void startApplication() throws Exception {
    int appPort = freePort();
    int managementPort = freePort();

    appUrl = "http://localhost:" + appPort;
    camelBeeUrl = "http://localhost:" + managementPort + "/camelbee";

    main = new Main();
    main.configure().addRoutesBuilder(new MusicianRoute());
    main.addOverrideProperty("camel.server.port", String.valueOf(appPort));
    main.addOverrideProperty("camel.management.port", String.valueOf(managementPort));
    // the http producer calls the application back on its own (now ephemeral) port
    main.addOverrideProperty("camelbee.sample.self-url", appUrl);
    CamelBee.register(main);

    main.start();

    CamelContext context = main.getCamelContext();
    for (String routeId : BACKGROUND_ROUTES) {
      context.getRouteController().stopRoute(routeId);
    }

    awaitReady();
  }

  protected static void stopApplication() {
    if (main != null) {
      main.stop();
      main = null;
    }
  }

  /**
   * Blocks until the CamelBee API answers, so no test races the management server's startup.
   */
  private static void awaitReady() throws Exception {
    Exception last = null;
    for (int attempt = 0; attempt < 100; attempt++) {
      try {
        if (get("/routes").statusCode() == 200) {
          return;
        }
      } catch (IOException e) {
        last = e;
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException("CamelBee API never became available on " + camelBeeUrl, last);
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  /* ---------------------------------------------------------------- */
  /*  CamelBee API                                                     */
  /* ---------------------------------------------------------------- */

  protected static JsonNode topology() throws Exception {
    HttpResponse<String> response = get("/routes");
    assertThat(response.statusCode()).isEqualTo(200);
    return MAPPER.readTree(response.body());
  }

  protected static void setTracer(String status) throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder()
        .uri(URI.create(camelBeeUrl + "/tracer/status"))
        .header("Content-Type", "text/plain")
        .POST(HttpRequest.BodyPublishers.ofString(status))
        .build());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  protected static void clearMessages() throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder()
        .uri(URI.create(camelBeeUrl + "/messages"))
        .DELETE()
        .build());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  protected static JsonNode messages() throws Exception {
    HttpResponse<String> response = get("/messages?index=0");
    assertThat(response.statusCode()).isEqualTo(200);
    return MAPPER.readTree(response.body());
  }

  /**
   * Polls the traced messages until the pipeline's last hop has been traced <em>and</em> the message
   * count has stopped growing, so assertions never run against a half-finished pipeline.
   *
   * <p>Both conditions are needed. Async steps (wireTap, parallel multicast) mean "the HTTP call
   * returned" is not the same as "everything has been traced", so a completion marker alone is not
   * enough; and the 200ms redelivery delay on the flaky route means the count can sit still for
   * longer than a naive quiet period, so stability alone is not enough either. The quiet window is
   * deliberately wider than the redelivery delay.
   *
   * @return the settled message payload.
   */
  protected static JsonNode awaitSettledMessages() throws Exception {
    int previous = -1;
    int stableRounds = 0;
    JsonNode payload = null;

    for (int attempt = 0; attempt < 120; attempt++) {
      payload = messages();
      int count = payload.get("messages").size();
      stableRounds = count == previous ? stableRounds + 1 : 0;
      previous = count;
      if (stableRounds >= 5 && pipelineCompleted(payload)) {
        return payload;
      }
      Thread.sleep(150);
    }
    throw new IllegalStateException("traced messages never settled, last count=" + previous);
  }

  /** True once the final hop of the main pipeline ({@code to("log:result")}) has responded. */
  private static boolean pipelineCompleted(JsonNode payload) {
    for (JsonNode message : payload.get("messages")) {
      JsonNode endpoint = message.get("endpoint");
      if (endpoint != null && !endpoint.isNull() && "log://result".equals(endpoint.asText())
          && "RESPONSE".equals(message.get("messageType").asText())) {
        return true;
      }
    }
    return false;
  }

  /* ---------------------------------------------------------------- */
  /*  Application API                                                  */
  /* ---------------------------------------------------------------- */

  protected static HttpResponse<String> postMusician(String json) throws Exception {
    return send(HttpRequest.newBuilder()
        .uri(URI.create(appUrl + "/api/musicians"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build());
  }

  private static HttpResponse<String> get(String path) throws Exception {
    return send(HttpRequest.newBuilder().uri(URI.create(camelBeeUrl + path)).GET().build());
  }

  private static HttpResponse<String> send(HttpRequest request) throws Exception {
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
