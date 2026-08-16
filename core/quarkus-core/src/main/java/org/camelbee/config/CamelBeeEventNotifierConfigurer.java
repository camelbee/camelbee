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
import java.util.Optional;
import org.apache.camel.CamelContext;
import org.camelbee.masking.Masker;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.camelbee.tracers.TracerService;
import org.camelbee.utils.ExchangeUtils;
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

  /*
   Masking defaults to ON, unlike every other camelbee switch, which default to off. The others fail
   closed by staying off; this one fails closed by staying on - forgetting to configure it must not
   be the thing that leaks a password into a traced body.
   */
  @ConfigProperty(name = "camelbee.masking-enabled", defaultValue = "true")
  boolean maskingEnabled;

  /*
   Optional rather than a defaultValue: Quarkus treats an empty default as "no value" and fails to
   start. Absent means "use Masker.DEFAULT_KEYS", which parseKeys handles.
  */
  @ConfigProperty(name = "camelbee.masked-keys")
  Optional<String> maskedKeys;

  @ConfigProperty(name = "camelbee.tracer-body-enabled", defaultValue = "true")
  boolean tracerBodyEnabled;

  /**
   * Creates EventNotifierSupport bean.
   *
   * @param ev The StartupEvent.
   */
  @SuppressWarnings("java:S1128")
  public void onStart(@Observes StartupEvent ev) {
    /*
     Applied before the notifier is attached, so nothing can be traced unmasked. ExchangeUtils also
     starts out masking with the default keys, so the window before this runs is safe rather than
     open - and stays safe if this configurer is never reached at all.
     */
    ExchangeUtils.configureMasking(
        maskingEnabled ? new Masker(true, Masker.parseKeys(maskedKeys.orElse(null))) : Masker.disabled(),
        tracerBodyEnabled);

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
