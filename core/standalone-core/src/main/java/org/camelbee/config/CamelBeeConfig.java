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
  private final boolean maskingEnabled;
  private final String maskedKeys;
  private final boolean tracerBodyEnabled;
  private final boolean authEnabled;
  private final String authUsername;
  private final String authPassword;
  private final long authSessionTimeout;
  private final String corsAllowedOrigin;

  @SuppressWarnings("java:S107")
  private CamelBeeConfig(boolean contextEnabled, boolean tracerEnabled, boolean loggingEnabled,
      boolean notifierEnabled, boolean routeConfigurerEnabled, boolean metricsEnabled,
      long tracerMaxIdleTime, long tracerMaxMessagesCount,
      boolean maskingEnabled, String maskedKeys, boolean tracerBodyEnabled,
      boolean authEnabled, String authUsername, String authPassword, long authSessionTimeout,
      String corsAllowedOrigin) {
    this.contextEnabled = contextEnabled;
    this.tracerEnabled = tracerEnabled;
    this.loggingEnabled = loggingEnabled;
    this.notifierEnabled = notifierEnabled;
    this.routeConfigurerEnabled = routeConfigurerEnabled;
    this.metricsEnabled = metricsEnabled;
    this.tracerMaxIdleTime = tracerMaxIdleTime;
    this.tracerMaxMessagesCount = tracerMaxMessagesCount;
    this.maskingEnabled = maskingEnabled;
    this.maskedKeys = maskedKeys;
    this.tracerBodyEnabled = tracerBodyEnabled;
    this.authEnabled = authEnabled;
    this.authUsername = authUsername;
    this.authPassword = authPassword;
    this.authSessionTimeout = authSessionTimeout;
    this.corsAllowedOrigin = corsAllowedOrigin;
  }

  /**
   * Builds the configuration from the given CamelContext's resolved properties, applying the
   * same defaults as the Quarkus/Spring Boot cores: everything that exposes the debug surface
   * (topology, tracing) fails closed - off unless explicitly turned on - so an application that
   * forgets to set {@code camelbee.*} in a given environment doesn't silently expose it there.
   *
   * @param camelContext the CamelContext whose PropertiesComponent supplies the values.
   * @return the resolved configuration.
   */
  public static CamelBeeConfig from(CamelContext camelContext) {
    return new CamelBeeConfig(
        resolveBoolean(camelContext, "camelbee.context-enabled", false),
        resolveBoolean(camelContext, "camelbee.tracer-enabled", false),
        resolveBoolean(camelContext, "camelbee.logging-enabled", false),
        resolveBoolean(camelContext, "camelbee.notifier-enabled", true),
        resolveBoolean(camelContext, "camelbee.route-configurer-enabled", true),
        resolveBoolean(camelContext, "camelbee.metrics-enabled", true),
        resolveLong(camelContext, "camelbee.tracer-max-idle-time", 300000L),
        resolveLong(camelContext, "camelbee.tracer-max-messages-count", 1000L),
        /*
         Masking defaults to ON, unlike every other switch here. The rest fail closed by staying
         off; this one fails closed by staying on - forgetting to configure it must not be the
         thing that leaks a password into a traced body.
         */
        resolveBoolean(camelContext, "camelbee.masking-enabled", true),
        resolveString(camelContext, "camelbee.masked-keys", null),
        resolveBoolean(camelContext, "camelbee.tracer-body-enabled", true),
        /*
         Authentication defaults ON. With no password configured, AuthService generates one and logs
         it - deliberately not a fixed default, which would be a published credential and therefore
         no protection at all.
         */
        resolveBoolean(camelContext, "camelbee.auth-enabled", true),
        resolveString(camelContext, "camelbee.username", "camelbee"),
        resolveString(camelContext, "camelbee.password", null),
        resolveLong(camelContext, "camelbee.session-timeout", 120000L),
        // Empty by default: CORS stays closed unless the UI dev server needs it.
        resolveString(camelContext, "camelbee.cors-allowed-origin", null));
  }

  private static String resolveString(CamelContext camelContext, String key, String defaultValue) {
    return camelContext.getPropertiesComponent().resolveProperty(key).orElse(defaultValue);
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

  public boolean isMaskingEnabled() {
    return maskingEnabled;
  }

  /** Comma-separated key list, or null to use {@code Masker.DEFAULT_KEYS}. */
  public String getMaskedKeys() {
    return maskedKeys;
  }

  public boolean isTracerBodyEnabled() {
    return tracerBodyEnabled;
  }

  public boolean isAuthEnabled() {
    return authEnabled;
  }

  public String getAuthUsername() {
    return authUsername;
  }

  public String getAuthPassword() {
    return authPassword;
  }

  public long getAuthSessionTimeout() {
    return authSessionTimeout;
  }

  public String getCorsAllowedOrigin() {
    return corsAllowedOrigin;
  }
}
