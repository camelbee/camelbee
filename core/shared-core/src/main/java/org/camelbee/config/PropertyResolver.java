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

package org.camelbee.config;

import java.util.Optional;
import org.apache.camel.CamelContext;

/**
 * Resolves an application property by key, for the one place the topology needs it:
 * {@code RouteContextService} expands {@code {{...}}} placeholders so the UI shows the endpoint an
 * application really talks to.
 *
 * <p>This is a seam rather than a single implementation because the three runtimes each already had
 * their own source of truth - MicroProfile {@code Config} on Quarkus, Spring's {@code Environment}
 * on Spring Boot, Camel's own {@code PropertiesComponent} standalone. Camel bridges all three, so
 * {@link #fromCamelContext} would very likely serve everywhere; each runtime keeps its own resolver
 * anyway, because swapping the configuration source is a behaviour change and this extraction is
 * meant to be behaviour-preserving.
 */
@FunctionalInterface
public interface PropertyResolver {

  /**
   * Resolves a property.
   *
   * @param key the property key, never null.
   * @return the value, or empty when the key is unknown.
   */
  Optional<String> resolve(String key);

  /**
   * A resolver backed by Camel's own property placeholders.
   *
   * @param camelContext the context to resolve against.
   * @return a resolver over {@code camelContext.getPropertiesComponent()}.
   */
  static PropertyResolver fromCamelContext(CamelContext camelContext) {
    return key -> camelContext.getPropertiesComponent().resolveProperty(key);
  }
}
