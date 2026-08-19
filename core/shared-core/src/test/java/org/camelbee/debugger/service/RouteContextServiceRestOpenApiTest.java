package org.camelbee.debugger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.camelbee.debugger.model.route.CamelRoute;
import org.camelbee.debugger.model.route.CamelRouteOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the rest-openapi half of {@link RouteContextService}.
 *
 * <p>A contract-first route - {@code from("rest-openapi:openapi.yaml")} - dispatches each spec
 * operation to {@code direct:<operationId>} at runtime, and none of that dispatch is recorded in the
 * route model. The service reconstructs those edges by reading the spec, so these tests pin down the
 * three things that can go wrong: recognizing every URI spelling Camel accepts (the URI is kept
 * verbatim in the model, never normalized), reading the spec from wherever it lives, and never
 * letting an unreadable spec take a route out of the topology or fail the whole topology call.
 *
 * <p>The end-to-end tests drive a real, started CamelContext. {@code camel-rest-openapi} is not on
 * this module's classpath - and its consumer would need a platform-http runtime anyway - so the
 * scheme is served by {@link StubRestOpenApiComponent}. That is enough, because everything under
 * test reads the route model, and the model holds the URI exactly as it was authored.
 */
class RouteContextServiceRestOpenApiTest {

  /** The operations declared by every fixture spec, in document order and without the duplicate. */
  private static final List<String> FIXTURE_OPERATION_IDS = List.of("getWidgets", "createWidget", "getWidgetById");

  private CamelContext camelContext;

  @AfterEach
  void tearDown() {
    if (camelContext != null) {
      camelContext.stop();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // End to end: routes collected from a real CamelContext, for every URI spelling and both formats.
  // ---------------------------------------------------------------------------------------------

  /**
   * The whole point of the feature, exercised through {@link RouteContextService#getCamelRoutes()}:
   * the rest-openapi route drops out of the topology and every route that handles one of its
   * operations comes back flagged as REST, whichever way the URI was written and whichever format
   * the spec is in.
   *
   * <p>{@code rest-openapi:openapi.json} - the single-colon form used throughout Camel's own
   * documentation - is the case that matters most: matching the scheme as a substring
   * ({@code "rest-openapi://"}) silently skipped it, leaving the handler routes unflagged.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
      "rest-openapi:openapi.json",
      "rest-openapi://openapi.json",
      "rest-openapi:///openapi.json",
      "rest-openapi:classpath:openapi.json",
      "rest-openapi:openapi.yaml",
      "rest-openapi://openapi.yaml",
      "rest-openapi:///openapi.yaml",
      "rest-openapi:classpath:openapi.yaml",
      "rest-openapi:classpath:my-api.yaml",
      "rest-openapi:specs/nested-api.yml",
      "rest-openapi:///specs/nested-api.yml",
      "rest-openapi:openapi.json?missingOperation=ignore",
      "rest-openapi://openapi.yaml?missingOperation=ignore&mockIncludePattern=classpath:mock/**",
  })
  void getCamelRoutes_flagsTheHandlerRoutesOfEveryRestOpenApiUriForm(String restOpenApiUri) throws Exception {

    List<CamelRoute> routes = collectRoutes(restOpenApiUri, "direct");

    // The rest-openapi route itself only carries the synthetic edges; it is not part of the topology.
    assertThat(routes).extracting(CamelRoute::getId).doesNotContain("restRoute");

    assertThat(routes)
        .filteredOn(RouteContextServiceRestOpenApiTest::isRest)
        .extracting(CamelRoute::getId)
        .containsExactlyInAnyOrder("getWidgetsRoute", "createWidgetRoute", "getWidgetByIdRoute");

    // A route the spec does not mention keeps its non-REST identity.
    assertThat(routes)
        .filteredOn(r -> "internalRoute".equals(r.getId()))
        .singleElement()
        .satisfies(r -> assertThat(isRest(r)).isFalse());
  }

  /**
   * The failure mode that used to be worst: an unreadable spec made the route disappear from the
   * topology on nothing but a WARN, because the route was moved to the REST bucket before anything
   * was known about the spec. It now stays in the graph - unlinked, but visible.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
      "rest-openapi:does-not-exist.json",
      "rest-openapi:does-not-exist.yaml",
      "rest-openapi:///does-not-exist.json",
      "rest-openapi:classpath:does-not-exist.yaml",
      "rest-openapi:malformed.json",
      "rest-openapi:malformed.yaml",
      "rest-openapi:no-paths.json",
      "rest-openapi:no-operation-ids.yaml",
      "rest-openapi:https://example.org/openapi.json",
      "rest-openapi:openapi.txt",
  })
  void getCamelRoutes_keepsTheRouteInTheTopologyWhenTheSpecCannotBeRead(String restOpenApiUri) throws Exception {

    List<CamelRoute> routes = collectRoutes(restOpenApiUri, "direct");

    assertThat(routes).extracting(CamelRoute::getId).contains("restRoute");
    assertThat(routes).filteredOn(RouteContextServiceRestOpenApiTest::isRest).isEmpty();
  }

  /**
   * The handler routes are matched on their input URI, which commonly carries query parameters the
   * synthetic edge never has. Covered here end to end because it is the combination - synthesis plus
   * matching - that has to hold.
   */
  @Test
  void getCamelRoutes_flagsHandlerRoutesThatCarryQueryParametersOnTheirInput() throws Exception {

    camelContext = newCamelContext(builder -> {
      builder.from("rest-openapi:openapi.yaml").routeId("restRoute").to("mock:rest");
      builder.from("direct:getWidgets?block=false&timeout=5000").routeId("getWidgetsRoute").to("mock:widgets");
    });

    List<CamelRoute> routes = new RouteContextService(camelContext).getCamelRoutes();

    assertThat(routes)
        .filteredOn(r -> "getWidgetsRoute".equals(r.getId()))
        .singleElement()
        .satisfies(r -> assertThat(isRest(r)).isTrue());
  }

  /**
   * Camel's consumer dispatches to {@code direct} unless the endpoint says otherwise, so a route
   * that overrides {@code consumerComponentName} must have its edges built with that component -
   * hardcoding {@code direct:} would leave its handler routes unflagged.
   */
  @Test
  void getCamelRoutes_honoursConsumerComponentNameWhenBuildingTheEdges() throws Exception {

    List<CamelRoute> routes = collectRoutes("rest-openapi:openapi.json?consumerComponentName=seda", "seda");

    assertThat(routes)
        .filteredOn(RouteContextServiceRestOpenApiTest::isRest)
        .extracting(CamelRoute::getId)
        .containsExactlyInAnyOrder("getWidgetsRoute", "createWidgetRoute", "getWidgetByIdRoute");
  }

  /**
   * {@code from("direct://getWidgets")} and {@code from("direct:getWidgets")} are the same route to
   * Camel, which keeps whichever spelling was authored, while the synthetic edge is always built
   * with a single colon. The double-slash spelling therefore used to go unflagged - for any
   * component, {@code direct} included.
   */
  @ParameterizedTest(name = "[{index}] handler authored as {0}")
  @CsvSource({
      "direct://getWidgets,  rest-openapi:openapi.json",
      "direct://getWidgets,  rest-openapi://openapi.yaml",
      "seda://getWidgets,    rest-openapi:openapi.json?consumerComponentName=seda",
      "seda://getWidgets,    rest-openapi:openapi.yaml?consumerComponentName=seda",
  })
  void getCamelRoutes_flagsAHandlerRouteAuthoredWithTheAuthoritySeparator(String handlerUri, String restOpenApiUri) throws Exception {

    camelContext = newCamelContext(builder -> {
      builder.from(restOpenApiUri.trim()).routeId("restRoute").to("mock:rest");
      builder.from(handlerUri.trim()).routeId("getWidgetsRoute").to("mock:widgets");
    });

    assertThat(new RouteContextService(camelContext).getCamelRoutes())
        .filteredOn(r -> "getWidgetsRoute".equals(r.getId()))
        .singleElement()
        .satisfies(r -> assertThat(isRest(r)).isTrue());
  }

  @ParameterizedTest(name = "[{index}] {0} <-> {1}")
  @CsvSource({
      "From[direct://getWidgets],           From[direct:getWidgets]",
      "From[direct:getWidgets],             From[direct://getWidgets]",
      "From[jms://queue],                   From[jms:queue]",
      "From[DIRECT://GetWidgets],           From[direct:getWidgets]",
      "From[direct://getWidgets?block=false], From[direct:getWidgets]",
  })
  void normalizeRouteInput_treatsTheAuthoritySeparatorAsTheSameEndpoint(String left, String right) {
    assertThat(RouteContextService.normalizeRouteInput(left.trim()))
        .isEqualTo(RouteContextService.normalizeRouteInput(right.trim()));
  }

  @Test
  void normalizeRouteInput_stillSeparatesDifferentEndpoints() {
    assertThat(RouteContextService.normalizeRouteInput("From[direct://getWidgets]"))
        .isNotEqualTo(RouteContextService.normalizeRouteInput("From[direct:createWidget]"));
  }

  // ---------------------------------------------------------------------------------------------
  // Spec path extraction.
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest(name = "[{index}] {0} -> {1}")
  @CsvSource({
      "rest-openapi:openapi.json,                                  openapi.json",
      "rest-openapi://openapi.yaml,                                openapi.yaml",
      "rest-openapi:///openapi.json,                               /openapi.json",
      "rest-openapi:classpath:my-api.yaml,                         classpath:my-api.yaml",
      "rest-openapi://classpath:my-api.yaml,                       classpath:my-api.yaml",
      "rest-openapi:specs/nested-api.yml,                          specs/nested-api.yml",
      "rest-openapi:file:/etc/camelbee/openapi.yaml,               file:/etc/camelbee/openapi.yaml",
      "rest-openapi:openapi.json?consumerComponentName=seda,       openapi.json",
      "rest-openapi://openapi.yaml?missingOperation=ignore,        openapi.yaml",
      "rest-openapi:///openapi.json?a=1&b=2,                       /openapi.json",
      "REST-OPENAPI:openapi.json,                                  openapi.json",
  })
  void extractOpenApiSpecPath_handlesEveryUriSpelling(String inputUri, String expectedPath) {
    assertThat(RouteContextService.extractOpenApiSpecPath(inputUri.trim())).isEqualTo(expectedPath);
  }

  /**
   * The scheme has to be a prefix, not a substring. The substring check also let a URI that merely
   * mentioned the scheme somewhere - in a query parameter, say - be treated as a spec location.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
      "direct:start",
      "direct:start?fallback=rest-openapi://openapi.json",
      "timer:tick?period=1000",
      "rest-openapi://",
      "rest-openapi:",
      "rest-openapi:?consumerComponentName=seda",
      "rest-openapi:   ",
  })
  void extractOpenApiSpecPath_returnsNullForAnythingThatIsNotASpecLocation(String inputUri) {
    assertThat(RouteContextService.extractOpenApiSpecPath(inputUri)).isNull();
  }

  @Test
  void extractOpenApiSpecPath_returnsNullForNull() {
    assertThat(RouteContextService.extractOpenApiSpecPath(null)).isNull();
  }

  @Test
  void extractOpenApiSpecPath_ignoresSurroundingWhitespace() {
    assertThat(RouteContextService.extractOpenApiSpecPath("  rest-openapi:openapi.json  ")).isEqualTo("openapi.json");
  }

  // ---------------------------------------------------------------------------------------------
  // Reading the spec.
  // ---------------------------------------------------------------------------------------------

  /**
   * Every fixture declares the same operations, in both formats and at every location, so a single
   * expectation covers the whole matrix. {@code /openapi.json} is the regression that matters:
   * {@code ClassLoader.getResourceAsStream} - unlike {@code Class.getResourceAsStream} - returns
   * null for a leading slash, which is exactly what {@code rest-openapi:///openapi.json} yields.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
      "openapi.json",
      "openapi.yaml",
      "/openapi.json",
      "/openapi.yaml",
      "classpath:openapi.json",
      "classpath:openapi.yaml",
      "classpath:/openapi.json",
      "CLASSPATH:my-api.yaml",
      "my-api.yaml",
      "specs/nested-api.yml",
      "/specs/nested-api.yml",
  })
  void readOperationIds_readsTheDeclaredOperationsFromEveryLocation(String specPath) {
    assertThat(newService().readOperationIds(specPath)).containsExactlyElementsOf(FIXTURE_OPERATION_IDS);
  }

  /**
   * Operations with no operationId are skipped - Camel generates ids for those, but no hand-written
   * route could match the generated form - and an id that appears twice contributes one edge.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"openapi.json", "openapi.yaml"})
  void readOperationIds_skipsOperationsWithoutAnIdAndDeduplicates(String specPath) {
    assertThat(newService().readOperationIds(specPath))
        .containsExactly("getWidgets", "createWidget", "getWidgetById")
        .doesNotHaveDuplicates();
  }

  /**
   * A path item legally holds {@code summary} and {@code description} (strings), {@code parameters}
   * (a list) and {@code $ref} next to its operations. Treating each of those as an operation map
   * threw a ClassCastException mid-walk in the YAML reader, truncating the operation list of any
   * spec that used them - the fixtures use all of them, and nothing is lost.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"openapi.json", "openapi.yaml"})
  void readOperationIds_ignoresPathItemKeysThatAreNotOperations(String specPath) {
    assertThat(newService().readOperationIds(specPath))
        .hasSize(3)
        .doesNotContain("legacyOperation");
  }

  /**
   * Nothing may escape: the topology is built for every route in one pass, so an exception thrown
   * over one unreadable spec used to fail the entire {@code /camelbee/routes} call. A missing
   * {@code .json} spec was the live case - Jackson answers a null stream with
   * IllegalArgumentException, which the old {@code catch (IOException)} did not cover.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
      "does-not-exist.json",
      "does-not-exist.yaml",
      "does-not-exist.yml",
      "/does-not-exist.json",
      "classpath:does-not-exist.json",
      "malformed.json",
      "malformed.yaml",
      "no-paths.json",
      "no-paths.yaml",
      "no-operation-ids.yaml",
      "paths-not-a-map.yaml",
      "openapi.txt",
      "openapi",
      "http://example.org/openapi.json",
      "https://example.org/openapi.yaml",
      "file:/does/not/exist.yaml",
  })
  void readOperationIds_returnsEmptyAndNeverThrowsForAnUnreadableSpec(String specPath) {
    RouteContextService service = newService();
    assertThatCode(() -> assertThat(service.readOperationIds(specPath)).isEmpty()).doesNotThrowAnyException();
  }

  @Test
  void readOperationIds_readsASpecFromDiskThroughAFileUri(@TempDir Path tempDir) throws IOException {

    Path spec = copyFixtureTo(tempDir, "openapi.yaml");

    assertThat(newService().readOperationIds("file:" + spec)).containsExactlyElementsOf(FIXTURE_OPERATION_IDS);
    assertThat(newService().readOperationIds("file://" + spec)).containsExactlyElementsOf(FIXTURE_OPERATION_IDS);
  }

  @Test
  void readOperationIds_readsASpecFromAnAbsolutePathThatIsNotOnTheClasspath(@TempDir Path tempDir) throws IOException {

    Path spec = copyFixtureTo(tempDir, "openapi.json");

    assertThat(newService().readOperationIds(spec.toString())).containsExactlyElementsOf(FIXTURE_OPERATION_IDS);
  }

  /**
   * A {@code file:} location is turned into a filesystem path by collapsing the leading slashes, and
   * a Windows drive letter is the one case where that produces a path the platform rejects:
   * {@code file:///C:/spec.json} must not become {@code /C:/spec.json}. Asserted on the mapping
   * itself, so the case is covered wherever the suite runs.
   */
  @ParameterizedTest(name = "[{index}] {0} -> {1}")
  @CsvSource({
      "file:/etc/camelbee/openapi.yaml,      /etc/camelbee/openapi.yaml",
      "file://etc/camelbee/openapi.yaml,     /etc/camelbee/openapi.yaml",
      "file:///etc/camelbee/openapi.yaml,    /etc/camelbee/openapi.yaml",
      "file:openapi.json,                    openapi.json",
      "file:specs/openapi.json,              specs/openapi.json",
      "file:///C:/specs/openapi.json,        C:/specs/openapi.json",
      "file://C:/specs/openapi.json,         C:/specs/openapi.json",
      "file:C:/specs/openapi.json,           C:/specs/openapi.json",
  })
  void toFilePath_keepsAWindowsDriveLetterAddressable(String fileLocation, String expectedPath) {
    assertThat(RouteContextService.toFilePath(fileLocation.trim())).isEqualTo(expectedPath);
  }

  // ---------------------------------------------------------------------------------------------
  // The detection itself, without a context.
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"rest-openapi:openapi.json", "rest-openapi://openapi.yaml", "rest-openapi:///openapi.json"})
  void checkRestOpenApiRouteDefinition_addsOneEdgePerOperation(String inputUri) {

    List<CamelRouteOutput> outputs = new ArrayList<>();

    boolean rest = newService().checkRestOpenApiRouteDefinition(routeDefinition(inputUri), outputs);

    assertThat(rest).isTrue();
    assertThat(outputs)
        .extracting(CamelRouteOutput::getDescription)
        .containsExactly("From[direct:getWidgets]", "From[direct:createWidget]", "From[direct:getWidgetById]");
  }

  @Test
  void checkRestOpenApiRouteDefinition_buildsTheEdgesWithTheOverriddenConsumerComponent() {

    List<CamelRouteOutput> outputs = new ArrayList<>();

    newService().checkRestOpenApiRouteDefinition(
        routeDefinition("rest-openapi:openapi.json?consumerComponentName=seda&missingOperation=ignore"), outputs);

    assertThat(outputs)
        .extracting(CamelRouteOutput::getDescription)
        .containsExactly("From[seda:getWidgets]", "From[seda:createWidget]", "From[seda:getWidgetById]");
  }

  /** The component name is a URI value like any other, so a placeholder in it has to be resolved. */
  @Test
  void checkRestOpenApiRouteDefinition_resolvesAPlaceholderInTheConsumerComponent() {

    RouteContextService service = new RouteContextService(new DefaultCamelContext(),
        key -> "camelbee.consumer".equals(key) ? Optional.of("seda") : Optional.empty());

    List<CamelRouteOutput> outputs = new ArrayList<>();

    service.checkRestOpenApiRouteDefinition(
        routeDefinition("rest-openapi:openapi.json?consumerComponentName={{camelbee.consumer}}"), outputs);

    assertThat(outputs).extracting(CamelRouteOutput::getDescription).allSatisfy(d -> assertThat(d).startsWith("From[seda:"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"direct:start", "rest-openapi:does-not-exist.json", "rest-openapi:no-operation-ids.yaml"})
  void checkRestOpenApiRouteDefinition_returnsFalseAndAddsNothingWhenThereIsNothingToSynthesize(String inputUri) {

    List<CamelRouteOutput> outputs = new ArrayList<>();

    boolean rest = newService().checkRestOpenApiRouteDefinition(routeDefinition(inputUri), outputs);

    assertThat(rest).isFalse();
    assertThat(outputs).isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers.
  // ---------------------------------------------------------------------------------------------

  /**
   * Copies a fixture spec out of the test classpath. Reading it through a working-directory-relative
   * path breaks the moment the suite is run from another module's basedir, which
   * {@code quarkus-core-camelk} does - it compiles these very test sources against Camel 4.8.5.
   */
  private static Path copyFixtureTo(Path directory, String fixture) throws IOException {

    Path spec = directory.resolve(fixture);

    try (InputStream fixtureStream = RouteContextServiceRestOpenApiTest.class.getClassLoader().getResourceAsStream(fixture)) {
      assertThat(fixtureStream).as("fixture %s must be on the test classpath", fixture).isNotNull();
      Files.copy(fixtureStream, spec);
    }

    return spec;
  }

  /** {@code getRest()} is a Boolean that is left null on a route Camel never marked as REST. */
  private static boolean isRest(CamelRoute route) {
    return Boolean.TRUE.equals(route.getRest());
  }

  private RouteContextService newService() {
    return new RouteContextService(new DefaultCamelContext(), key -> Optional.empty());
  }

  private static RouteDefinition routeDefinition(String inputUri) {
    RouteDefinition routeDefinition = new RouteDefinition();
    routeDefinition.setId("restRoute");
    routeDefinition.from(inputUri);
    return routeDefinition;
  }

  /**
   * Starts a context holding the rest-openapi route, one handler route per fixture operation and one
   * unrelated route, then returns the collected topology.
   */
  private List<CamelRoute> collectRoutes(String restOpenApiUri, String consumerComponent) throws Exception {

    camelContext = newCamelContext(builder -> {
      builder.from(restOpenApiUri).routeId("restRoute").to("mock:rest");
      builder.from(consumerComponent + ":getWidgets").routeId("getWidgetsRoute").to("mock:widgets");
      builder.from(consumerComponent + ":createWidget").routeId("createWidgetRoute").to("mock:widgets");
      builder.from(consumerComponent + ":getWidgetById").routeId("getWidgetByIdRoute").to("mock:widgets");
      builder.from("direct:internal").routeId("internalRoute").to("mock:internal");
    });

    return new RouteContextService(camelContext).getCamelRoutes();
  }

  private static CamelContext newCamelContext(java.util.function.Consumer<RouteBuilder> routes) throws Exception {

    DefaultCamelContext context = new DefaultCamelContext();
    context.addComponent("rest-openapi", new StubRestOpenApiComponent());
    context.addRoutes(new RouteBuilder() {

      @Override
      public void configure() {
        routes.accept(this);
      }
    });
    context.start();

    return context;
  }

  /**
   * Stands in for {@code camel-rest-openapi}, which is not a dependency of this module and whose
   * consumer needs a platform-http runtime to start. It does nothing but let a
   * {@code from("rest-openapi:...")} route start, which is all these tests need: the URI they assert
   * on is the one held by the route model, untouched by the component.
   */
  private static final class StubRestOpenApiComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
      // The stub understands no options; drop them so DefaultComponent does not reject the URI.
      parameters.clear();
      return new StubRestOpenApiEndpoint(uri, this);
    }
  }

  private static final class StubRestOpenApiEndpoint extends DefaultEndpoint {

    private StubRestOpenApiEndpoint(String uri, DefaultComponent component) {
      super(uri, component);
    }

    @Override
    public Producer createProducer() {
      throw new UnsupportedOperationException("rest-openapi stub is consumer only");
    }

    @Override
    public Consumer createConsumer(Processor processor) {
      return new DefaultConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
      return true;
    }
  }
}
