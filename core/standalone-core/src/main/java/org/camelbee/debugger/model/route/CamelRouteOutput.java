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
 * CamelRouteOutput.
 */
public class CamelRouteOutput {

  private final String id;
  private final String description;
  private final String delimiter;

  private final String type;
  private final List<CamelRouteOutput> outputs;

  /**
   * Constructor.
   *
   * @param id          The id.
   * @param description The description.
   * @param delimiter   The delimiter.
   * @param type        The type.
   * @param outputs     The outputs.
   */
  public CamelRouteOutput(String id, String description, String delimiter, String type,
      List<CamelRouteOutput> outputs) {
    this.id = id;
    this.description = description;
    this.delimiter = delimiter;
    this.type = type;
    this.outputs = outputs;
  }

  public String getId() {
    return id;
  }

  public String getDescription() {
    return description;
  }

  public String getDelimiter() {
    return delimiter;
  }

  public String getType() {
    return type;
  }

  public List<CamelRouteOutput> getOutputs() {
    return outputs;
  }
}
