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

package io.camelbee.coreonly.standalone;

import org.apache.camel.main.Main;
import org.camelbee.CamelBee;

/**
 * Plain camel-main application wired to CamelBee through the core library alone - no CamelBee
 * starter as parent, no CamelBee-supplied dependency management.
 *
 * <p>Run with {@code mvn compile exec:java -Dexec.mainClass=...Application}, then open
 * {@code http://localhost:8081/camelbee}. The UI and API live on the camel-main management server,
 * not on the application's own port.
 */
public final class Application {

  private Application() {
  }

  /**
   * Entry point: attaches CamelBee before the context starts, then runs.
   *
   * @param args command-line arguments
   * @throws Exception if the application fails to start
   */
  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.configure().addRoutesBuilder(new PingRoute());
    CamelBee.register(main);
    main.run(args);
  }
}
