/*
 * Copyright 2023 Rahmi Ege Karaosmanoglu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camelbee.springboot.example.exception;

import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.camelbee.config.CamelBeeRouteConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Global Error Handler.
 *
 * @author ekaraosmanoglu
 */
@Component
public class ExceptionHandler extends RouteBuilder {

    final CamelBeeRouteConfigurer camelBeeRouteConfigurer;

    final ErrorProcessor globalErrorProcessor;

    public ExceptionHandler(CamelBeeRouteConfigurer camelBeeRouteConfigurer, ErrorProcessor globalErrorProcessor) {
        this.camelBeeRouteConfigurer = camelBeeRouteConfigurer;
        this.globalErrorProcessor = globalErrorProcessor;
    }

    /**
     * The creates a new deadletter channel builder.
     */

    @Bean
    public DeadLetterChannelBuilder appErrorHandler() {
        var deadLetterChannelBuilder = new DeadLetterChannelBuilder();
        deadLetterChannelBuilder.setDeadLetterUri("direct:error");
        deadLetterChannelBuilder.logHandled(false);
        deadLetterChannelBuilder.useOriginalMessage();
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

        from("direct:error").routeId("errorHandlerDirectComponent")
                .process(globalErrorProcessor);
    }
}
