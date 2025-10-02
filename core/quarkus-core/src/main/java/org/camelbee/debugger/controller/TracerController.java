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

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.tracers.TracerService;

/**
 * TracerController.
 */

@Path("/")
@IfBuildProperty(name = "camelbee.context-enabled", stringValue = "true")
@IfBuildProperty(name = "camelbee.tracer-enabled", stringValue = "true")
public class TracerController {

  private enum TraceStatus {
    ACTIVE, DEACTIVE
  }

  @Inject
  TracerService tracerService;

  @Inject
  MessageService messageService;

  /**
   * Enables/Disables tracing.
   *
   * @param traceStatus The traceStatus.
   * @return String The result.
   */
  @POST
  @Consumes("application/json")
  @Produces("application/json")
  @Path("/camelbee/tracer/status")
  public Response updateTraceStatus(@Valid TraceStatus traceStatus) {

    if (traceStatus == TraceStatus.ACTIVE) {
      tracerService.activateTracing(true);
      tracerService.keepTracingActive();
    } else if (traceStatus == TraceStatus.DEACTIVE) {
      tracerService.activateTracing(false);
    }

    return Response.ok("tracing status updated as:" + traceStatus.toString()).build();
  }

  /**
   * Returns messages starting from the specified index along with version info.
   * This endpoint is useful for polling new messages without retrieving the entire list.
   *
   * @param fromIndex The index to start retrieving messages from (0-based)
   * @return MessageListWithInfo containing messages from the specified index onwards plus metadata
   */
  @GET
  @Consumes("application/json")
  @Produces("application/json")
  @Path("/camelbee/messages")
  public Response getMessages(@QueryParam("index") int fromIndex, @QueryParam("addVersion") long addVersion,
      @QueryParam("resetVersion") long resetVersion) {

    //when we start polling the messages we need to activate tracing as well
    tracerService.activateTracing(true);
    tracerService.keepTracingActive();

    return Response.ok(messageService.getMessagesFrom(fromIndex, addVersion, resetVersion)).build();
  }

  /**
   * Delete messages and increment reset version.
   *
   * @return String The success message.
   */
  @DELETE
  @Consumes("application/json")
  @Produces("application/json")
  @Path("/camelbee/messages")
  public Response deleteMessages() {

    messageService.reset();

    return Response.ok("deleted.").build();

  }

}
