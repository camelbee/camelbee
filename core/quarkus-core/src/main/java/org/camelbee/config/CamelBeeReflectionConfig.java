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

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.camelbee.debugger.model.exchange.Message;
import org.camelbee.debugger.model.exchange.MessageEventType;
import org.camelbee.debugger.model.exchange.MessageListInfo;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.model.exchange.MessageType;
import org.camelbee.debugger.model.route.CamelBeeContext;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.camelbee.tracers.TracerService;

/**
 * Registers the engine's model classes for reflection in a Quarkus native image.
 *
 * <p>These types used to carry {@code @RegisterForReflection} themselves. They now live in
 * {@code camelbee-core}, which must not depend on Quarkus, so the annotation is applied from here
 * using its {@code targets} attribute instead. Without this, a native build serializes them as empty
 * JSON objects - the REST API returns {@code {}} for every route and message, and nothing fails
 * loudly enough to notice.
 *
 * <p>Anything added to the engine's model must be listed here as well.
 */
@RegisterForReflection(targets = {
    Message.class,
    MessageEventType.class,
    MessageType.class,
    MessageListInfo.class,
    MessageListWithInfo.class,
    CamelBeeContext.class,
    CamelRoute.class,
    CamelRouteOutput.class,
    TracerService.class
})
public final class CamelBeeReflectionConfig {

  private CamelBeeReflectionConfig() {
  }
}
