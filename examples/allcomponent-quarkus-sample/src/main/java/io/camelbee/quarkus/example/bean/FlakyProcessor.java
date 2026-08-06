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

package io.camelbee.quarkus.example.bean;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

/**
 * Simulates a transient failure so the sample produces redelivery traffic without depending on one
 * of the real backends being down.
 *
 * <p>The attempt counter lives on the exchange, which survives redelivery, so every top-level
 * exchange fails exactly {@value #FAILING_ATTEMPTS} times and then succeeds.
 *
 * @author ekaraosmanoglu
 */
@ApplicationScoped
public class FlakyProcessor {

  /** Attempt counter property, kept on the exchange so it survives redelivery. */
  public static final String ATTEMPTS_PROPERTY = "flakyAttempts";

  /** How many attempts fail before the call succeeds. */
  public static final int FAILING_ATTEMPTS = 2;

  /**
   * Throws on the first {@value #FAILING_ATTEMPTS} invocations for a given exchange, then passes.
   *
   * @param exchange the current exchange.
   */
  public void maybeFail(Exchange exchange) {
    int attempts = exchange.getProperty(ATTEMPTS_PROPERTY, 0, Integer.class) + 1;
    exchange.setProperty(ATTEMPTS_PROPERTY, attempts);

    if (attempts <= FAILING_ATTEMPTS) {
      throw new IllegalStateException("simulated transient failure, attempt " + attempts);
    }
  }
}
