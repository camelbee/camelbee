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

import java.util.Map;
import org.camelbee.security.AuthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exchanges credentials for a session token, and tells the UI whether it needs to ask for them.
 *
 * <p>Both endpoints are deliberately outside the guard: a caller has no token yet, and the UI must
 * be able to ask whether authentication is on before it decides to show a login form. Neither
 * discloses anything an unauthenticated caller would not learn from the first 401.
 */
@RestController
@ConditionalOnProperty(value = "camelbee.context-enabled", havingValue = "true")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Whether the UI needs to show a login form.
   *
   * @return the authentication state.
   */
  @GetMapping(value = "/camelbee/auth/status", produces = "application/json")
  public ResponseEntity<Map<String, Object>> status() {
    return ResponseEntity.ok(Map.of("authEnabled", authService.isEnabled()));
  }

  /**
   * Exchanges credentials for a token.
   *
   * @param credentials the supplied username and password.
   * @return a token, or 401.
   */
  @PostMapping(value = "/camelbee/auth/login", produces = "application/json", consumes = "application/json")
  public ResponseEntity<Map<String, String>> login(@RequestBody(required = false) Map<String, String> credentials) {
    if (!authService.isEnabled()) {
      return ResponseEntity.ok(Map.of("token", ""));
    }

    final String username = credentials == null ? null : credentials.get("username");
    final String password = credentials == null ? null : credentials.get("password");

    if (!authService.authenticate(username, password)) {
      // The same response for an unknown user and a wrong password, so neither can be probed for.
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid credentials"));
    }

    return ResponseEntity.ok(Map.of("token", authService.issueToken()));
  }
}
