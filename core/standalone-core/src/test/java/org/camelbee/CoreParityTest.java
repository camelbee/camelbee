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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the deliberate duplication of the three cores.
 *
 * <p>quarkus-core, springboot-core and standalone-core carry the same tracing, topology and model
 * code, differing only in how each runtime injects it. That duplication is intentional, but it is
 * maintained by hand: a change is applied to one core and copied to the other two. Nothing detects a
 * copy that was forgotten, which is the realistic failure - a new class added to one core only.
 *
 * <p>This asserts the three cores declare the same set of classes in the shared packages. It does not
 * compare their contents: the cores have small legitimate divergences beyond the DI annotations, so a
 * content diff would either drown in false positives or need normalising so aggressively it would
 * stop catching anything. Behavioural divergence is covered instead by the unit tests, which are
 * themselves mirrored across the three.
 *
 * <p>It lives in standalone-core because a test has to live in exactly one module, and reaches the
 * siblings through the reactor layout. If that layout ever changes, this fails loudly rather than
 * silently passing.
 */
class CoreParityTest {

  private static final List<String> CORES = List.of("quarkus-core", "springboot-core", "standalone-core");

  private static Path packageDir(String core, String sourceSet, String pkg) {
    // surefire runs with the module basedir as the working directory
    return Path.of("..", core, "src", sourceSet, "java", "org", "camelbee").resolve(pkg);
  }

  private static SortedSet<String> classesIn(String core, String sourceSet, String pkg) throws IOException {
    Path dir = packageDir(core, sourceSet, pkg);
    assertThat(dir)
        .as("expected to find %s of %s - has the reactor layout changed?", pkg, core)
        .exists();

    try (Stream<Path> files = Files.list(dir)) {
      return files
          .map(p -> p.getFileName().toString())
          .filter(name -> name.endsWith(".java"))
          .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    }
  }

  @ParameterizedTest(name = "org.camelbee.{0}")
  @ValueSource(strings = {
      "tracers",
      "debugger/service",
      "debugger/model/exchange",
      "debugger/model/route",
      "utils",
      "constants",
      "notifier",
      "logging",
      "masking",
  })
  @DisplayName("all three cores declare the same classes")
  void allCoresDeclareTheSameClasses(String pkg) throws IOException {
    SortedSet<String> reference = classesIn(CORES.get(0), "main", pkg);

    for (String core : CORES.subList(1, CORES.size())) {
      assertThat(classesIn(core, "main", pkg))
          .as("%s of %s differs from %s - a change was applied to one core and not the others",
              pkg, core, CORES.get(0))
          .isEqualTo(reference);
    }
  }

  /**
   * The same guard for the tests, which are mirrored by hand exactly like the main code.
   *
   * <p>Worth its own assertion because a forgotten test copy is quieter than a forgotten class: the
   * build still passes, the feature still works in the core it was written against, and the other
   * two are simply never exercised. Nothing else would notice.
   */
  @ParameterizedTest(name = "test org.camelbee.{0}")
  @ValueSource(strings = {
      "tracers",
      "debugger/service",
      "debugger/model/exchange",
      "utils",
      "masking",
      "logging",
  })
  @DisplayName("all three cores mirror the same tests")
  void allCoresMirrorTheSameTests(String pkg) throws IOException {
    SortedSet<String> reference = classesIn(CORES.get(0), "test", pkg);

    for (String core : CORES.subList(1, CORES.size())) {
      assertThat(classesIn(core, "test", pkg))
          .as("test %s of %s differs from %s - a test was added to one core and not the others",
              pkg, core, CORES.get(0))
          .isEqualTo(reference);
    }
  }
}
