# CamelBee — Apache Camel monitoring and debugging, embedded in your application

CamelBee asks the running application what its Camel routes are — every endpoint, and how they connect —
and draws it as a live topology in an **embedded UI, served from your application's own HTTP port**. Turn on
the tracer from that UI and the traffic becomes visible too: messages animate along the graph, every hop
lands as a bar in a latency waterfall, and each request and response is there to read.

One dependency. No agent, no collector, no external service — and tracing is switched on and off from the
UI, without a redeploy. Works on **Quarkus**, **Spring Boot**, **Camel K** and **standalone Camel**, on
your laptop and in SIT, UAT or a pod in the cluster.

> **On Spring Boot or Quarkus, but not using Camel?** Your service almost certainly still does
> integration work — REST clients, scheduled jobs, queue listeners, retries, mappers — spread across
> classes with no picture of how they fit together, and nothing to show what actually flowed through them
> last Tuesday. Written as Camel routes with CamelBee added, that same logic reports its own topology and
> lets you replay any request hop by hop, in the environment where it broke, without standing up a
> tracing backend first. Camel is the routing engine; CamelBee is what makes it something you can see.

![Debugger Page](images/debugger_page.png)

## Table of Contents

- [Why](#why)
- [Features](#features)
  - [Route Visualization](#route-visualization)
  - [Message Tracing & Debugging](#message-tracing--debugging)
  - [Latency Waterfall](#latency-waterfall)
  - [Safe to Run Outside Development](#safe-to-run-outside-development)
  - [Health Monitoring](#health-monitoring)
  - [Real-time Metrics](#real-time-metrics)
  - [Metrics Charts](#metrics-charts)
  - [Settings](#settings)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Option 1: Add the Core Library as a Dependency (Recommended)](#option-1-add-the-core-library-as-a-dependency-recommended)
  - [Option 2: Use a CamelBee Starter as Parent (New projects only)](#option-2-use-a-camelbee-starter-as-parent-new-projects-only)
  - [Option 3: Build a Custom Core Library (Custom Java/Camel Versions)](#option-3-build-a-custom-core-library-custom-javacamel-versions)
  - [Running CamelBee Outside Development](#running-camelbee-outside-development)
  - [All configuration properties](#all-configuration-properties)
  - [Securing the CamelBee Endpoints](#securing-the-camelbee-endpoints)
  - [Detailed Documentation](#detailed-documentation)
- [License](#license)

## Why

Integrations are increasingly written by AI agents, and a whole one now arrives in a single commit —
code you did not write, in a codebase you are still expected to own. Reading it line by line tells you
what the code *says*, and slowly: it does not tell you what actually crossed each route boundary, which
endpoint was really called, or what your service sent to its backends. Closing that cognitive gap by
reading is the slow path.

CamelBee closes it a different way. You look at the topology the running application reports and see how
everything is wired at once. Then you ask the agent to trigger a scenario — and watch the messages flow
through it: each hop as a bar in the waterfall, in the order it happened, with the request and response
of every backend call and the time each one took. A few scenarios later you know what the integration
does, far sooner than reading it would have told you. The same applies to inherited routes, where nobody
left is sure what the flow does either.

And because it is served from the application's own HTTP port, you can do this where it matters —
SIT, UAT, a pod in the cluster — not only on a laptop. Tracing starts off, is enabled from the UI
without a restart, switches itself off when idle, and redacts sensitive values at the point of capture,
so it is safe to leave in place.

## Features

### Route Visualization
- Effortlessly visualize complex Camel routes and their interconnections as an interactive topology graph for a better understanding of your microservice architecture.
- Gain a clear overview of message routing and flow paths within your application, with color-coded routes and animated dashed lines showing message traversal.

### Message Tracing & Debugging
- Trace messages as they traverse through Camel routes, enabling real-time debugging and issue identification.
- Inspect full request and response message contents including headers and body in the side panel.
- Detect bottlenecks, errors, or unexpected behavior in your message processing.
- Navigate through the debugging session's timeline using the timeline bar at the bottom, moving back and forth to thoroughly analyze the process flow.
- Follow a request across the exchanges it spawns: `wireTap`, `multicast`, `split`, `recipientList` and `seda` branches are linked back to the exchange that started them instead of appearing as unrelated traffic.
- See failures even on routes started by a consumer (timer, file, JMS), where there is no caller for the error to be reported to.
- Filter traced messages to focus on specific routes or endpoints.

![Message Tracing](images/debugger_messages.png)

### Latency Waterfall
- See where the time actually went: every hop is drawn as a bar positioned by when it started and sized by how long it took.
- Branches are nested under the request that spawned them, so one request reads as one flow rather than a dozen disconnected entries.
- Retry delays and poll timeouts are obvious at a glance — a wide parent bar over near-instant children is waiting, not working.
- Click a bar to select that connection on the topology graph, or click a connection on the graph to highlight and scroll to its bars. The two views stay in step.

![Latency Waterfall](images/debugger_waterfall.png)

Selecting a connection on the topology highlights its bars, and selecting a bar selects the
connection — the two views stay in step.

![Waterfall linked to the topology](images/debugger_waterfall_linked.png)

Retries show up before you open anything. The amber `↻` badge on a connection counts the
**attempts**, while the message count beside it stays at 1 — one exchange, delivered three
times. Here `invokeFlakyRoute → flakyTargetRoute` reads `1` and `↻3`, with the failed
attempts drawn as red edges and the one that finally succeeded in green.

![Retry count on the topology](images/debugger_retry_badge.png)

The waterfall then shows what those attempts cost. `direct://invokeFlaky` is a wide 416ms
bar sitting above three near-instant attempts on `direct://flakyTarget` — two red, then a
blue one that succeeds. The parent was not working for 416ms; it was waiting out two
redelivery delays. `direct://invokeAlwaysFailsDlq` shows the other ending: retries
exhausted, then the `direct://deadLetter` tail. Both flows were started by a timer, so
there was no caller for the failure to be reported to.

![Retries and dead-lettering in the waterfall](images/debugger_retries.png)

### Safe to Run Outside Development
- Tracing starts **off**, and once on it stops itself after a configurable period of inactivity, so it cannot be left running by accident.
- Sensitive values in headers and bodies are **redacted by default** — passwords, tokens, API keys, card numbers and more — with a configurable key list.
- Bodies can be excluded from capture entirely when even best-effort redaction is not enough.
- Trace a **single transaction** in a busy application: give CamelBee an order id or correlation id and it records only the flow containing it, along with the branches that flow spawns. Everything else is never recorded at all.

![Trace a single transaction](images/debugger_capture_filter.png)

### Health Monitoring
- View the health status of your microservice at a glance with the built-in health panel, showing context name, framework version, Camel version, JVM, and garbage collector information.
- Inspect detailed health check results in a modal dialog displaying the full health JSON response including camel-context, camel-routes, and camel-consumers status.

![Health Panel](images/debugger_health.png)

### Real-time Metrics
- Monitor Camel microservices with essential metrics and variables, ensuring the health and performance of your application.
- Browse all available metrics in a detailed modal view, or filter metrics by keyword to quickly find the data you need.
- Visualize route exchange counts and traffic flow across your topology.

![Filtered Metrics](images/metrics_filtered_metrics.png)

### Metrics Charts
- Track CPU usage, GC average pauses, JVM memory usage (heap used vs heap max), and thread counts (live, daemon, peak) over time with real-time charts.
- Toggle between the topology view and charts view on the metrics page.

![Metrics Charts](images/metrics_charts.png)

### Settings
- Configure health and metrics URLs, refresh rates, metrics history duration, max characters in a text field, and theme (light/dark).

![Settings](images/settings_page.png)


---

## Project Structure

The project is structured as follows:

```shell
camelbee/
|-- common/                              # Shared build config (checkstyle, spotbugs, formatter)
|-- core/
|   |-- shared-core/                     # camelbee-core: the framework-neutral engine
|   |-- quarkus-core/                    # Quarkus-specific core module
|   |   |-- README.md
|   |-- springboot-core/                 # Spring Boot-specific core module
|   |   |-- README.md
|   |-- standalone-core/                 # Plain Camel (camel-main) core module
|   |   |-- README.md
|   |-- quarkus-core-camelk/             # quarkus-core's sources, built against Camel K's platform
|   |   |-- README.md
|-- dependencies/
|   |-- quarkus/                         # Quarkus BOM/dependency management
|   |-- springboot/                      # Spring Boot BOM/dependency management
|   |-- quarkus-camelk/                  # BOM pinned to the platform Camel K runs
|   |-- standalone/                      # Standalone (camel-main) BOM/dependency management
|-- examples/
|   |-- allcomponent-quarkus-sample/     # Quarkus example project
|   |   |-- README.md
|   |-- allcomponent-springboot-sample/  # Spring Boot example project
|   |   |-- README.md
|   |-- allcomponent-standalone-sample/  # Standalone (camel-main) example project
|   |   |-- README.md
|   |-- allcomponent-camelk-sample/      # Camel K integration sample (kamel CLI, not a Maven module)
|   |   |-- README.md
|   |-- core-only-quarkus-sample/        # Option 1 wiring test, oldest supported Quarkus
|   |   |-- README.md
|   |-- core-only-springboot-sample/     # Option 1 wiring test, oldest supported Spring Boot
|   |   |-- README.md
|   |-- core-only-standalone-sample/     # Option 1 wiring test, oldest supported Camel
|   |   |-- README.md
|-- parent/                              # Parent POM with shared build config
|-- security/
|   |-- quarkus-security/               # Quarkus security module
|   |-- springboot-security/            # Spring Boot security module
|-- starters/
|   |-- camelbee-quarkus-starter/       # Quarkus starter (use as parent)
|   |-- camelbee-springboot-starter/    # Spring Boot starter (use as parent)
|   |-- camelbee-standalone-starter/    # Standalone starter (use as parent)
|-- ui/                                  # Embedded React UI (route visualization, tracing, metrics)
|-- README.md
```

- `common`: Shared build configuration (Checkstyle, SpotBugs, formatter profiles) unpacked by the parent POM during the build.
- `core`: Contains the core modules for CamelBee that provide route tracing, event notification, and REST endpoints.
  - `shared-core` (`camelbee-core`): The framework-neutral engine — tracer, event notifier, intercept strategies, topology extraction, redaction and structured logging. It has no DI or HTTP layer of its own and scopes Camel as `provided`; the three runtime cores below depend on it and add only the wiring. See [How CamelBee works](docs/how-it-works.md).
  - `quarkus-core`: Quarkus-specific core module.
  - `springboot-core`: Spring Boot-specific core module.
  - `standalone-core`: Core module for plain standalone Camel applications (`camel-main`, no Spring Boot or Quarkus).
  - `quarkus-core-camelk`: Owns no sources — it recompiles `quarkus-core` and `shared-core` against the platform the Apache Camel K runtime pins (Quarkus 3.15.4 / Camel Quarkus 3.15.3), producing `camelbee-quarkus-core-camelk` for use from a Camel K integration modeline.
- `dependencies`: BOM (Bill of Materials) modules for dependency version management.
- `security`: Optional modules providing reusable JWT validation Camel routes (JWKS fetching/caching, token validation, authorization utilities).
  - `quarkus-security`: JWT validation routes for Camel Quarkus.
  - `springboot-security`: JWT validation routes for Camel Spring Boot.
- `starters`: Starter modules to use as parent projects for quick integration.
  - `camelbee-quarkus-starter`: Quarkus starter parent project.
  - `camelbee-springboot-starter`: Spring Boot starter parent project.
  - `camelbee-standalone-starter`: Standalone starter parent project.
- `ui`: Embedded React-based UI that is bundled into the core libraries and served directly from your application at the `/camelbee` path. Provides route visualization, message tracing, debugging with timeline replay, filtering, and metrics.
- `examples`: Example projects demonstrating the usage of CamelBee. Two families, differing in **how
  they integrate** and in **how much they show**:

  | Sample | Integrates via | Stack | Shows |
  |---|---|---|---|
  | `allcomponent-quarkus-sample` | Starter as parent (Option 2) | This project's | A wide, EIP-rich topology across many Camel components |
  | `allcomponent-springboot-sample` | Starter as parent (Option 2) | This project's | Same, for Spring Boot |
  | `allcomponent-standalone-sample` | Starter as parent (Option 2) | This project's | Same, for plain `camel-main` |
  | `allcomponent-camelk-sample` | `camelbee-quarkus-core-camelk` via modeline | Camel K's | The same infra-free topology as the standalone sample, in one integration file (`kamel` CLI, not a Maven module) |
  | `core-only-quarkus-sample` | Core as a dependency (Option 1) | **Oldest supported** | Nothing — deliberately minimal. A wiring test |
  | `core-only-springboot-sample` | Core as a dependency (Option 1) | **Oldest supported** | Same |
  | `core-only-standalone-sample` | Core as a dependency (Option 1) | **Oldest supported** | Same |

  The `allcomponent-*` samples are the ones to read to learn CamelBee. The `core-only-*` samples
  exist to be *run*: they are the only coverage of the Option 1 path, and pinning them to the floor
  is what keeps the version table above honest. See their READMEs.

Each subproject has its own README file for detailed information specific to that project.

> **Curious what it actually does inside your application?**
> [How CamelBee works](docs/how-it-works.md) covers the event notifier, the intercept strategies,
> what is stored and what never is, and how the UI reads it.

## Getting Started

> **Configuration in one place:** the snippets below show the minimum to get running. Every property
> CamelBee reads, with defaults, is listed in
> [All configuration properties](#all-configuration-properties).


There are three ways to integrate CamelBee into your project:

### Option 1: Add the Core Library as a Dependency (Recommended)

The recommended way for existing microservices. Add the CamelBee core library directly as a dependency from Maven Central — no local build needed, and it works alongside your existing parent POM.

> **Supported versions.** The core libraries are built for **JDK 17+** and declare their framework
> dependencies as `provided`, so **your** BOM decides every Camel / Spring Boot / Quarkus version —
> CamelBee adds nothing to your dependency graph but itself. You do not need to match the versions
> this project is built against, and you do not need to build a custom jar to stay on an older stack.
>
> | Artifact | Minimum |
> |---|---|
> | `camelbee-springboot-core` | **Camel 4.8+** · Spring Boot as your Camel release pairs with it (4.8 → 3.3, 4.16 → 3.5, 4.22 → 4.1) |
> | `camelbee-quarkus-core` | **Quarkus 3.15 LTS+** |
> | `camelbee-standalone-core` | **Camel 4.12+** |
> | all of the above | **JDK 17+** |
>
> Each is proved on every build by `examples/core-only-*-sample`, which pin exactly these versions.
> Below them, see [Option 3](#option-3-build-a-custom-core-library-custom-javacamel-versions).

**For Quarkus:**

*Example: [core-only-quarkus-sample](examples/core-only-quarkus-sample/README.md)*

Add the CamelBee core dependency:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-core</artifactId>
  <version>4.0.0</version>
</dependency>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true
  # A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
  # auth-enabled: false to leave it open (see Securing the CamelBee Endpoints).
  auth-enabled: true
  username: camelbee
  password: change-me
  # Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
  # the 19 built-in keys rather than adding to them, so list every key you still want;
  # set tracer-body-enabled: false to never capture bodies at all.
  masking-enabled: true
  # masked-keys: password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
  tracer-body-enabled: true

quarkus:
  http:
    port: 8080
  micrometer:
    export:
      prometheus:
        path: /metrics
  index-dependency:
    camelbeecore:
      group-id: io.camelbee
      artifact-id: camelbee-quarkus-core
```

**For Spring Boot:**

*Example: [core-only-springboot-sample](examples/core-only-springboot-sample/README.md)*

Add the CamelBee core dependency. `spring-boot-starter-web` and `camel-spring-boot-starter` are
`provided` — declare them yourself (at your own versions) if they aren't already in your POM:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-core</artifactId>
  <version>4.0.0</version>
</dependency>
<!-- supplied by your application, at your versions -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.apache.camel.springboot</groupId>
  <artifactId>camel-spring-boot-starter</artifactId>
</dependency>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true
  # A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
  # auth-enabled: false to leave it open (see Securing the CamelBee Endpoints).
  auth-enabled: true
  username: camelbee
  password: change-me
  # Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
  # the 19 built-in keys rather than adding to them, so list every key you still want;
  # set tracer-body-enabled: false to never capture bodies at all.
  masking-enabled: true
  # masked-keys: password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
  tracer-body-enabled: true

management:
  server:
    port: 8080
  security:
    enabled: false
  endpoints:
    web:
      exposure:
        include: '*'
      base-path: /
      path-mapping:
        prometheus: metrics
        metrics: metrics-default
```

Also add `org.camelbee` to your `@ComponentScan` to pick up CamelBee beans:
```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.camelbee", "your.application.package"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

**For Standalone (plain Camel / `camel-main`, no Spring Boot or Quarkus):**

*Example: [core-only-standalone-sample](examples/core-only-standalone-sample/README.md)*

> **Note:** For standalone projects without an existing parent POM, the starter (Option 2) is the recommended path — it brings `camel-platform-http-main` and Micrometer/Prometheus metrics automatically.

Add the CamelBee core dependency, together with `camel-platform-http-main` (CamelBee uses the camel-main management server to expose its UI and API):
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-core</artifactId>
  <version>4.0.0</version>
</dependency>
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-platform-http-main</artifactId>
</dependency>
```

Unlike Quarkus and Spring Boot, there is no dependency-injection container to auto-configure — attach CamelBee explicitly before the context starts:
```java
import org.apache.camel.main.Main;
import org.camelbee.CamelBee;

public final class Application {

  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.configure().addRoutesBuilder(new MyRoutes());
    // attach CamelBee monitoring (endpoints + tracer + notifier)
    CamelBee.register(main);
    main.run(args);
  }
}
```

Then add the following to your `application.properties`:
```properties
camelbee.notifier-enabled = true
camelbee.route-configurer-enabled = true
camelbee.context-enabled = true
camelbee.tracer-enabled = true
camelbee.tracer-max-idle-time = 60000
camelbee.tracer-max-messages-count = 10000
camelbee.metrics-enabled = true
camelbee.logging-enabled = false
# A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
# auth-enabled = false to leave it open (see Securing the CamelBee Endpoints).
camelbee.auth-enabled = true
camelbee.username = camelbee
camelbee.password = change-me
# Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
# the 19 built-in keys rather than adding to them, so list every key you still want;
# set tracer-body-enabled = false to never capture bodies at all.
camelbee.masking-enabled = true
# camelbee.masked-keys = password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
camelbee.tracer-body-enabled = true

# the application's own platform-http server (your routes)
camel.server.enabled = true
camel.server.host = 0.0.0.0
camel.server.port = 8080
```

`CamelBee.register(...)` enables the camel-main management server on port `8081` by default, and serves the CamelBee UI, REST API, and Prometheus metrics (at `/observe/metrics`) there — separate from your application's own routes, so they never appear in the route topology or message tracer.

**For Camel K:**

*Example: [allcomponent-camelk-sample](examples/allcomponent-camelk-sample/README.md)*

Camel K runs integrations on the **Camel Quarkus** runtime, but pins an older Camel than this project's main build (Camel 4.8.5 vs 4.21). Use `camelbee-quarkus-core-camelk` — the same sources built against Camel K's baseline. Declare everything in your integration file's modeline (the core's CDI beans are auto-discovered because the jar ships a Jandex index):

```java
// camel-k: dependency=mvn:io.camelbee:camelbee-quarkus-core-camelk:4.0.0
// camel-k: dependency=camel:direct
// camel-k: dependency=camel:log
// camel-k: build-property=camelbee.context-enabled=true
// camel-k: build-property=camelbee.tracer-enabled=true
// camel-k: property=camelbee.tracer-enabled=true
// camel-k: trait=service.enabled=true
```

The REST/Jackson stack comes transitively with this core, so it does not need declaring. Declare every component you use explicitly, though: Camel K's auto-detection reads URIs at the `from(...)`/`to(...)` call site and misses any built from a constant, which then fails at runtime rather than at build time.

The **default operator image works** — this core is built for JDK 17, so the `-21-jdk` operator flavour is no longer required.

Run it with `kamel run YourRoute.java`, then expose the HTTP port (e.g. `kubectl port-forward svc/your-route 8080:80`) and open `http://localhost:8080/camelbee`. See the [Camel K sample](examples/allcomponent-camelk-sample/README.md) for a complete, cluster-verified integration and a full local setup walkthrough.

**Then call the route configurer from every `RouteBuilder`.** This is required, not optional: it
installs the intercept strategies that record per-node hops and `poll()` / `pollEnrich()` edges, and
turns on stream caching and the MDC unit of work. It must run before the routes are reified, so make
it the first statement in `configure()`. Without it CamelBee starts and draws the topology, but
message tracing and the waterfall are incomplete.

```java
public class YourRoute extends RouteBuilder {

  private final CamelBeeRouteConfigurer camelBeeRouteConfigurer;

  public YourRoute(CamelBeeRouteConfigurer camelBeeRouteConfigurer) {
    this.camelBeeRouteConfigurer = camelBeeRouteConfigurer;
  }

  @Override
  public void configure() {
    camelBeeRouteConfigurer.configureRoute(this);
    // ... your routes
  }
}
```

> On **standalone** (`camel-main`) there is no configurer - `CamelBee.register(main)` does the same
> work before the context starts.

### Option 2: Use a CamelBee Starter as Parent (New projects only)
> **What you get.** The starter pins the whole stack — you do not choose these, and overriding them
> is not supported:
>
> | Starter | Pins |
> |---|---|
> | `camelbee-springboot-starter` | Spring Boot 4.1.0 · Camel 4.22.0 |
> | `camelbee-standalone-starter` | Camel 4.22.0 |
> | `camelbee-quarkus-starter` | Quarkus 3.38.2 · Camel 4.21.0 (the platform decides the Camel version) |
>
> That is the trade-off against [Option 1](#option-1-add-the-core-library-as-a-dependency-recommended): the starter decides your framework versions, so
> your stack moves when CamelBee releases. If you need to stay on your own versions, use the core as
> a dependency instead.


Only suitable for new projects without an existing parent POM. The starters are available on Maven Central and automatically include the core library, embedded UI, and all required dependencies — including all dependency version management. No local build needed.

**For Quarkus:**

*Example: [allcomponent-quarkus-sample](examples/allcomponent-quarkus-sample/README.md)*

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-starter</artifactId>
  <version>4.0.0</version>
</parent>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true
  # A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
  # auth-enabled: false to leave it open (see Securing the CamelBee Endpoints).
  auth-enabled: true
  username: camelbee
  password: change-me
  # Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
  # the 19 built-in keys rather than adding to them, so list every key you still want;
  # set tracer-body-enabled: false to never capture bodies at all.
  masking-enabled: true
  # masked-keys: password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
  tracer-body-enabled: true

quarkus:
  http:
    port: 8080
  micrometer:
    export:
      prometheus:
        path: /metrics
  index-dependency:
    camelbeecore:
      group-id: io.camelbee
      artifact-id: camelbee-quarkus-core
```

**For Spring Boot:**

*Example: [allcomponent-springboot-sample](examples/allcomponent-springboot-sample/README.md)*

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-starter</artifactId>
  <version>4.0.0</version>
</parent>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true
  # A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
  # auth-enabled: false to leave it open (see Securing the CamelBee Endpoints).
  auth-enabled: true
  username: camelbee
  password: change-me
  # Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
  # the 19 built-in keys rather than adding to them, so list every key you still want;
  # set tracer-body-enabled: false to never capture bodies at all.
  masking-enabled: true
  # masked-keys: password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
  tracer-body-enabled: true

management:
  server:
    port: 8080
  security:
    enabled: false
  endpoints:
    web:
      exposure:
        include: '*'
      base-path: /
      path-mapping:
        prometheus: metrics
        metrics: metrics-default
```

Also add `org.camelbee` to your `@ComponentScan` to pick up CamelBee beans:
```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.camelbee", "your.application.package"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

**For Standalone (plain Camel / `camel-main`, no Spring Boot or Quarkus):**

*Example: [allcomponent-standalone-sample](examples/allcomponent-standalone-sample/README.md)*

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-starter</artifactId>
  <version>4.0.0</version>
</parent>
```

The starter automatically includes `camel-platform-http-main` and Micrometer/Prometheus support. As with Option 1, attach CamelBee explicitly before the context starts:
```java
import org.apache.camel.main.Main;
import org.camelbee.CamelBee;

public final class Application {

  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.configure().addRoutesBuilder(new MyRoutes());
    // attach CamelBee monitoring (endpoints + tracer + notifier)
    CamelBee.register(main);
    main.run(args);
  }
}
```

Then add the following to your `application.properties`:
```properties
camelbee.notifier-enabled = true
camelbee.route-configurer-enabled = true
camelbee.context-enabled = true
camelbee.tracer-enabled = true
camelbee.tracer-max-idle-time = 60000
camelbee.tracer-max-messages-count = 10000
camelbee.metrics-enabled = true
camelbee.logging-enabled = false
# A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
# auth-enabled = false to leave it open (see Securing the CamelBee Endpoints).
camelbee.auth-enabled = true
camelbee.username = camelbee
camelbee.password = change-me
# Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
# the 19 built-in keys rather than adding to them, so list every key you still want;
# set tracer-body-enabled = false to never capture bodies at all.
camelbee.masking-enabled = true
# camelbee.masked-keys = password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
camelbee.tracer-body-enabled = true

# the application's own platform-http server (your routes)
camel.server.enabled = true
camel.server.host = 0.0.0.0
camel.server.port = 8080
```

For working examples using the starters, see the [camelbee-examples](https://github.com/camelbee/camelbee-examples) repository.

### Option 3: Build a Custom Core Library (Custom Java/Camel Versions)

**Most projects do not need this.** The published cores already run on JDK 17+ and across a wide
Camel range (see the version table in [Option 1](#option-1-add-the-core-library-as-a-dependency-recommended)) —
Option 1 works without building anything.

Use this only if you are **below** the supported floor (JDK 16 or older, Camel older than 4.8, or
older than 4.12 for standalone), or you need a patched build. Build the core independently with the
provided `pom-custom.xml` and add it as a dependency.

**For Quarkus:** build with `mvn -f pom-custom.xml clean install` in `core/quarkus-core/`, then add:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-core-custom</artifactId>
  <version>4.0.0</version>
</dependency>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true
  # A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
  # auth-enabled: false to leave it open (see Securing the CamelBee Endpoints).
  auth-enabled: true
  username: camelbee
  password: change-me
  # Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
  # the 19 built-in keys rather than adding to them, so list every key you still want;
  # set tracer-body-enabled: false to never capture bodies at all.
  masking-enabled: true
  # masked-keys: password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
  tracer-body-enabled: true

quarkus:
  http:
    port: 8080
  micrometer:
    export:
      prometheus:
        path: /metrics
  index-dependency:
    camelbeecore:
      group-id: io.camelbee
      artifact-id: camelbee-quarkus-core-custom
```

**For Spring Boot:** build with `mvn -f pom-custom.xml clean install` in `core/springboot-core/`, then add:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-core-custom</artifactId>
  <version>4.0.0</version>
</dependency>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true
  # A login is required by default. Set a password (or CAMELBEE_PASSWORD), or set
  # auth-enabled: false to leave it open (see Securing the CamelBee Endpoints).
  auth-enabled: true
  username: camelbee
  password: change-me
  # Sensitive values are redacted at the point of capture. Setting masked-keys REPLACES
  # the 19 built-in keys rather than adding to them, so list every key you still want;
  # set tracer-body-enabled: false to never capture bodies at all.
  masking-enabled: true
  # masked-keys: password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
  tracer-body-enabled: true

management:
  server:
    port: 8080
  security:
    enabled: false
  endpoints:
    web:
      exposure:
        include: '*'
      base-path: /
      path-mapping:
        prometheus: metrics
        metrics: metrics-default
```

Also add `org.camelbee` to your `@ComponentScan` to pick up CamelBee beans:
```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.camelbee", "your.application.package"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

**For Standalone (plain Camel / `camel-main`, no Spring Boot or Quarkus):** build with `mvn -f pom-custom.xml clean install` in `core/standalone-core/`, then add:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-core-custom</artifactId>
  <version>4.0.0</version>
</dependency>
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-platform-http-main</artifactId>
</dependency>
```

Wiring and `application.properties` configuration are the same as in Option 1 above — attach CamelBee explicitly with `CamelBee.register(main)` before the context starts.

Once your application is running, the CamelBee UI is available at: `http://localhost:8080/camelbee/` (Quarkus/Spring Boot) or `http://localhost:8081/camelbee/` (Standalone, served on the camel-main management server). Its pages can be bookmarked, shared and reloaded — every runtime answers a client-side route such as `/camelbee/settings` with the application itself.

### Running CamelBee Outside Development

The snippets above are enough to get started, and the production-safety defaults are already correct
— tracing starts off, stops itself when idle, and sensitive values are redacted without any
configuration. Three further properties are available when you need them:

```properties
# redact configured keys out of traced headers and bodies (default: true)
camelbee.masking-enabled = true
# your own comma-separated key list, replacing the built-in one
camelbee.masked-keys = password,passwd,secret,token,authorization,auth,apikey,accesskey,privatekey,credential,creditcard,cardnumber,cardno,cvv,cvc,iban,ssn,pin,otp,nationalId
# never capture body text at all - the only hard guarantee (default: true)
camelbee.tracer-body-enabled = true
```

To trace one transaction in a busy application, type an order id or correlation id into the amber
**Only trace containing…** box in the toolbar before starting tracing: only the flow containing it is
recorded, along with the branches that flow spawns.

See [Using CamelBee in Production](https://github.com/camelbee/camelbee/blob/main/docs/camelbee_userguide.md#using-camelbee-in-production)
in the User Guide, and the Configuration section of your runtime's core README, for what these
guarantee and — just as importantly — what they do not.

### All configuration properties

Every property CamelBee reads, with its default. The snippets above show only what you need to get
started; nothing else has to be set. Same names on all runtimes — `camelbee.*` in
`application.properties`, or nested under `camelbee:` in `application.yaml`.

| Property | Default | What it does |
|---|---|---|
| `camelbee.context-enabled` | `false` | Serves the topology API and the embedded UI. Turn on to use CamelBee at all. |
| `camelbee.notifier-enabled` | `true` | Event notifier behind message tracing and the waterfall. |
| `camelbee.route-configurer-enabled` | `true` | Attaches the interceptors that record hops. |
| `camelbee.tracer-enabled` | `false` | Enables the tracer at startup. Off by design — turn it on from the UI instead, no restart needed. |
| `camelbee.tracer-max-idle-time` | `300000` | Milliseconds of inactivity before the tracer switches itself off. |
| `camelbee.tracer-max-messages-count` | `1000` | Cap on retained traced messages; the UI warns when it is hit. |
| `camelbee.tracer-body-enabled` | `true` | Set `false` to never capture body text at all — the only hard guarantee. |
| `camelbee.masking-enabled` | `true` | Redacts sensitive values out of traced headers and bodies at the point of capture. |
| `camelbee.masked-keys` | *(19 built-in keys)* | Comma-separated list that **replaces** the built-in one, rather than adding to it: `password, passwd, secret, token, authorization, auth, apikey, accesskey, privatekey, credential, creditcard, cardnumber, cardno, cvv, cvc, iban, ssn, pin, otp`. A key is matched whole-word and separator-insensitively, so `apikey` covers `X-Api-Key`, `api_key` and `apiKey`, and `userPassword` is caught by `password` - but `shipping` is not caught by `pin`. |
| `camelbee.auth-enabled` | `true` | Requires a login for the UI and REST API. Leave on outside a laptop. |
| `camelbee.username` | `camelbee` | Login user when authentication is on. |
| `camelbee.password` | *(none)* | Login password. Set it, or via `CAMELBEE_PASSWORD`. No default — auth cannot be used until it is set. |
| `camelbee.session-timeout` | `120000` | Milliseconds a UI session stays valid. |
| `camelbee.logging-enabled` | `false` | Structured request/response logging alongside tracing. |
| `camelbee.metrics-enabled` | `true` | Standalone only — exposes the Prometheus scrape on the management server. |
| `camelbee.cors-allowed-origin` | *(none)* | Standalone only — allows a browser origin other than the app's own. |

JWT validation for **your own** routes is a separate, optional module with its own
`camelbee.security.*` properties — see the [security README](security/README.md).

The **capture filter** ("Only trace containing…") is deliberately not a property: it is set at
runtime from the UI or the REST API, so you can narrow to one transaction without a restart.

### Securing the CamelBee Endpoints

Once `camelbee.context-enabled` is set, CamelBee publishes its UI and REST API under `/camelbee` —
on your application's own port for Quarkus and Spring Boot, and on the camel-main management server
(default `8081`) for standalone.

**Since 4.0 those endpoints require a login by default.** `camelbee.auth-enabled` defaults to `true`,
and nothing is readable without a token.

![Login screen](images/login_screen.png)

That default exists because the API is not read-only. `GET /camelbee/routes` returns the full route
topology including internal hostnames and queue names; `POST /camelbee/tracer/status` **turns tracing
on**, so without a gate a caller can enable capture themselves and then read the traffic; and
`GET /camelbee/messages` returns captured bodies, headers and error text. Redaction governs *what is
recorded*, authentication governs *who may read it* — you want both.

| Property | Default | Meaning |
| --- | --- | --- |
| `camelbee.auth-enabled` | **`true`** | Require a login. Setting it `false` logs a warning at startup. |
| `camelbee.username` | `camelbee` | The login name. Not a secret. |
| `camelbee.password` | *(generated)* | Blank means one is generated at startup and written to the log. |
| `camelbee.session-timeout` | `120000` (2 min) | Idle window. Each request re-issues the token, so an active session never expires and an abandoned one does. |

**There is deliberately no default password.** A documented default protects nobody while looking as
though it does, so with none configured CamelBee generates one per start and logs it:

```text
WARN  CamelBee UI is protected. Generated password for user 'camelbee': f1d4a6a2-4478-49a9-90e7-…
WARN  Set camelbee.password (or CAMELBEE_PASSWORD) to use your own. Note that each replica
      generates its own, so a multi-instance deployment must configure one.
```

Zero config on a laptop — you read it from your own log. For anything shared, set one and keep it out
of the file:

```properties
camelbee.username = ${CAMELBEE_USERNAME:camelbee}
camelbee.password = ${CAMELBEE_PASSWORD:}
```

Three limits worth stating plainly. One shared credential is a **gate, not an identity** — no
per-user audit, no revocation before expiry; if you need SSO, set `camelbee.auth-enabled=false` and
put your host framework's security in front of `/camelbee/**` instead. **Use TLS**, or the password
and token are readable in transit. And the **UI shell loads without a token** by necessity, since a
browser must load the application before it can show a login form — every byte of *data* is behind
the guard.

> Authentication is defence in depth, not a licence to publish a debugging interface. Keep
> `/camelbee` off the public edge — an internal address, a VPN or a port-forward — with the login as
> the second line rather than the only one.

Per-runtime detail, including how to use your own security instead and how to remove the endpoints
altogether, is in the **Securing the CamelBee endpoints** section of your core README:
[Quarkus](https://github.com/camelbee/camelbee/blob/main/core/quarkus-core/README.md#securing-the-camelbee-endpoints) ·
[Spring Boot](https://github.com/camelbee/camelbee/blob/main/core/springboot-core/README.md#securing-the-camelbee-endpoints) ·
[Standalone](https://github.com/camelbee/camelbee/blob/main/core/standalone-core/README.md#securing-the-camelbee-endpoints)

### Detailed Documentation

- **How it works:** [How CamelBee works](https://github.com/camelbee/camelbee/blob/main/docs/how-it-works.md) — the event notifier, the intercept strategies, what is stored and what never is, and how the UI reads it
- **User Guide:** [CamelBee User Guide](https://github.com/camelbee/camelbee/blob/main/docs/camelbee_userguide.md)
- **Quarkus:** [CamelBee Quarkus Core README](https://github.com/camelbee/camelbee/blob/main/core/quarkus-core/README.md)
- **Spring Boot:** [CamelBee SpringBoot Core README](https://github.com/camelbee/camelbee/blob/main/core/springboot-core/README.md)
- **Standalone:** [CamelBee Standalone Core README](https://github.com/camelbee/camelbee/blob/main/core/standalone-core/README.md)
- **Camel K:** [CamelBee Camel K Sample README](https://github.com/camelbee/camelbee/blob/main/examples/allcomponent-camelk-sample/README.md) · [CamelBee Quarkus Core (Camel K runtime) README](https://github.com/camelbee/camelbee/blob/main/core/quarkus-core-camelk/README.md)
- **Security (JWT validation routes):** [CamelBee Security README](https://github.com/camelbee/camelbee/blob/main/security/README.md)
- **Embedded UI development:** [CamelBee UI README](https://github.com/camelbee/camelbee/blob/main/ui/README.md)

## License

This project is licensed under the the Apache License, Version 2.0. Feel free to use, modify, and distribute it as per the license terms.

For specific license information for individual subprojects, refer to their respective README files.