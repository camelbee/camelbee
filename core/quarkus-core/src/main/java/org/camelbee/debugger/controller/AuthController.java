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

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.camelbee.security.AuthService;

/**
 * Exchanges credentials for a session token, and tells the UI whether it needs to ask for them.
 *
 * <p>Both endpoints are deliberately outside the guard: a caller has no token yet, and the UI must
 * be able to ask whether authentication is on before it decides to show a login form. Neither
 * discloses anything an unauthenticated caller would not learn from the first 401.
 */
@Path("/")
@IfBuildProperty(name = "camelbee.context-enabled", stringValue = "true")
public class AuthController {

  @Inject
  AuthService authService;

  /**
   * Whether the UI needs to show a login form.
   *
   * @return the authentication state.
   */
  @GET
  @Path("/camelbee/auth/status")
  @Produces("application/json")
  public Response status() {
    return Response.ok(Map.of("authEnabled", authService.isEnabled())).build();
  }

  /**
   * Exchanges credentials for a token.
   *
   * @param credentials the supplied username and password.
   * @return a token, or 401.
   */
  @POST
  @Path("/camelbee/auth/login")
  @Consumes("application/json")
  @Produces("application/json")
  public Response login(Map<String, String> credentials) {
    if (!authService.isEnabled()) {
      return Response.ok(Map.of("token", "")).build();
    }

    final String username = credentials == null ? null : credentials.get("username");
    final String password = credentials == null ? null : credentials.get("password");

    if (!authService.authenticate(username, password)) {
      // The same response for an unknown user and a wrong password, so neither can be probed for.
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "invalid credentials")).build();
    }

    return Response.ok(Map.of("token", authService.issueToken())).build();
  }
}
