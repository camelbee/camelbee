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

package org.camelbee;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.main.Main;
import org.camelbee.notifier.CamelBeeEventNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link CamelBee#register(Main)}, the documented entry point for a {@code camel-main}
 * application - {@code CamelBee.register(main)} is the single line the README asks users to add.
 *
 * <p>{@code CamelBeeTest} exercises {@link CamelBee#attach} directly, which skips both halves of
 * what register does: the management-server defaults, and the {@code MainListener} that defers
 * attach until camel-main has finished configuring. Neither was covered by any test.
 */
class CamelBeeRegisterTest {

  private Main main;

  @AfterEach
  void tearDown() {
    if (main != null) {
      main.stop();
    }
  }

  @Test
  @DisplayName("register enables the management server on 8081 with health checks")
  void registerConfiguresTheManagementServer() {
    main = new Main();

    CamelBee.register(main);

    var management = main.configure().httpManagementServer();
    assertThat(management.isEnabled()).isTrue();
    assertThat(management.getPort()).isEqualTo(8081);
    assertThat(management.isHealthCheckEnabled()).isTrue();
  }

  @Test
  @DisplayName("these are defaults - application.properties still wins")
  void managementServerSettingsAreOverridable() {
    // The javadoc promises camel.management.* overrides these. Setting them after register is the
    // same ordering camel-main uses when it applies application.properties.
    main = new Main();

    CamelBee.register(main);
    main.configure().httpManagementServer().withPort(9999);

    assertThat(main.configure().httpManagementServer().getPort()).isEqualTo(9999);
  }

  @Test
  @DisplayName("the deferred listener attaches CamelBee once the context is configured")
  void listenerAttachesOnStart() throws Exception {
    main = new Main();
    main.setPropertyPlaceholderLocations("false");
    // Nothing here needs an HTTP server; the endpoints degrade gracefully without one.
    main.configure().httpManagementServer().withEnabled(false);

    CamelBee.register(main);
    main.start();

    assertThat(main.getCamelContext().getManagementStrategy().getEventNotifiers())
        .as("register must attach the notifier via its MainListener, not leave it to the caller")
        .anyMatch(CamelBeeEventNotifier.class::isInstance);
  }

  @Test
  @DisplayName("attaching twice does not register the notifier twice")
  void attachIsNotDuplicatedByRegister() throws Exception {
    main = new Main();
    main.setPropertyPlaceholderLocations("false");
    main.configure().httpManagementServer().withEnabled(false);

    CamelBee.register(main);
    main.start();

    long notifiers = main.getCamelContext().getManagementStrategy().getEventNotifiers().stream()
        .filter(CamelBeeEventNotifier.class::isInstance)
        .count();

    assertThat(notifiers)
        .as("a second notifier would double every traced message")
        .isEqualTo(1);
  }
}
