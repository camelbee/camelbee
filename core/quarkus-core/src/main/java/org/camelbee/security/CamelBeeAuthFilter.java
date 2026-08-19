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

package org.camelbee.security;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Guards the CamelBee endpoints on Quarkus.
 *
 * <p><strong>A Vert.x filter rather than a JAX-RS one.</strong> The REST API is JAX-RS, but the UI is
 * static content under {@code META-INF/resources/camelbee} served by Quarkus itself - a
 * {@code ContainerRequestFilter} never sees it. Since the whole point is that unauthenticated
 * callers get nothing, the guard has to sit where both paths pass, which is the Vert.x layer.
 *
 * <p>Registered through the {@link Filters} CDI event, which needs no extension beyond
 * {@code quarkus-vertx-http} - already on the classpath transitively via RESTEasy.
 */
@ApplicationScoped
public class CamelBeeAuthFilter {

  /** Runs before the handlers it protects; higher priority means earlier in Quarkus filters. */
  private static final int PRIORITY = 400;

  @Inject
  AuthService authService;

  /**
   * Registers the guard.
   *
   * @param filters the Quarkus filter registry.
   */
  public void register(@Observes Filters filters) {
    filters.register(this::filter, PRIORITY);
  }

  void filter(RoutingContext ctx) {
    if (!authService.isEnabled() || !AuthPaths.isProtected(ctx.request().path())) {
      ctx.next();
      return;
    }

    final String token = AuthPaths.bearerToken(ctx.request().getHeader(AuthPaths.AUTHORIZATION_HEADER));

    authService.verifyAndRefresh(token).ifPresentOrElse(
        refreshed -> {
          ctx.response().putHeader(AuthPaths.REFRESHED_TOKEN_HEADER, refreshed);
          ctx.next();
        },
        () -> ctx.response().setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"unauthorized\"}"));
  }
}
