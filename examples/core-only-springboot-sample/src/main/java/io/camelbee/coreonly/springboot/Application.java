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

package io.camelbee.coreonly.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application wired to CamelBee through the core library alone - no CamelBee starter as
 * parent, no CamelBee-supplied dependency management.
 *
 * <p>{@code org.camelbee} has to be in the component scan: the core ships plain Spring beans rather
 * than an auto-configuration, so nothing picks them up otherwise. See README Option 1.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.camelbee", "io.camelbee.coreonly.springboot"})
public class Application {

  /**
   * Entry point.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
