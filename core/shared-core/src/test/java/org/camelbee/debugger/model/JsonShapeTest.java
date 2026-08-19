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

package org.camelbee.debugger.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageListInfo;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the JSON key contract that the embedded UI parses.
 *
 * <p>The controller tests assert on the objects the endpoints return, never on the bytes that go
 * over the wire. That leaves the actual contract - the property names in the JSON - unasserted, and
 * the three cores carry hand-copied duplicates of these model classes. The realistic failure is a
 * field added to one core and forgotten in the others, or a getter renamed in a way that silently
 * changes the emitted key; either way the UI reads {@code undefined} and draws nothing, with no test
 * going red.
 *
 * <p>The expected key sets below are the Java side of the interfaces declared in
 * {@code ui/src/types/routes.ts} and {@code ui/src/types/messages.ts}. Changing one without the
 * other is the bug this catches, so update them together.
 *
 * <p>This test is mirrored verbatim in quarkus-core, springboot-core and standalone-core.
 *
 * <p><b>Scope.</b> It serializes with a plain {@link ObjectMapper}, so it pins the model, not each
 * runtime's mapper configuration. The one place those configurations have to agree beyond key names
 * is {@code Instant} rendering: {@code MessageListInfo.lastModified}/{@code lastResetTime} are typed
 * {@code string} in the UI, so they must be ISO-8601 and not epoch numbers. standalone-core
 * configures that explicitly in {@code CamelBeeHttpEndpoints}; Quarkus and Spring Boot get it from
 * their own defaults. That is asserted end-to-end by the standalone integration tests rather than
 * here - a plain mapper cannot serialize {@code Instant} at all without the JSR-310 module, which is
 * not on every core's classpath.
 */
class JsonShapeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** The keys of one serialized object, ignoring anything nested inside it. */
  private static Set<String> keysOf(Object value) {
    return MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {
    }).keySet();
  }

  private static CamelRouteOutput output() {
    return new CamelRouteOutput("to1", "To[direct:next]", null, "direct", List.of(), "sends it on");
  }

  private static CamelRoute route() {
    return new CamelRoute("route1", "direct://start", List.of(output()), false, "direct:error",
        "the first route");
  }

  private static Message message() {
    return new Message("exchange-1", MessageEventType.SENT, "{}", "{}", "route1", "direct://next",
        "to1", MessageType.RESPONSE, null, 42L);
  }

  /** Instants are left null so a plain mapper can serialize this - see the class javadoc. */
  private static MessageListInfo info() {
    return new MessageListInfo(3, 1L, 2L, null, null, true);
  }

  @Test
  @DisplayName("CamelBeeContext emits exactly the keys the UI declares")
  void camelBeeContextKeys() {
    CamelBeeContext context = new CamelBeeContext(List.of(route()), "ctx", "jvm", "args", "gc",
        "framework", "4.21.0");

    assertThat(keysOf(context)).containsExactlyInAnyOrder(
        "routes", "name", "jvm", "jvmInputParameters", "garbageCollectors", "framework",
        "camelVersion");
  }

  @Test
  @DisplayName("CamelRoute emits exactly the keys the UI declares, including routeDescription")
  void camelRouteKeys() {
    assertThat(keysOf(route())).containsExactlyInAnyOrder(
        "id", "input", "outputs", "rest", "errorHandler", "routeDescription");
  }

  @Test
  @DisplayName("CamelRouteOutput emits exactly the keys the UI declares, including nodeDescription")
  void camelRouteOutputKeys() {
    assertThat(keysOf(output())).containsExactlyInAnyOrder(
        "id", "description", "delimiter", "type", "outputs", "nodeDescription");
  }

  @Test
  @DisplayName("Message emits exactly the keys the UI declares, including timeTaken")
  void messageKeys() {
    assertThat(keysOf(message())).containsExactlyInAnyOrder(
        "exchangeId", "exchangeEventType", "messageBody", "headers", "routeId", "endpoint",
        "endpointId", "messageType", "exception", "timeStamp", "timeTaken", "parentExchangeId");
  }

  @Test
  @DisplayName("MessageListInfo emits exactly the keys the UI declares, including capReached")
  void messageListInfoKeys() {
    assertThat(keysOf(info())).containsExactlyInAnyOrder(
        "count", "resetVersion", "addVersion", "lastModified", "lastResetTime", "capReached");
  }

  @Test
  @DisplayName("MessageListWithInfo wraps the list under the keys the UI polls")
  void messageListWithInfoKeys() {
    MessageListWithInfo payload = new MessageListWithInfo(List.of(message()), info());

    assertThat(keysOf(payload)).containsExactlyInAnyOrder("messages", "info");
  }

  @Test
  @DisplayName("keeps null-valued keys instead of dropping them")
  void keepsNullKeys() {
    // The UI's message-to-edge matching branches on endpointId being null; a mapper configured to
    // omit nulls would make the key absent rather than null, which is not the same shape.
    Message unattributed = new Message("exchange-1", MessageEventType.CREATED, null, null, null,
        null, null, MessageType.REQUEST, null, 0L);

    assertThat(keysOf(unattributed))
        .contains("routeId", "endpoint", "endpointId", "messageBody", "headers", "exception");
    assertThat(MAPPER.convertValue(unattributed, new TypeReference<Map<String, Object>>() {
    })).containsEntry("endpointId", null);
  }

  @Test
  @DisplayName("serializes the enums as their names, which is what the UI's union types expect")
  void enumsAsNames() {
    Map<String, Object> json = MAPPER.convertValue(message(),
        new TypeReference<Map<String, Object>>() {
        });

    assertThat(json).containsEntry("exchangeEventType", "SENT")
        .containsEntry("messageType", "RESPONSE");
  }
}
