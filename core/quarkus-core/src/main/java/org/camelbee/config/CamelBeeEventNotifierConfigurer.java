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

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.camelbee.tracers.TracerService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CamelBeeEventNotifierConfigurer.
 */
@Singleton
public class CamelBeeEventNotifierConfigurer {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(CamelBeeEventNotifierConfigurer.class);

  @Inject
  CamelContext camelContext;

  @Inject
  TracerService tracerService;

  @ConfigProperty(name = "camelbee.notifier-enabled", defaultValue = "true")
  boolean notifierEnabled;

  /**
   * Creates EventNotifierSupport bean.
   *
   * @param ev The StartupEvent.
   */
  @SuppressWarnings("java:S1128")
  public void onStart(@Observes StartupEvent ev) {
    if (notifierEnabled) {
      // Only when notifier is enabled do we create the notifier
      // The notifiers themselves will check tracer-enabled and logging-enabled
      final CamelBeeEventNotifier camelBeeEventNotifier = new CamelBeeEventNotifier(tracerService);
      camelContext.getManagementStrategy().addEventNotifier(camelBeeEventNotifier);
    } else {
      LOGGER.debug("CamelBee event notifier disabled via camelbee.notifier-enabled=false");
    }
  }

}
