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

import org.apache.camel.CamelContext;

/**
 * Immutable holder of CamelBee configuration flags for the standalone runtime.
 *
 * <p>Replaces the per-framework config injection (Spring {@code @Value} / MicroProfile
 * {@code @ConfigProperty}) with values resolved from Camel's PropertiesComponent, so the
 * same {@code camelbee.*} keys work via application.properties, system properties, env, etc.
 */
public final class CamelBeeConfig {

  private final boolean contextEnabled;
  private final boolean tracerEnabled;
  private final boolean loggingEnabled;
  private final boolean notifierEnabled;
  private final boolean routeConfigurerEnabled;
  private final boolean metricsEnabled;
  private final long tracerMaxIdleTime;
  private final long tracerMaxMessagesCount;

  private CamelBeeConfig(boolean contextEnabled, boolean tracerEnabled, boolean loggingEnabled,
      boolean notifierEnabled, boolean routeConfigurerEnabled, boolean metricsEnabled,
      long tracerMaxIdleTime, long tracerMaxMessagesCount) {
    this.contextEnabled = contextEnabled;
    this.tracerEnabled = tracerEnabled;
    this.loggingEnabled = loggingEnabled;
    this.notifierEnabled = notifierEnabled;
    this.routeConfigurerEnabled = routeConfigurerEnabled;
    this.metricsEnabled = metricsEnabled;
    this.tracerMaxIdleTime = tracerMaxIdleTime;
    this.tracerMaxMessagesCount = tracerMaxMessagesCount;
  }

  /**
   * Builds the configuration from the given CamelContext's resolved properties, applying the
   * same defaults as the Quarkus/Spring Boot cores.
   *
   * @param camelContext the CamelContext whose PropertiesComponent supplies the values.
   * @return the resolved configuration.
   */
  public static CamelBeeConfig from(CamelContext camelContext) {
    return new CamelBeeConfig(
        resolveBoolean(camelContext, "camelbee.context-enabled", true),
        resolveBoolean(camelContext, "camelbee.tracer-enabled", false),
        resolveBoolean(camelContext, "camelbee.logging-enabled", false),
        resolveBoolean(camelContext, "camelbee.notifier-enabled", true),
        resolveBoolean(camelContext, "camelbee.route-configurer-enabled", true),
        resolveBoolean(camelContext, "camelbee.metrics-enabled", true),
        resolveLong(camelContext, "camelbee.tracer-max-idle-time", 300000L),
        resolveLong(camelContext, "camelbee.tracer-max-messages-count", 1000L));
  }

  private static boolean resolveBoolean(CamelContext camelContext, String key, boolean defaultValue) {
    return camelContext.getPropertiesComponent().resolveProperty(key)
        .map(Boolean::parseBoolean).orElse(defaultValue);
  }

  private static long resolveLong(CamelContext camelContext, String key, long defaultValue) {
    return camelContext.getPropertiesComponent().resolveProperty(key)
        .map(Long::parseLong).orElse(defaultValue);
  }

  public boolean isContextEnabled() {
    return contextEnabled;
  }

  public boolean isTracerEnabled() {
    return tracerEnabled;
  }

  public boolean isLoggingEnabled() {
    return loggingEnabled;
  }

  public boolean isNotifierEnabled() {
    return notifierEnabled;
  }

  public boolean isRouteConfigurerEnabled() {
    return routeConfigurerEnabled;
  }

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public long getTracerMaxIdleTime() {
    return tracerMaxIdleTime;
  }

  public long getTracerMaxMessagesCount() {
    return tracerMaxMessagesCount;
  }
}
