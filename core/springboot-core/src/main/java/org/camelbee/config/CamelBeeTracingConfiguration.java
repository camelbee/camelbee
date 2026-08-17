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

import org.camelbee.tracers.TracerService;
import org.springframework.context.annotation.Import;

/**
 * The tracing beans, as one importable unit, for contexts that do not component scan.
 *
 * <p>An application gets these from its own {@code @ComponentScan("org.camelbee")}. A route unit
 * test does not: {@code @SpringBootTest(classes = {SomeRoute.class, CamelBeeRouteConfigurer.class})}
 * is a whitelist, the application class is never consulted, and nothing is scanned - so
 * {@link CamelBeeRouteConfigurer}'s required {@link TracerService} has nothing to resolve against.
 * Such a test lists this class as well and gets the whole graph.
 *
 * <p>Listing it rather than the eight beans keeps the graph the framework's business: a tracer
 * gaining a collaborator is then a change in {@link CamelBeeCoreBeans}, not a change in every
 * generated project's tests.
 *
 * <p><b>Deliberately not annotated {@code @Configuration} or {@code @Component}.</b> Component
 * scanning only picks up {@code @Component}-meta-annotated types, so as written this class is
 * invisible to an application's scan of {@code org.camelbee} - which matters, because imported
 * classes are registered under their fully qualified name while scanned ones get the short name.
 * Were it scannable, an application would end up with two definitions of every bean below and
 * autowiring by type would fail as ambiguous. {@code @Import} on its own is enough to have Spring
 * treat it as a configuration class when it is named explicitly, which is the only way it is ever
 * used. {@code CamelBeeTracingConfigurationTest} guards both halves of that.
 */
@Import(CamelBeeCoreBeans.class)
public class CamelBeeTracingConfiguration {
}
