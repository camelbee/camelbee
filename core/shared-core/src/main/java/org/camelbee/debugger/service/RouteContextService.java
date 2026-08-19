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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  /**
   * The scheme of Camel's rest-openapi component. Matched as a prefix, not searched for anywhere in
   * the URI: the location that follows it may be spelled {@code openapi.json}, {@code //openapi.json},
   * {@code ///openapi.json} or {@code classpath:openapi.json}, and all of them must be recognized.
   */
  public static final String REST_OPENAPI_COMPONENT = "rest-openapi:";

  /** Component a rest-openapi consumer dispatches its operations to unless the endpoint overrides it. */
  private static final String DEFAULT_CONSUMER_COMPONENT = "direct";

  private static final String CLASSPATH_PREFIX = "classpath:";

  private static final String FILE_PREFIX = "file:";

  /**
   * The keys of an OpenAPI path item that are operations. The others - {@code summary},
   * {@code description}, {@code parameters}, {@code servers}, {@code $ref} - are not, and are not
   * shaped like one either.
   */
  private static final Set<String> OPENAPI_HTTP_METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

  private static final Pattern CONSUMER_COMPONENT_PATTERN = Pattern.compile("[?&]consumerComponentName=([^&]*)");

  /** {@code scheme://host} and {@code scheme:host} address the same endpoint; see {@link #normalizeRouteInput}. */
  private static final Pattern AUTHORITY_SEPARATOR_PATTERN = Pattern.compile(":/{2,}");

  /** A path like {@code /C:/specs/openapi.json}, which only Windows produces and only Windows rejects. */
  private static final Pattern WINDOWS_DRIVE_PATTERN = Pattern.compile("^/[A-Za-z]:");

  private static final Pattern LEADING_SLASHES_PATTERN = Pattern.compile("^/{2,}");

  /** Reused: an ObjectMapper is expensive to build and this one is only ever asked to read. */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
   * strings (common on direct:/seda: inputs, e.g. {@code bridgeErrorHandler}), collapse the
   * {@code ://} authority separator and lowercase, so a real route's input matches the
   * query-param-free synthetic input built from an OpenAPI operationId. Recipe format is not
   * case-guaranteed across Camel versions, hence the case-insensitive comparison.
   *
   * <p>{@code from("direct://getWidgets")} and {@code from("direct:getWidgets")} are the same route
   * to Camel, which stores whichever spelling was authored. The synthetic input is always built with
   * a single colon, so without this the double-slash spelling of a handler route silently failed to
   * be flagged as REST. This is only ever a comparison key - nothing displayed is normalized.
   */
  static String normalizeRouteInput(String input) {
    if (input == null) {
      return null;
    }
    String withoutQuery = QUERY_STRING_IN_BRACKETS_PATTERN.matcher(input).replaceAll("");
    return AUTHORITY_SEPARATOR_PATTERN.matcher(withoutQuery).replaceAll(":").toLowerCase();
  }

  /**
   * Detects a {@code rest-openapi} consumer route and, when it is one, synthesizes the edges Camel
   * resolves at runtime but never records in the route model.
   *
   * <p>A contract-first route is written as {@code from("rest-openapi:openapi.yaml")}: the component
   * reads the spec and dispatches each operation to {@code direct:<operationId>}. None of that
   * dispatch reaches the RouteDefinition - its output list is empty - so without this step the REST
   * route is drawn as a dead end and every handler route as an orphan. The spec is therefore parsed
   * here and one synthetic output added per operationId, which {@link #adjustRestInputRoutes} then
   * matches against the real routes to flag them as REST.
   *
   * <p>Returning true takes the route out of the topology (see {@link #buildCamelRoutes()}): it only
   * exists to carry the synthetic outputs. That happens only when operations were actually found - a
   * spec that cannot be read leaves the route in the graph, unlinked but visible, rather than making
   * it vanish on nothing but a WARN.
   *
   * <p>Known limitation: operations declaring no {@code operationId} are skipped. Camel generates one
   * for those, but the generated form is an internal detail of Camel's processor strategy and no
   * hand-written {@code from("direct:...")} could match it anyway.
   *
   * @param routeDefinition the route whose input is inspected.
   * @param outputs         the output list the synthetic edges are appended to.
   * @return true when this is a rest-openapi route whose spec yielded at least one operation.
   */
  boolean checkRestOpenApiRouteDefinition(RouteDefinition routeDefinition, List<CamelRouteOutput> outputs) {

    String inputUri = routeDefinition.getInput() != null ? routeDefinition.getInput().getUri() : null;

    String specPath = extractOpenApiSpecPath(inputUri);

    if (specPath == null) {
      return false;
    }

    List<String> operationIds = readOperationIds(specPath);

    if (operationIds.isEmpty()) {
      LOGGER.warn("No OpenApi operations read from {}: publishing route {} without its generated edges.",
          specPath, routeDefinition.getId());
      return false;
    }

    String component = resolveConsumerComponent(inputUri);

    operationIds.forEach(p -> outputs.add(new CamelRouteOutput("", "From[" + component + ":" + p + "]", null, null, null)));

    return true;
  }

  /**
   * Extracts the OpenAPI spec location from a {@code rest-openapi} route input, or null if the input
   * is not such a route.
   *
   * <p>Camel keeps the URI exactly as authored in the route model - it is not normalized on start -
   * so every spelling the DSL accepts has to be handled here: {@code rest-openapi:openapi.json},
   * {@code rest-openapi://openapi.yaml}, {@code rest-openapi:///openapi.json} and
   * {@code rest-openapi:classpath:my-api.yaml} all name the same kind of resource. Matching on the
   * scheme prefix rather than searching for {@code rest-openapi://} anywhere in the string is what
   * makes the single-colon form - the one used throughout Camel's own documentation - work.
   *
   * @param inputUri the raw {@code from()} URI of the route.
   * @return the spec location with the scheme and any query string removed, or null.
   */
  static String extractOpenApiSpecPath(String inputUri) {

    if (inputUri == null) {
      return null;
    }

    String uri = inputUri.trim();

    if (!startsWithIgnoreCase(uri, REST_OPENAPI_COMPONENT)) {
      return null;
    }

    String path = uri.substring(REST_OPENAPI_COMPONENT.length());

    int queryIndex = path.indexOf('?');
    if (queryIndex >= 0) {
      path = path.substring(0, queryIndex);
    }

    // "//" is the authority separator, not part of the location: rest-openapi:///x.json means x.json.
    if (path.startsWith("//")) {
      path = path.substring(2);
    }

    path = path.trim();

    return path.isEmpty() ? null : path;
  }

  /**
   * Returns the component the rest-openapi consumer routes its operations to: {@code direct} unless
   * the endpoint overrides it with {@code consumerComponentName}. A component-level override is not
   * visible in the URI and is therefore not honoured here.
   *
   * @param inputUri the raw {@code from()} URI of the route.
   * @return the component name to build the synthetic {@code From[component:operationId]} edges with.
   */
  private String resolveConsumerComponent(String inputUri) {

    Matcher matcher = CONSUMER_COMPONENT_PATTERN.matcher(inputUri);

    if (!matcher.find()) {
      return DEFAULT_CONSUMER_COMPONENT;
    }

    String component = resolvePlaceholders(matcher.group(1)).trim();

    return component.isEmpty() ? DEFAULT_CONSUMER_COMPONENT : component;
  }

  /**
   * Reads the operationIds declared in an OpenAPI spec.
   *
   * <p>Never throws: a spec that is missing, remote, malformed or of an unknown type yields an empty
   * list and a warning. The topology is built for every route in one pass, so an exception escaping
   * here would fail the whole {@code /camelbee/routes} call over one unreadable file.
   *
   * @param specPath the spec location as returned by {@link #extractOpenApiSpecPath(String)}.
   * @return the declared operationIds in document order, without duplicates; never null.
   */
  List<String> readOperationIds(String specPath) {

    String lowerCasePath = specPath.toLowerCase();
    boolean json = lowerCasePath.endsWith(".json");
    boolean yaml = lowerCasePath.endsWith(".yaml") || lowerCasePath.endsWith(".yml");

    if (!json && !yaml) {
      LOGGER.warn("Unknown file type for the OpenAPI spec: {}", specPath);
      return List.of();
    }

    try (InputStream inputStream = openSpecification(specPath)) {

      if (inputStream == null) {
        LOGGER.warn("Could not find the OpenApi spec: {}", specPath);
        return List.of();
      }

      return json ? readOperationIdsFromJson(inputStream) : readOperationIdsFromYaml(inputStream);

    } catch (Exception e) {
      LOGGER.warn("Could not read the OpenApi spec: {} with exception: {}", specPath, e.toString());
      return List.of();
    }
  }

  /**
   * Opens an OpenAPI spec the way the rest-openapi component resolves it: from the classpath by
   * default, from disk for a {@code file:} location or a path that exists there, and not at all for
   * a remote one - fetching a URL while the topology is being built would block a request thread on
   * a third party.
   *
   * @param specPath the spec location.
   * @return the stream, or null when the spec cannot be located.
   * @throws IOException if a file that exists cannot be opened.
   */
  private InputStream openSpecification(String specPath) throws IOException {

    if (startsWithIgnoreCase(specPath, "http://") || startsWithIgnoreCase(specPath, "https://")) {
      LOGGER.warn("Remote OpenApi specs are not read for the topology: {}", specPath);
      return null;
    }

    if (startsWithIgnoreCase(specPath, FILE_PREFIX)) {
      return Files.newInputStream(Path.of(toFilePath(specPath)));
    }

    String path = startsWithIgnoreCase(specPath, CLASSPATH_PREFIX)
        ? specPath.substring(CLASSPATH_PREFIX.length()) : specPath;

    /*
     ClassLoader.getResourceAsStream - unlike Class.getResourceAsStream - does not accept a leading
     slash and returns null for one, which is exactly what rest-openapi:///openapi.json produces.
     */
    InputStream inputStream = classpathResource(path.startsWith("/") ? path.substring(1) : path);

    if (inputStream != null) {
      LOGGER.debug("Read the OpenApi spec {} from the classpath.", specPath);
      return inputStream;
    }

    Path onDisk = Path.of(path);

    if (!Files.isRegularFile(onDisk)) {
      return null;
    }

    // Logged, not silent: a relative path that misses the classpath resolves against the working
    // directory, and reading a same-named file that happens to sit there should be visible.
    LOGGER.info("OpenApi spec {} is not on the classpath; reading it from {}.", specPath, onDisk.toAbsolutePath());

    return Files.newInputStream(onDisk);
  }

  /**
   * Turns a {@code file:} location into a filesystem path. {@code file:/x}, {@code file://x} and
   * {@code file:///x} all denote the same file, so the leading slashes are collapsed - except on a
   * Windows drive letter, where {@code file:///C:/spec.json} would collapse to {@code /C:/spec.json}
   * and no longer be a path Windows accepts.
   *
   * @param fileLocation a location starting with {@code file:}.
   * @return the filesystem path it denotes.
   */
  static String toFilePath(String fileLocation) {

    String filePath = LEADING_SLASHES_PATTERN.matcher(fileLocation.substring(FILE_PREFIX.length())).replaceFirst("/");

    return WINDOWS_DRIVE_PATTERN.matcher(filePath).find() ? filePath.substring(1) : filePath;
  }

  private static InputStream classpathResource(String resource) {

    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

    InputStream inputStream = contextClassLoader != null ? contextClassLoader.getResourceAsStream(resource) : null;

    return inputStream != null ? inputStream : RouteContextService.class.getClassLoader().getResourceAsStream(resource);
  }

  private static boolean startsWithIgnoreCase(String value, String prefix) {
    return value.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  private List<String> readOperationIdsFromYaml(InputStream inputStream) {

    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    Object specification = yaml.load(inputStream);

    return specification instanceof Map<?, ?> map ? collectOperationIds(map.get("paths")) : List.of();
  }

  /**
   * Reads the spec as plain maps rather than as a JsonNode tree, so both formats are walked by the
   * one {@link #collectOperationIds} implementation. Two walkers over the same structure had already
   * drifted apart on how they type-check an operationId.
   */
  private List<String> readOperationIdsFromJson(InputStream inputStream) throws IOException {

    Object specification = OBJECT_MAPPER.readValue(inputStream, Object.class);

    return specification instanceof Map<?, ?> map ? collectOperationIds(map.get("paths")) : List.of();
  }

  /**
   * Collects the operationIds out of a parsed {@code paths} node.
   *
   * <p>Everything is checked before it is cast. A path item legally holds more than operations -
   * {@code summary} and {@code description} are strings, {@code parameters} and {@code servers} are
   * lists, {@code $ref} points elsewhere - and blindly treating each value as an operation map used
   * to abort the walk on a ClassCastException, silently truncating the operation list of any spec
   * that used those keys.
   *
   * @param paths the value of the spec's {@code paths} key.
   * @return the operationIds in document order, without duplicates.
   */
  private List<String> collectOperationIds(Object paths) {

    if (!(paths instanceof Map<?, ?> pathItems)) {
      return List.of();
    }

    Set<String> operationIds = new LinkedHashSet<>();

    for (Object pathItem : pathItems.values()) {

      if (!(pathItem instanceof Map<?, ?> operations)) {
        continue;
      }

      for (Map.Entry<?, ?> operation : operations.entrySet()) {

        if (!isOperation(operation.getKey()) || !(operation.getValue() instanceof Map<?, ?> operationFields)) {
          continue;
        }

        Object operationId = operationFields.get(OPENAPI_OPERATIONID);

        if (operationId != null) {
          addOperationId(operationIds, operationId.toString());
        }
      }
    }

    return List.copyOf(operationIds);
  }

  private static void addOperationId(Set<String> operationIds, String operationId) {
    if (!operationId.isBlank()) {
      operationIds.add(operationId.trim());
    }
  }

  private static boolean isOperation(Object pathItemKey) {
    return pathItemKey instanceof String key && OPENAPI_HTTP_METHODS.contains(key.toLowerCase());
  }

}
