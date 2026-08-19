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

package org.camelbee.debugger.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.camelbee.security.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Mirrors {@code quarkus-core}'s test of the same name, so the two runtimes cannot drift. */
class AuthControllerTest {

  private AuthController controller;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(true, "camelbee", "s3cret", 120_000);
    controller = new AuthController(authService);
  }

  @Test
  @DisplayName("correct credentials yield a token the guard accepts")
  void loginIssuesAUsableToken() {
    ResponseEntity<Map<String, String>> response = controller.login(Map.of("username", "camelbee", "password", "s3cret"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    String token = response.getBody().get("token");
    assertThat(authService.verifyAndRefresh(token))
        .as("a token handed to the browser must be one the guard will accept")
        .isPresent();
  }

  @Test
  @DisplayName("a wrong password is refused")
  void loginRejectsWrongPassword() {
    assertThat(controller.login(Map.of("username", "camelbee", "password", "wrong")).getStatusCode().value())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("an unknown user is refused identically, so neither can be probed for")
  void loginRejectsUnknownUser() {
    assertThat(controller.login(Map.of("username", "nobody", "password", "s3cret")).getStatusCode().value())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("a missing body is a failed login, not a crash")
  void loginRejectsMissingBody() {
    assertThat(controller.login(null).getStatusCode().value()).isEqualTo(401);
  }

  @Test
  @DisplayName("status reports whether a login is needed")
  void statusReportsAuthState() {
    assertThat(controller.status().getBody()).isEqualTo(Map.of("authEnabled", true));

    assertThat(new AuthController(AuthService.disabled()).status().getBody())
        .isEqualTo(Map.of("authEnabled", false));
  }

  @Test
  @DisplayName("with authentication off, login succeeds with an empty token")
  void loginIsANoOpWhenDisabled() {
    ResponseEntity<Map<String, String>> response = new AuthController(AuthService.disabled()).login(Map.of("username", "x", "password", "y"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(Map.of("token", ""));
  }
}
