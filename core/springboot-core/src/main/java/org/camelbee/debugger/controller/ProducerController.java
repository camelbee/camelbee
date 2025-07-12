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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.commons.lang3.StringUtils;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.model.produce.ProduceMessage;
import org.camelbee.tracers.TracerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ProducerController.
 */
@RestController
@CrossOrigin(origins = {"https://www.camelbee.io", "http://localhost:8083"})
@ConditionalOnExpression("'${camelbee.context-enabled:false}' && '${camelbee.producer-enabled:false}'")
public class ProducerController {

  @Autowired
  CamelContext camelContext;

  @Autowired
  ProducerTemplate producerTemplate;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  Environment env;

  @Autowired
  TracerService tracerService;

  /**
   * Call the route.
   *
   * @param produceMessage The ProduceMessage.
   * @return String The response of the called route.
   */
  @PostMapping(value = "/camelbee/produce/direct", produces = "application/json", consumes = "application/json")
  public ResponseEntity<String> produceDirect(@Valid @RequestBody(required = true) ProduceMessage produceMessage) {

    // first set the tracing status
    tracerService.activateTracing(Boolean.TRUE.equals(produceMessage.getTraceEnabled()));

    Exchange exchange = ExchangeBuilder.anExchange(camelContext).build();

    // setting this to exclude the events for this exchange from event notifiers
    exchange.setProperty(CamelBeeConstants.CAMELBEE_PRODUCED_EXCHANGE, "true");

    Map<String, Object> defaultHeaders = produceMessage.getHeaders().getHeaders().stream()
        .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

    exchange.getIn().setHeaders(defaultHeaders);

    final Object request;

    String response = "";

    try {

      if (produceMessage.getMediaType() != null && produceMessage.getMediaType().equals("json")
          && !StringUtils.isEmpty(produceMessage.getClazz())) {
        request = objectMapper.readValue(produceMessage.getMessage(), Class.forName(produceMessage.getClazz()));

      } else {
        request = produceMessage.getMessage();
      }

      exchange.getIn().setBody(request);

      Exchange result = producerTemplate.send(adjustRouteName(produceMessage.getRouteName()), exchange);
      response = result.getMessage().getBody(String.class);

    } catch (Exception e) {
      response = e.getLocalizedMessage();
    }

    return ResponseEntity.ok(response);
  }

  /**
   * Replaces the placeholders with the actual environment variables.
   *
   * @param routeName The route name.
   * @return String The adjusted route name.
   */
  public String adjustRouteName(String routeName) {

    String adjustedRouteName = null;

    if (routeName.startsWith("From[rest:")) {
      adjustedRouteName = "http:localhost:{{local.server.port}}" + applyPattern(routeName, "://[a-zA-Z]+:(/[^?]+)") + "?throwExceptionOnFailure=false";
    } else if (routeName.startsWith("From[jpa:")) {
      adjustedRouteName = applyPattern(routeName, "From\\[(jpa:[^?]+)");
    } else {
      adjustedRouteName = applyPattern(routeName, "\\[([^\\]]+)\\]");
    }

    return adjustedRouteName;
  }

  /**
   * Applies regex pattern on the route name.
   *
   * @param input      The route name.
   * @param patternVal The regex.
   * @return String The updated route name.
   */
  public static String applyPattern(String input, String patternVal) {

    Pattern pattern = Pattern.compile(patternVal);
    Matcher matcher = pattern.matcher(input);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

}
