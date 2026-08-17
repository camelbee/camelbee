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

package org.camelbee.http;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Serves the UI's {@code index.html} for the client-side routes, so a reload does not 404.
 *
 * <p>Quarkus serves {@code META-INF/resources/camelbee} as static files. {@code /camelbee/settings}
 * is not one - it exists only in the browser's router - so pressing F5 there, or opening a shared
 * link to it, used to answer "Resource not found" for a page that was on screen a moment earlier.
 *
 * <p>Implemented as a reroute rather than by writing the file: rerouting restarts Vert.x routing at
 * {@code /camelbee/index.html}, so the static handler answers it with the correct content type and
 * caching headers, and nothing here has to know where the bundle lives.
 *
 * <p>Registered at a lower priority than {@link org.camelbee.security.CamelBeeAuthFilter}, so the
 * guard still runs first. That ordering does not currently matter - the shell is public by necessity,
 * since a browser has to load the application before it can show a login form - but it keeps the
 * guard in front of everything if the public paths are ever narrowed.
 */
@ApplicationScoped
public class CamelBeeUiRoutingFilter {

  /** Below the auth filter's 400, so authentication decides first. */
  private static final int PRIORITY = 300;

  /**
   * Registers the fallback.
   *
   * @param filters the Quarkus filter registry.
   */
  public void register(@Observes Filters filters) {
    filters.register(this::filter, PRIORITY);
  }

  void filter(RoutingContext ctx) {
    if (UiPaths.isClientRoute(ctx.request().path())
        && UiPaths.wantsHtml(ctx.request().getHeader("Accept"))) {
      ctx.reroute(UiPaths.INDEX);
      return;
    }
    ctx.next();
  }
}
