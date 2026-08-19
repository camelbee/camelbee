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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Replaces {@code CoreParityTest}, and guards the opposite invariant.
 *
 * <p>Until the engine was extracted into {@code camelbee-core}, the three runtime cores carried
 * hand-copied copies of the tracing, topology and model code, and the risk worth testing was a copy
 * someone forgot to make. That risk is now gone by construction: there is one copy.
 *
 * <p>The risk that replaces it is the duplication coming back - someone fixing a bug by pasting an
 * engine class into a runtime core, where it would shadow the shared one on that runtime only and
 * diverge silently. This asserts the engine packages exist in exactly one place.
 *
 * <p>It also keeps a narrow parity check that still means something: quarkus-core and springboot-core
 * expose the same REST surface through parallel controller classes. Their contents differ (JAX-RS
 * versus Spring MVC), so only the class names are compared - enough to catch an endpoint added to
 * one runtime and not the other.
 */
class NoEngineDuplicationTest {

  /** Packages that must live only in camelbee-core. */
  private static final List<String> ENGINE_PACKAGES = List.of(
      "constants", "debugger/model", "debugger/service", "logging",
      "masking", "notifier", "tracers", "utils");

  private static final List<String> RUNTIME_CORES = List.of("quarkus-core", "springboot-core", "standalone-core");

  private static Path packageDir(String module, String sourceSet, String pkg) {
    // surefire runs with the module basedir as the working directory
    return Path.of("..", module, "src", sourceSet, "java", "org", "camelbee").resolve(pkg);
  }

  private static SortedSet<String> classesIn(String module, String sourceSet, String pkg) throws IOException {
    Path dir = packageDir(module, sourceSet, pkg);
    if (!Files.isDirectory(dir)) {
      return new TreeSet<>();
    }
    // Recursive: debugger/model holds its classes in subpackages, and a copy pasted back into a
    // runtime core could land at any depth.
    try (Stream<Path> files = Files.walk(dir)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .map(path -> dir.relativize(path).toString())
          .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    }
  }

  @ParameterizedTest(name = "{0} declares no engine classes")
  @ValueSource(strings = {"quarkus-core", "springboot-core", "standalone-core"})
  @DisplayName("the engine lives in camelbee-core only")
  void runtimeCoresDeclareNoEngineClasses(String core) throws IOException {
    for (String pkg : ENGINE_PACKAGES) {
      SortedSet<String> classes = classesIn(core, "main", pkg);

      /*
       standalone-core keeps its own config package - CamelBeeConfig reads camelbee.* properties
       from the CamelContext, which is runtime wiring rather than engine code. Only the packages
       listed above are engine packages, and 'config' is deliberately not one of them.
       */
      assertThat(classes)
          .as("%s/%s should be empty - that package belongs to camelbee-core. Found %s, "
              + "which will shadow the shared class on this runtime only and then drift.", core, pkg, classes)
          .isEmpty();
    }
  }

  @Test
  @DisplayName("camelbee-core actually holds the engine")
  void sharedCoreHoldsTheEngine() throws IOException {
    // The check above passes trivially if the shared module were emptied or the layout moved, so
    // pin the other half: the classes have to exist somewhere.
    for (String pkg : ENGINE_PACKAGES) {
      assertThat(classesIn("shared-core", "main", pkg))
          .as("shared-core/%s is empty - has the module layout changed?", pkg)
          .isNotEmpty();
    }
  }

  @Test
  @DisplayName("quarkus and spring boot expose the same controller classes")
  void restSurfacesMatch() throws IOException {
    SortedSet<String> quarkus = classesIn("quarkus-core", "main", "debugger/controller");
    SortedSet<String> springboot = classesIn("springboot-core", "main", "debugger/controller");

    assertThat(quarkus)
        .as("a controller exists in one runtime and not the other - the REST surface has diverged")
        .isEqualTo(springboot)
        .isNotEmpty();
  }

  @Test
  @DisplayName("the runtime cores still exist where this test expects them")
  void reactorLayoutIsAsExpected() {
    // Guards against this whole test passing vacuously if the module layout changes.
    for (String core : RUNTIME_CORES) {
      assertThat(Path.of("..", core, "src", "main", "java").toFile())
          .as("%s not found - the reactor layout changed and this test needs updating", core)
          .isDirectory();
    }
  }
}
