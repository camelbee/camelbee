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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
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

}
