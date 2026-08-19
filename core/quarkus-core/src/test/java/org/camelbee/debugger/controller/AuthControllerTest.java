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

import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.camelbee.security.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mirrors {@code springboot-core}'s test of the same name, so the two runtimes cannot drift. */
class AuthControllerTest {

  private AuthController controller;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(true, "camelbee", "s3cret", 120_000);
    controller = new AuthController();
    controller.authService = authService;
  }

  @Test
  @DisplayName("correct credentials yield a token the guard accepts")
  void loginIssuesAUsableToken() {
    Response response = controller.login(Map.of("username", "camelbee", "password", "s3cret"));

    assertThat(response.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    String token = ((Map<String, String>) response.getEntity()).get("token");
    assertThat(authService.verifyAndRefresh(token))
        .as("a token handed to the browser must be one the guard will accept")
        .isPresent();
  }

  @Test
  @DisplayName("a wrong password is refused")
  void loginRejectsWrongPassword() {
    assertThat(controller.login(Map.of("username", "camelbee", "password", "wrong")).getStatus())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("an unknown user is refused identically, so neither can be probed for")
  void loginRejectsUnknownUser() {
    assertThat(controller.login(Map.of("username", "nobody", "password", "s3cret")).getStatus())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("a missing body is a failed login, not a crash")
  void loginRejectsMissingBody() {
    assertThat(controller.login(null).getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("status reports whether a login is needed")
  void statusReportsAuthState() {
    assertThat(controller.status().getEntity()).isEqualTo(Map.of("authEnabled", true));

    controller.authService = AuthService.disabled();
    assertThat(controller.status().getEntity()).isEqualTo(Map.of("authEnabled", false));
  }

  @Test
  @DisplayName("with authentication off, login succeeds with an empty token")
  void loginIsANoOpWhenDisabled() {
    controller.authService = AuthService.disabled();

    Response response = controller.login(Map.of("username", "x", "password", "y"));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(Map.of("token", ""));
  }
}
