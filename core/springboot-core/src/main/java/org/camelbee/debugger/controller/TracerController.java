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
@CrossOrigin(origins = {"https://www.camelbee.io", "http://localhost:8083"})
@ConditionalOnExpression("'${camelbee.context-enabled:false}' && '${camelbee.tracer-enabled:false}'")
public class TracerController {

  private enum TraceStatus {
    ACTIVE, DEACTIVE
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
    } else if (traceStatus == TraceStatus.DEACTIVE) {
      tracerService.activateTracing(false);
    }

    return ResponseEntity.ok("tracing status updated as:" + traceStatus.toString());
  }

  /**
   * Returns messages starting from the specified index along with version info.
   * This endpoint is useful for polling new messages without retrieving the entire list.
   *
   * @param fromIndex The index to start retrieving messages from (0-based)
   * @return MessageListWithInfo containing messages from the specified index onwards plus metadata
   */
  @GetMapping(value = "/camelbee/messages")
  public ResponseEntity<MessageListWithInfo> getMessages(@RequestParam("index") int fromIndex, @RequestParam("addVersion") long addVersion,
      @RequestParam("resetVersion") long resetVersion) {

    //when we start polling the messages we need to activate tracing as well
    tracerService.activateTracing(true);
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
