# CamelBee - Apache Camel Library for Microservices Monitoring and Debugging

CamelBee java core libraries are engineered to extract the architecture of Camel Routes, pinpoint endpoints, and map out the interconnections among them
to visualize the topology within the **embedded CamelBee UI** served directly from your application.

![Debugger Page](images/debugger_page.png)

## Features

### Route Visualization
- Effortlessly visualize complex Camel routes and their interconnections as an interactive topology graph for a better understanding of your microservice architecture.
- Gain a clear overview of message routing and flow paths within your application, with color-coded routes and animated dashed lines showing message traversal.
- Trigger consumer endpoints directly from the UI to start tracing sessions and observe route behavior in real time.

### Message Tracing & Debugging
- Trace messages as they traverse through Camel routes, enabling real-time debugging and issue identification.
- Inspect full request and response message contents including headers and body in the side panel.
- Detect bottlenecks, errors, or unexpected behavior in your message processing.
- Navigate through the debugging session's timeline using the timeline bar at the bottom, moving back and forth to thoroughly analyze the process flow.
- Filter traced messages to focus on specific routes or endpoints.

![Message Tracing](images/debugger_messages.png)

### Health Monitoring
- View the health status of your microservice at a glance with the built-in health panel, showing context name, framework version, Camel version, JVM, and garbage collector information.
- Inspect detailed health check results in a modal dialog displaying the full health JSON response including camel-context, camel-routes, and camel-consumers status.

![Health Panel](images/debugger_health.png)

### Real-time Metrics
- Monitor Camel microservices with essential metrics and variables, ensuring the health and performance of your application.
- Browse all available metrics in a detailed modal view, or filter metrics by keyword to quickly find the data you need.
- Visualize route exchange counts and traffic flow across your topology.
- Concurrently invoke consumer endpoints to conduct a stress test.

![All Metrics](images/metrics_all_metrics.png)

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
|   |-- quarkus-core/                    # Quarkus-specific core module
|   |   |-- README.md
|   |-- springboot-core/                 # Spring Boot-specific core module
|   |   |-- README.md
|   |-- standalone-core/                 # Plain Camel (camel-main) core module
|   |   |-- README.md
|-- dependencies/
|   |-- quarkus/                         # Quarkus BOM/dependency management
|   |-- springboot/                      # Spring Boot BOM/dependency management
|   |-- standalone/                      # Standalone (camel-main) BOM/dependency management
|-- examples/
|   |-- allcomponent-quarkus-sample/     # Quarkus example project
|   |   |-- README.md
|   |-- allcomponent-springboot-sample/  # Spring Boot example project
|   |   |-- README.md
|   |-- allcomponent-standalone-sample/  # Standalone (camel-main) example project
|   |   |-- README.md
|   |-- camelk-sample/                   # Camel K integration sample (kamel CLI, not a Maven module)
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
  - `quarkus-core`: Quarkus-specific core module.
  - `springboot-core`: Spring Boot-specific core module.
  - `standalone-core`: Core module for plain standalone Camel applications (`camel-main`, no Spring Boot or Quarkus).
- `dependencies`: BOM (Bill of Materials) modules for dependency version management.
- `security`: Optional modules providing reusable JWT validation Camel routes (JWKS fetching/caching, token validation, authorization utilities).
  - `quarkus-security`: JWT validation routes for Camel Quarkus.
  - `springboot-security`: JWT validation routes for Camel Spring Boot.
- `starters`: Starter modules to use as parent projects for quick integration.
  - `camelbee-quarkus-starter`: Quarkus starter parent project.
  - `camelbee-springboot-starter`: Spring Boot starter parent project.
  - `camelbee-standalone-starter`: Standalone starter parent project.
- `ui`: Embedded React-based UI that is bundled into the core libraries and served directly from your application at the `/camelbee` path. Provides route visualization, message tracing, debugging, replay, filtering, endpoint triggering, and metrics.
- `examples`: Contains example projects demonstrating the usage of CamelBee.
  - `allcomponent-quarkus-sample`: Quarkus example project which uses `camelbee-quarkus-starter` as parent.
  - `allcomponent-springboot-sample`: Spring Boot example project which uses `camelbee-springboot-starter` as parent.
  - `allcomponent-standalone-sample`: Standalone example project which uses `camelbee-standalone-starter` as parent.
  - `camelk-sample`: Camel K integration sample run with the `kamel` CLI — Camel K runs on the Camel Quarkus runtime, so `camelbee-quarkus-core` works there unchanged (no separate module needed).

Each subproject has its own README file for detailed information specific to that project.

## Getting Started

There are three ways to integrate CamelBee into your project:

### Option 1: Add the Core Library as a Dependency (Recommended)

The recommended way for existing microservices. Add the CamelBee core library directly as a dependency from Maven Central — no local build needed, and it works alongside your existing parent POM.

> **Note:** This library requires Quarkus 3.x+ and Camel Quarkus 3.x+ (or Spring Boot 3.x+ and Camel Spring Boot 4.x+, or a plain Camel application on `camel-main` 4.x+). Your existing BOMs should satisfy this — no changes needed if your project already targets these versions.

**For Quarkus:**

Add the CamelBee core dependency:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-core</artifactId>
  <version>3.3.1</version>
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

Add the CamelBee core dependency:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-core</artifactId>
  <version>3.3.1</version>
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

> **Note:** For standalone projects without an existing parent POM, the starter (Option 2) is the recommended path — it brings `camel-platform-http-main` and Micrometer/Prometheus metrics automatically.

Add the CamelBee core dependency, together with `camel-platform-http-main` (CamelBee uses the camel-main management server to expose its UI and API):
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-core</artifactId>
  <version>3.3.1</version>
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

# the application's own platform-http server (your routes)
camel.server.enabled = true
camel.server.host = 0.0.0.0
camel.server.port = 8080
```

`CamelBee.register(...)` enables the camel-main management server on port `8081` by default, and serves the CamelBee UI, REST API, and Prometheus metrics (at `/observe/metrics`) there — separate from your application's own routes, so they never appear in the route topology or message tracer.

**For Camel K:**

Camel K runs integrations on the **Camel Quarkus** runtime, so `camelbee-quarkus-core` works on Camel K unchanged — no separate module or local build needed. Declare everything in your integration file's modeline (the core's CDI beans are auto-discovered because the jar ships a Jandex index):

```java
// camel-k: dependency=mvn:io.camelbee:camelbee-quarkus-core:3.3.1
// camel-k: dependency=mvn:org.apache.camel.quarkus:camel-quarkus-rest
// camel-k: dependency=mvn:org.apache.camel.quarkus:camel-quarkus-jackson
// camel-k: dependency=mvn:io.quarkus:quarkus-resteasy-jackson
// camel-k: build-property=camelbee.context-enabled=true
// camel-k: build-property=camelbee.tracer-enabled=true
// camel-k: property=camelbee.tracer-enabled=true
// camel-k: trait=service.enabled=true
```

Run it with `kamel run YourRoute.java`, then expose the HTTP port (e.g. `kubectl port-forward svc/your-route 8080:80`) and open `http://localhost:8080/camelbee`. See the [Camel K sample](examples/camelk-sample/README.md) for a complete working integration, including how to use the starter instead of the core.

### Option 2: Use a CamelBee Starter as Parent (New projects only)

Only suitable for new projects without an existing parent POM. The starters are available on Maven Central and automatically include the core library, embedded UI, and all required dependencies — including all dependency version management. No local build needed.

**For Quarkus:**
```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-starter</artifactId>
  <version>3.3.1</version>
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
```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-starter</artifactId>
  <version>3.3.1</version>
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
```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-starter</artifactId>
  <version>3.3.1</version>
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

# the application's own platform-http server (your routes)
camel.server.enabled = true
camel.server.host = 0.0.0.0
camel.server.port = 8080
```

For working examples using the starters, see the [camelbee-examples](https://github.com/camelbee/camelbee-examples) repository.

### Option 3: Build a Custom Core Library (Custom Java/Camel Versions)

If you need to customize Java or Camel versions, you can build the core library independently using the provided `pom-custom.xml` and add it as a dependency.

**For Quarkus:** build with `mvn -f pom-custom.xml clean install` in `core/quarkus-core/`, then add:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-core-custom</artifactId>
  <version>3.3.1</version>
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
  <version>3.3.1</version>
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
  <version>3.3.1</version>
</dependency>
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-platform-http-main</artifactId>
</dependency>
```

Wiring and `application.properties` configuration are the same as in Option 1 above — attach CamelBee explicitly with `CamelBee.register(main)` before the context starts.

Once your application is running, the CamelBee UI is available at: `http://localhost:8080/camelbee/index.html` (Quarkus/Spring Boot) or `http://localhost:8081/camelbee` (Standalone, served on the camel-main management server).

### Detailed Documentation

- **User Guide:** [CamelBee User Guide](https://github.com/camelbee/camelbee/blob/main/docs/camelbee_userguide.md)
- **Quarkus:** [CamelBee Quarkus Core README](https://github.com/camelbee/camelbee/blob/main/core/quarkus-core/README.md)
- **Spring Boot:** [CamelBee SpringBoot Core README](https://github.com/camelbee/camelbee/blob/main/core/springboot-core/README.md)
- **Standalone:** [CamelBee Standalone Core README](https://github.com/camelbee/camelbee/blob/main/core/standalone-core/README.md)
- **Camel K:** [CamelBee Camel K Sample README](https://github.com/camelbee/camelbee/blob/main/examples/camelk-sample/README.md)
- **Security (JWT validation routes):** [CamelBee Security README](https://github.com/camelbee/camelbee/blob/main/security/README.md)
- **Embedded UI development:** [CamelBee UI README](https://github.com/camelbee/camelbee/blob/main/ui/README.md)

## License

This project is licensed under the the Apache License, Version 2.0. Feel free to use, modify, and distribute it as per the license terms.

For specific license information for individual subprojects, refer to their respective README files.