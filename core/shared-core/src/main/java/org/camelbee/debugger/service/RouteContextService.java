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

package org.camelbee.debugger.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.camelbee.config.PropertyResolver;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.camelbee.utils.ExchangeUtils;
import org.camelbee.utils.UriSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * RouteContextService.
 *
 * <p>Standalone variant of the core service: the framework glue is removed
 * (CDI/Spring DI -> plain constructor taking the CamelContext, and MicroProfile/Spring
 * config -> Camel's PropertiesComponent for {{placeholder}} resolution). The route
 * topology extraction is otherwise identical to the Quarkus/Spring Boot cores.
 */
public class RouteContextService {

  public static final String OPENAPI_OPERATIONID = "operationId";
  public static final String REST_OPENAPI_COMPONENT = "rest-openapi://";

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(.*?)}}");

  private static final Pattern QUERY_STRING_IN_BRACKETS_PATTERN = Pattern.compile("\\?[^\\]]*");

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RouteContextService.class);

  private final CamelContext camelContext;

  /** How {{...}} placeholders are resolved; supplied per runtime. See {@link PropertyResolver}. */
  private final PropertyResolver propertyResolver;

  /**
   * Cached topology. Populated only once the CamelContext has started — see
   * {@link #getCamelRoutes()}. Volatile because it is written from whichever thread asks
   * first (an HTTP worker serving /camelbee/routes) and read from Camel's routing threads
   * via the tracer.
   */
  private volatile List<CamelRoute> routes;

  public RouteContextService(CamelContext camelContext) {
    this(camelContext, PropertyResolver.fromCamelContext(camelContext));
  }

  /**
   * Constructor taking an explicit property resolver.
   *
   * @param camelContext     the context whose routes are published.
   * @param propertyResolver how {{...}} placeholders in endpoint URIs are resolved.
   */
  public RouteContextService(CamelContext camelContext, PropertyResolver propertyResolver) {
    this.camelContext = camelContext;
    this.propertyResolver = propertyResolver;
  }

  /**
   * Returns CamelRoutes.
   *
   * <p>The result is cached, because the tracer asks for it per exchange. It is only cached
   * once the CamelContext reports started, though: the HTTP layer can be serving requests
   * while Camel is still starting, and a call landing in that window would otherwise pin an
   * empty — or half-built — topology for the life of the process. That is not hypothetical;
   * a readiness probe polling /camelbee/routes during startup left the UI drawing a blank
   * canvas until the application was restarted. While the context is still coming up the
   * topology is rebuilt on each call and nothing is retained.
   *
   * @return route list with the links.
   */
  public List<CamelRoute> getCamelRoutes() {

    List<CamelRoute> cached = routes;
    if (cached != null) {
      return cached;
    }

    List<CamelRoute> collectedRoutes = buildCamelRoutes();

    if (camelContext.getStatus().isStarted()) {
      routes = collectedRoutes;
    } else {
      LOGGER.debug("CamelContext is not started yet ({}); serving the topology without caching it.",
          camelContext.getStatus());
    }

    return collectedRoutes;
  }

  private List<CamelRoute> buildCamelRoutes() {

    List<CamelRoute> collected = new ArrayList<>();

    List<CamelRoute> restRoutes = new ArrayList<>();

    for (Route route : camelContext.getRoutes()) {
      String routeId = route.getId();

      RouteDefinition routeDefinition = ((ModelCamelContext) camelContext)
          .getRouteDefinition(routeId);

      if (routeDefinition == null) {
        // Can happen while the context is still starting: the Route exists but its
        // definition is not registered on the model yet. Skip it; this call is not cached.
        continue;
      }

      List<CamelRouteOutput> outputs = new ArrayList<>();

      extractOutputs(routeDefinition.getOutputs(), outputs);

      boolean isRestApiRoute = checkRestOpenApiRouteDefinition(routeDefinition, outputs);

      String errorHandler = null;

      if (routeDefinition.getErrorHandlerFactory() instanceof DeadLetterChannelBuilder deadLetterChannelBuilder) {
        errorHandler = deadLetterChannelBuilder.getDeadLetterUri();
      }

      CamelRoute metaRoute = new CamelRoute(routeDefinition.getId(),
          updateWithSystemProperties(routeDefinition.getInput().toString()), outputs,
          routeDefinition.isRest(), errorHandler, routeDefinition.getDescriptionText());

      if (isRestApiRoute) {
        restRoutes.add(metaRoute);
      } else {
        collected.add(metaRoute);
      }

    }
    /*
     set the rest property to true of the routes that are called
     directly from the rest-openapi route,
     this is not done by camel anymore if you use rest-openapi with a yaml file.
     */
    adjustRestInputRoutes(restRoutes, collected);

    return collected;
  }

  private void extractOutputs(List<ProcessorDefinition<?>> outputss,
      List<CamelRouteOutput> outputs) {

    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, ToDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            null, p.getClass().getTypeName(), null, p.getDescriptionText())));

    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, ToDynamicDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            null, p.getClass().getTypeName(), null, p.getDescriptionText())));

    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, PollDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            null, p.getClass().getTypeName(), null, p.getDescriptionText())));

    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, EnrichDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            null, p.getClass().getTypeName(), null, p.getDescriptionText())));

    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, PollEnrichDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            null, p.getClass().getTypeName(), null, p.getDescriptionText())));

    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, RecipientListDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            p.getDelimiter(), p.getClass().getTypeName(), null, p.getDescriptionText())));

    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, RoutingSlipDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            p.getUriDelimiter(), p.getClass().getTypeName(), null, p.getDescriptionText())));

    /*
     A dynamicRouter computes its targets at runtime, so it contributes no static edge - the UI
     draws its hops from the traced messages instead. It is still collected because the traced
     messages carry this node's id, and that is what lets the UI attribute those runtime hops to
     the route that owns the router rather than to whichever route was called just before.
     DynamicRouterDefinition extends ExpressionNode, so no pass above already collects it.
     */
    ProcessorDefinitionHelper.filterTypeInOutputs(outputss, DynamicRouterDefinition.class).stream()
        .forEach(p -> outputs.add(new CamelRouteOutput(p.getId(), updateWithSystemProperties(p.toString()),
            p.getUriDelimiter(), p.getClass().getTypeName(), null, p.getDescriptionText())));

  }

  /**
   * Resolves {@code {{...}}} placeholders in a route model description and then redacts any
   * credentials the resolution just exposed.
   *
   * <p>The two steps belong together, which is why the sanitizing is here rather than at the call
   * sites. Resolving is what creates the problem: a route that keeps its password in a property is
   * written safely, but the resolved form of {@code authPassword={{backend.password}}} is the
   * password itself. Every URI-bearing string in the topology passes through this method, so this is
   * the one place that has to be right - see {@link UriSanitizer} for what it can and cannot catch.
   *
   * @param id the raw route model description.
   * @return the description with placeholders resolved and credentials redacted.
   */
  private String updateWithSystemProperties(String id) {
    return UriSanitizer.sanitize(resolvePlaceholders(id), ExchangeUtils.getMasker());
  }

  private String resolvePlaceholders(String id) {
    if (id.contains("{{") && id.contains("}}")) {
      Matcher matcher = PLACEHOLDER_PATTERN.matcher(id);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
        String fullMatch = matcher.group(1);
        String key;
        String defaultValue = null;

        // Check if there's a colon in the property key
        int colonIndex = fullMatch.indexOf(':');
        if (colonIndex > 0) {
          // Extract the key (before the colon)
          key = fullMatch.substring(0, colonIndex);
          // Extract the default value (after the colon)
          defaultValue = fullMatch.substring(colonIndex + 1);
        } else {
          key = fullMatch;
        }

        // standalone: resolve via Camel's PropertiesComponent (was MicroProfile/Spring config in the cores)
        Optional<String> configValue = propertyResolver.resolve(key);
        String replacement;

        // Use config value if present, otherwise use default value
        if (configValue.isPresent()) {
          replacement = configValue.get();
        } else if (defaultValue != null) {
          replacement = defaultValue;
        } else {
          replacement = "";
        }

        // Escape special characters in the replacement string
        replacement = Matcher.quoteReplacement(replacement);

        matcher.appendReplacement(result, replacement);
      }

      matcher.appendTail(result);
      return result.toString();
    } else {
      return id;
    }
  }

  void adjustRestInputRoutes(List<CamelRoute> restApiRoutes, List<CamelRoute> routes) {

    restApiRoutes.stream().forEach(e -> e.getOutputs().forEach(p -> {
      String targetInput = normalizeRouteInput(p.getDescription());
      routes.stream().filter(r -> normalizeRouteInput(r.getInput()).equals(targetInput)).forEach(s -> s.setRest(true));
    }));
  }

  /**
   * Normalize a route's {@code From[...]} input string for REST-input matching: strip query
   * strings (common on direct:/seda: inputs, e.g. {@code bridgeErrorHandler}) and lowercase, so a
   * real route's input matches the query-param-free synthetic input built from an OpenAPI
   * operationId. Recipe format is not case-guaranteed across Camel versions, hence the
   * case-insensitive comparison.
   */
  static String normalizeRouteInput(String input) {
    if (input == null) {
      return null;
    }
    return QUERY_STRING_IN_BRACKETS_PATTERN.matcher(input).replaceAll("").toLowerCase();
  }

  private boolean checkRestOpenApiRouteDefinition(RouteDefinition routeDefinition, List<CamelRouteOutput> outputs) {
    String inputUri = routeDefinition.getInput() != null ? routeDefinition.getInput().getUri() : null;

    if (inputUri != null && inputUri.contains(REST_OPENAPI_COMPONENT)) {

      int startIndex = inputUri.indexOf(REST_OPENAPI_COMPONENT) + REST_OPENAPI_COMPONENT.length();

      int endIndex = inputUri.indexOf("?", startIndex);
      if (endIndex == -1) {
        endIndex = inputUri.length();
      }

      String openApiPath = inputUri.substring(startIndex, endIndex);
      List<String> operationIds = null;

      if (openApiPath.endsWith(".json")) {
        operationIds = readOperationIdsFromJson(openApiPath);
      } else if (openApiPath.endsWith(".yml") || openApiPath.endsWith(".yaml")) {
        operationIds = readOperationIdsFromYaml(openApiPath);
      } else {
        LOGGER.warn("Unknown file type for the OpenAPI spec: {}", openApiPath);
        return false;
      }

      operationIds.forEach(p -> outputs.add(new CamelRouteOutput("", "From[direct:" + p + "]", null, null, null)));

      return true;
    }

    return false;

  }

  private List<String> readOperationIdsFromYaml(String openApiPath) {

    List<String> operationIds = new ArrayList<>();

    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(openApiPath)) {

      Map<String, Object> data = yaml.load(inputStream);

      Map<String, Map<String, Map<String, Object>>> paths = (Map<String, Map<String, Map<String, Object>>>) data.get("paths");

      for (Map<String, Map<String, Object>> methods : paths.values()) {
        for (Map<String, Object> methodData : methods.values()) {
          if (methodData.containsKey(OPENAPI_OPERATIONID)) {
            operationIds.add(methodData.get(OPENAPI_OPERATIONID).toString());
          }
        }
      }

    } catch (Exception e) {
      LOGGER.warn("Could not read the OpenApi spec: {} with exception: {}", openApiPath, e);
    }

    return operationIds;
  }

  private List<String> readOperationIdsFromJson(String openApiPath) {

    List<String> operationIds = new ArrayList<>();

    ObjectMapper mapper = new ObjectMapper();

    try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(openApiPath)) {

      JsonNode rootNode = mapper.readTree(inputStream);

      JsonNode pathsNode = rootNode.get("paths");
      if (pathsNode != null) {
        pathsNode.fields().forEachRemaining(entry -> {
          JsonNode methodsNode = entry.getValue();
          methodsNode.fields().forEachRemaining(method -> {
            JsonNode operationIdNode = method.getValue().get(OPENAPI_OPERATIONID);
            if (operationIdNode != null) {
              operationIds.add(operationIdNode.asText());
            }
          });
        });
      }

    } catch (IOException e) {
      LOGGER.warn("Could not read the OpenApi spec: {} with exception: {}", openApiPath, e);
    }

    return operationIds;
  }

}
