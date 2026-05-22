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

package io.camelbee.standalone.example.bean;

/**
 * Demo bean invoked via the Camel bean component.
 */
public class MusicianProcessor {

  /**
   * Pass-through processing step (kept trivial so the demo runs with no external infra).
   *
   * @param body the incoming body.
   * @return the (unchanged) body.
   */
  public Object process(Object body) {
    return body;
  }
}
