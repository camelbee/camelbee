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

package org.camelbee.debugger.controller;

import jakarta.validation.Valid;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.tracers.TracerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TracerController.
 */
@RestController
@CrossOrigin(origins = {"${camelbee.cors.origins:https://www.camelbee.io,http://localhost:8083}"})
@ConditionalOnExpression("${camelbee.context-enabled:false} && ${camelbee.tracer-enabled:false}")
public class TracerController {

  private enum TraceStatus {
    ACTIVE, INACTIVE
  }

  @Autowired
  TracerService tracerService;

  @Autowired
  MessageService messageService;

  /**
   * Enables/Disables tracing.
   *
   * @param traceStatus The traceStatus.
   * @return String The result.
   */
  @PostMapping(value = "/camelbee/tracer/status", produces = "application/json", consumes = "application/json")
  public ResponseEntity<String> updateTraceStatus(@Valid @RequestBody(required = true) TraceStatus traceStatus) {

    if (traceStatus == TraceStatus.ACTIVE) {
      tracerService.activateTracing(true);
      tracerService.keepTracingActive();
    } else if (traceStatus == TraceStatus.INACTIVE) {
      tracerService.activateTracing(false);
    }

    return ResponseEntity.ok("tracing status updated as:" + traceStatus.toString());
  }

  /**
   * Sets the substring a message must contain to be recorded at all. An empty body clears it.
   *
   * <p>Taken as raw text rather than JSON: the filter is an arbitrary payload fragment - an order
   * id, a customer reference - and quoting rules would only get in the way.
   *
   * @param filter the substring, or empty to record everything.
   * @return String The result.
   */
  @PostMapping(value = "/camelbee/tracer/filter", produces = "text/plain", consumes = "text/plain")
  public ResponseEntity<String> updateCaptureFilter(@RequestBody(required = false) String filter) {
    messageService.setCaptureFilter(filter);

    return ResponseEntity.ok(messageService.getCaptureFilter() == null
        ? "capture filter cleared."
        : "capture filter set.");
  }

  /**
   * Returns messages starting from the specified index along with version info.
   * This endpoint is useful for polling new messages without retrieving the entire list.
   *
   * <p>Only {@code index} is required. The two version parameters carry defaults so that a caller
   * which omits them gets the whole list rather than a 400 - JAX-RS defaults a missing
   * {@code @QueryParam long} to 0, and the Quarkus core therefore accepts such a call; without these
   * defaults the same request against Spring Boot fails, and the cores are meant to serve one API.
   *
   * @param fromIndex    The index to start retrieving messages from (0-based)
   * @param addVersion   The last add version the caller has seen, or 0 when it has seen none.
   * @param resetVersion The last reset version the caller has seen, or 0 when it has seen none.
   * @return MessageListWithInfo containing messages from the specified index onwards plus metadata
   */
  @GetMapping(value = "/camelbee/messages")
  public ResponseEntity<MessageListWithInfo> getMessages(
      @RequestParam("index") int fromIndex,
      @RequestParam(value = "addVersion", defaultValue = "0") long addVersion,
      @RequestParam(value = "resetVersion", defaultValue = "0") long resetVersion) {

    tracerService.keepTracingActive();

    return ResponseEntity.ok(messageService.getMessagesFrom(fromIndex, addVersion, resetVersion));
  }

  /**
   * Delete messages and increment reset version.
   *
   * @return String The success message.
   */
  @DeleteMapping(value = "/camelbee/messages")
  public ResponseEntity<String> deleteMessages() {

    messageService.reset();

    return ResponseEntity.ok("deleted.");
  }

}
