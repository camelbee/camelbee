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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link CamelBeeConfig} resolves the {@code camelbee.*} flags from the CamelContext's
 * PropertiesComponent, applying the same defaults as the Quarkus/Spring Boot cores and honouring
 * overrides supplied as initial properties.
 */
class CamelBeeConfigTest {

  private CamelContext camelContext;

  @AfterEach
  void tearDown() {
    if (camelContext != null) {
      camelContext.stop();
    }
  }

  private CamelBeeConfig configWith(Properties properties) {
    camelContext = new DefaultCamelContext();
    if (properties != null) {
      camelContext.getPropertiesComponent().setInitialProperties(properties);
    }
    camelContext.start();
    return CamelBeeConfig.from(camelContext);
  }

  @Test
  void shouldApplyDefaultsWhenNoPropertiesSet() {
    CamelBeeConfig config = configWith(null);

    assertTrue(config.isContextEnabled());
    assertFalse(config.isTracerEnabled());
    assertFalse(config.isLoggingEnabled());
    assertTrue(config.isNotifierEnabled());
    assertTrue(config.isRouteConfigurerEnabled());
    assertTrue(config.isMetricsEnabled());
    assertEquals(300000L, config.getTracerMaxIdleTime());
    assertEquals(1000L, config.getTracerMaxMessagesCount());
  }

  @Test
  void shouldOverrideDefaultsFromProperties() {
    Properties properties = new Properties();
    properties.setProperty("camelbee.context-enabled", "false");
    properties.setProperty("camelbee.tracer-enabled", "true");
    properties.setProperty("camelbee.logging-enabled", "true");
    properties.setProperty("camelbee.notifier-enabled", "false");
    properties.setProperty("camelbee.route-configurer-enabled", "false");
    properties.setProperty("camelbee.metrics-enabled", "false");
    properties.setProperty("camelbee.tracer-max-idle-time", "12345");
    properties.setProperty("camelbee.tracer-max-messages-count", "50");

    CamelBeeConfig config = configWith(properties);

    assertFalse(config.isContextEnabled());
    assertTrue(config.isTracerEnabled());
    assertTrue(config.isLoggingEnabled());
    assertFalse(config.isNotifierEnabled());
    assertFalse(config.isRouteConfigurerEnabled());
    assertFalse(config.isMetricsEnabled());
    assertEquals(12345L, config.getTracerMaxIdleTime());
    assertEquals(50L, config.getTracerMaxMessagesCount());
  }
}
