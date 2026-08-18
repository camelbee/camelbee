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

package io.camelbee.coreonly.quarkus;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Proves the core-as-a-dependency path end to end on the OLDEST supported Quarkus platform (3.15
 * LTS), against a camelbee-quarkus-core built on Quarkus 3.38.
 *
 * <p>The value here is that augmentation actually runs: Quarkus bakes a lot in at build time, so a
 * jar built on 3.38 compiling against 3.15 proves very little on its own.
 *
 * <p>The module also guards camelbee-quarkus-core's {@code <indexVersion>12</indexVersion>} pin,
 * which Quarkus 3.15-3.21 need because their Jandex cannot read a v13 index. That guard lives in the
 * PACKAGE phase, not here: augmentation runs in quarkus-maven-plugin:build, so these tests pass even
 * with a broken index and only {@code mvn install} catches it.
 */
@QuarkusTest
class CoreOnlyWiringTest {

  @Test
  void servesTheTopologyFromTheCoreLibraryAlone() {
    String body = given()
        .when().get("/camelbee/routes")
        .then().statusCode(200)
        .extract().asString();

    // both routes are discovered, so the notifier and context services are actually wired
    assertThat(body).contains("pingRoute").contains("handleRoute");
  }

  @Test
  void servesTheEmbeddedUi() {
    String body = given()
        .accept("text/html")
        .when().get("/camelbee/")
        .then().statusCode(200)
        .extract().asString();

    assertThat(body).containsIgnoringCase("<html");
  }
}
