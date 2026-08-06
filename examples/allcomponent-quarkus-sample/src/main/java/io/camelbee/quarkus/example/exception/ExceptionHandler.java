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

package io.camelbee.quarkus.example.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.camelbee.config.CamelBeeRouteConfigurer;

/**
 * Global Error Handler.
 *
 * @author ekaraosmanoglu
 */
@ApplicationScoped
public class ExceptionHandler extends RouteBuilder {

  final CamelBeeRouteConfigurer camelBeeRouteConfigurer;

  final ErrorProcessor globalErrorProcessor;

  @Inject
  ObjectMapper mapper;

  public ExceptionHandler(CamelBeeRouteConfigurer camelBeeRouteConfigurer, ErrorProcessor globalErrorProcessor) {
    this.camelBeeRouteConfigurer = camelBeeRouteConfigurer;
    this.globalErrorProcessor = globalErrorProcessor;
  }

  /**
   * The creates a new deadletter channel builder.
   */

  public DeadLetterChannelBuilder appErrorHandler() {
    var deadLetterChannelBuilder = new DeadLetterChannelBuilder();
    deadLetterChannelBuilder.setDeadLetterUri("direct:error");
    deadLetterChannelBuilder.logHandled(false);
    deadLetterChannelBuilder.useOriginalMessage();
    /*
     Retry a failed send twice before handing the exchange to direct:error. CamelBee traces every
     attempt separately, so one failing exchange shows up as three request/response pairs on the
     same edge - see the invokeFlakyRoute in MusicianRoute for a route that exercises this.
     */
    deadLetterChannelBuilder.maximumRedeliveries(2);
    deadLetterChannelBuilder.redeliveryDelay(200);
    return deadLetterChannelBuilder;
  }

  /**
   * Configure global error route.
   *
   * @throws Exception can be thrown during configuration
   */
  @Override
  public void configure() throws Exception {

    camelBeeRouteConfigurer.configureRoute(this);

    JacksonDataFormat dataFormat = new JacksonDataFormat();
    dataFormat.setObjectMapper(mapper);

    from("direct:error").routeId("errorHandlerDirectComponent")
        .process(globalErrorProcessor)
        .marshal(dataFormat);

  }
}
