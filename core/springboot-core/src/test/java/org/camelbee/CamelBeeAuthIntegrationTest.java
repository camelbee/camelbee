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

package org.camelbee;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the guard is actually in the request path on Spring Boot.
 *
 * <p>{@code AuthServiceTest} proves the token logic in isolation, which is the easy half. The half
 * that actually protects anything is whether the filter is reached at all - a filter bean that is
 * never registered, or whose path matching does not fire, verifies nothing while every other test in
 * the suite keeps passing. So this drives real HTTP against a running application and asserts on the
 * status code.
 *
 * <p>Kept separate from {@code CamelBeeHttpApiIntegrationTest}, which runs with authentication off:
 * threading a login through all of its assertions would obscure what each is actually testing.
 */
@SpringBootTest(
    // Named explicitly rather than discovered: CamelBeeHttpApiIntegrationTest is also a
    // @SpringBootApplication in this package, and Spring refuses to choose between two.
    classes = CamelBeeAuthIntegrationTest.AuthTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "camelbee.context-enabled=true",
    "camelbee.tracer-enabled=true",
    "camelbee.auth-enabled=true",
    "camelbee.username=camelbee",
    "camelbee.password=s3cret",
    "spring.main.allow-bean-definition-overriding=true",
})
class CamelBeeAuthIntegrationTest {

  /**
   * Scans {@code org.camelbee} exactly as an application would, which is the point: the filter has
   * to be picked up by an ordinary component scan, not wired in by the test.
   */
  @SpringBootApplication
  static class AuthTestApplication {
  }

  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @LocalServerPort
  private int port;

  private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + path)).GET();
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> login(String username, String password) throws IOException, InterruptedException {
    return HTTP.send(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/camelbee/auth/login"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("the topology is not readable without a token")
  void topologyRequiresAToken() throws Exception {
    assertThat(get("/camelbee/routes", null).statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("traced messages are not readable without a token")
  void messagesRequireAToken() throws Exception {
    assertThat(get("/camelbee/messages", null).statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("tracing cannot be switched on without a token - the API is not read-only")
  void tracerCannotBeArmedWithoutAToken() throws Exception {
    HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/camelbee/tracer/status"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("\"ACTIVE\""))
        .build(), HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("an unauthenticated caller must not be able to start capturing traffic")
        .isEqualTo(401);
  }

  @Test
  @DisplayName("a wrong password does not yield a token")
  void wrongPasswordIsRejected() throws Exception {
    assertThat(login("camelbee", "wrong").statusCode()).isEqualTo(401);
    assertThat(login("nobody", "s3cret").statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("logging in yields a token that opens the API")
  void loginGrantsAccess() throws Exception {
    HttpResponse<String> login = login("camelbee", "s3cret");
    assertThat(login.statusCode()).isEqualTo(200);

    String token = MAPPER.readTree(login.body()).path("token").asText();
    assertThat(token).isNotEmpty();

    HttpResponse<String> routes = get("/camelbee/routes", token);
    assertThat(routes.statusCode()).isEqualTo(200);
    assertThat(routes.headers().firstValue("X-CamelBee-Token"))
        .as("the rolling token keeps an active session alive")
        .isPresent();
  }

  @Test
  @DisplayName("a garbage token is refused")
  void garbageTokenIsRefused() throws Exception {
    assertThat(get("/camelbee/routes", "not-a-real-token").statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("auth status is readable without a token, so the UI knows to show a login form")
  void authStatusIsPublic() throws Exception {
    HttpResponse<String> response = get("/camelbee/auth/status", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(MAPPER.readTree(response.body()).path("authEnabled").asBoolean()).isTrue();
  }
}
