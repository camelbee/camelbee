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

package io.camelbee.coreonly.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the core-as-a-dependency path end to end on the OLDEST supported stack: Spring Boot 3.3.13
 * with Camel 4.8.0, against a camelbee-springboot-core compiled for Spring Boot 4.1 / Camel 4.22.
 * A binary-incompatibility in that gap shows up here as a failure to start.
 *
 * <p>This is the only coverage of README Option 1 for Spring Boot - allcomponent-springboot-sample
 * inherits from the starter, so it cannot catch a regression in this path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    // keep the timer from injecting exchanges while assertions run
    "coreonly.timer-period=3600000",
    "coreonly.timer-delay=3600000"
})
class CoreOnlyWiringTest {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void servesTheTopologyFromTheCoreLibraryAlone() {
    ResponseEntity<String> response = restTemplate.getForEntity("/camelbee/routes", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    // both routes are discovered, so the notifier and context services are actually wired
    assertThat(response.getBody()).contains("pingRoute").contains("handleRoute");
  }

  @Test
  void servesTheEmbeddedUi() {
    // Accept: text/html on purpose - the UI routing controller forwards a client-side route to the
    // single page only for HTML requests, so that /camelbee/routes still reaches the REST endpoint.
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));

    ResponseEntity<String> response = restTemplate.exchange(
        "/camelbee/", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsIgnoringCase("<html");
  }
}
