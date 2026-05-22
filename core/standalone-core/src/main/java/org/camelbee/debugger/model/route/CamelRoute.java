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

package org.camelbee.debugger.model.route;

import java.util.List;

/**
 * CamelRoute.
 */
public class CamelRoute {

  private final String id;
  private final String input;
  private final List<CamelRouteOutput> outputs;

  private Boolean rest;

  private final String errorHandler;

  /**
   * Constructor.
   *
   * @param id           The id.
   * @param input        The input.
   * @param outputs      The outputs.
   * @param rest         The rest.
   * @param errorHandler The errorHandler.
   */
  public CamelRoute(String id, String input, List<CamelRouteOutput> outputs, Boolean rest,
      String errorHandler) {
    this.id = id;
    this.input = input;
    this.outputs = outputs;
    this.rest = rest;
    this.errorHandler = errorHandler;
  }

  public String getId() {
    return id;
  }

  public String getInput() {
    return input;
  }

  public List<CamelRouteOutput> getOutputs() {
    return outputs;
  }

  public Boolean getRest() {
    return rest;
  }

  public void setRest(boolean rest) {
    this.rest = rest;
  }

  public String getErrorHandler() {
    return errorHandler;
  }
}
