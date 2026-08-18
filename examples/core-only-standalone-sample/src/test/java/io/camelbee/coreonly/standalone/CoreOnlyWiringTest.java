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

package io.camelbee.coreonly.standalone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.camel.main.Main;
import org.camelbee.CamelBee;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves the core-as-a-dependency path end to end on the OLDEST supported Camel: the application
 * declares only camelbee-standalone-core plus camel-platform-http-main, and CamelBee still serves
 * its API and UI and reports the real topology.
 *
 * <p>This is the only coverage of README Option 1 for standalone - the allcomponent-* samples all
 * inherit from a starter, so they cannot catch a regression in this path.
 */
class CoreOnlyWiringTest {

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private static Main main;
  private static String camelBeeUrl;

  @BeforeAll
  static void startApplication() throws Exception {
    int managementPort = freePort();
    camelBeeUrl = "http://localhost:" + managementPort + "/camelbee";

    main = new Main();
    main.configure().addRoutesBuilder(new PingRoute());
    main.addOverrideProperty("camel.management.port", String.valueOf(managementPort));
    // keep the timer from injecting exchanges while assertions run
    main.addOverrideProperty("coreonly.timer-period", "3600000");
    main.addOverrideProperty("coreonly.timer-delay", "3600000");
    CamelBee.register(main);
    main.start();
  }

  @AfterAll
  static void stopApplication() {
    if (main != null) {
      main.stop();
    }
  }

  @Test
  void servesTheTopologyFromTheCoreLibraryAlone() throws Exception {
    HttpResponse<String> response = get(camelBeeUrl + "/routes");

    assertThat(response.statusCode()).isEqualTo(200);
    // both routes are discovered, so the notifier and context services are actually wired
    assertThat(response.body()).contains("pingRoute").contains("handleRoute");
  }

  @Test
  void servesTheEmbeddedUi() throws Exception {
    HttpResponse<String> response = get(camelBeeUrl + "/");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).containsIgnoringCase("<html");
  }

  private static HttpResponse<String> get(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
