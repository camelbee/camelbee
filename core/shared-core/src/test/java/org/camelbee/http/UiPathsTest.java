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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The single-page-app fallback rule, which all three runtimes apply.
 *
 * <p>Both halves matter equally. Too narrow and a reload on {@code /camelbee/settings} 404s, which is
 * what this rule exists to fix. Too wide and a missing asset or a retired API path returns a page of
 * HTML with status 200 - a far more confusing failure, because the browser then reports a syntax
 * error in what it thought was JavaScript, or a JSON parse error, nowhere near the real cause.
 */
class UiPathsTest {

  @Nested
  @DisplayName("paths that must be served as the single page")
  class ClientRoutes {

    @ParameterizedTest
    @ValueSource(strings = {
        "/camelbee",          // bare base path
        "/camelbee/",         // the canonical UI URL
        "/camelbee/debugger",
        "/camelbee/metrics",
        "/camelbee/settings",
    })
    void areClientRoutes(String path) {
      assertThat(UiPaths.isClientRoute(path)).isTrue();
    }
  }

  @Nested
  @DisplayName("paths that must keep failing as they did")
  class NotClientRoutes {

    @ParameterizedTest
    @DisplayName("the REST API, so a wrong API path still fails as one")
    @ValueSource(strings = {
        "/camelbee/routes",
        "/camelbee/messages",
        "/camelbee/tracer/status",
        "/camelbee/tracer/filter",
        "/camelbee/auth/login",
        "/camelbee/auth/status",
    })
    void apiPathsAreNotRewritten(String path) {
      assertThat(UiPaths.isClientRoute(path)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("file requests, so a missing bundle 404s instead of returning HTML")
    @ValueSource(strings = {
        "/camelbee/index.html",
        "/camelbee/assets/index-CgyUbDnL.css",
        "/camelbee/assets/index-DcSPX5Tc.js",
        "/camelbee/favicon.ico",
    })
    void fileRequestsAreLeftToTheStaticHandler(String path) {
      assertThat(UiPaths.isClientRoute(path)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("anything outside the UI's base path")
    @ValueSource(strings = {
        "/",
        "/api/musicians",
        "/camelbeeish/settings",
        "/q/health",
    })
    void otherPathsAreNotTouched(String path) {
      assertThat(UiPaths.isClientRoute(path)).isFalse();
    }

    @Test
    void nullIsNotAClientRoute() {
      assertThat(UiPaths.isClientRoute(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("only navigations are rewritten")
  class Navigations {

    @Test
    void aBrowserNavigationWantsHtml() {
      assertThat(UiPaths.wantsHtml("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
          .isTrue();
    }

    @Test
    void aFetchDoesNot() {
      // an XHR asking for JSON must get its 404, not a page
      assertThat(UiPaths.wantsHtml("application/json")).isFalse();
    }

    @Test
    void aMissingHeaderDoesNot() {
      assertThat(UiPaths.wantsHtml(null)).isFalse();
    }
  }
}
