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

package io.camelbee.standalone.example;

import io.camelbee.standalone.example.routes.MusicianRoute;
import org.apache.camel.main.Main;
import org.camelbee.CamelBee;

/**
 * Standalone Camel (camel-main) application demonstrating CamelBee.
 *
 * <p>Run it with: {@code mvn compile exec:java}. The application's own routes are served on the
 * main server (port 8080, see application.properties), while the CamelBee debugger UI runs on the
 * separate camel-main management port: open {@code http://localhost:8081/camelbee} to see the
 * topology and live message tracing.
 */
public final class Application {

  private Application() {
  }

  /**
   * Application entry point: starts the Camel main application with CamelBee monitoring attached.
   *
   * @param args the command-line arguments
   * @throws Exception if the Camel application fails to start
   */
  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.configure().addRoutesBuilder(new MusicianRoute());
    // attach CamelBee monitoring (endpoints + tracer + notifier)
    CamelBee.register(main);
    main.run(args);
  }
}
