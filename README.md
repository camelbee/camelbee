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
|-- common/                              # Shared utilities and common code
|-- core/
|   |-- quarkus-core/                    # Quarkus-specific core module
|   |   |-- README.md
|   |-- springboot-core/                 # Spring Boot-specific core module
|   |   |-- README.md
|-- dependencies/
|   |-- quarkus/                         # Quarkus BOM/dependency management
|   |-- springboot/                      # Spring Boot BOM/dependency management
|-- examples/
|   |-- allcomponent-quarkus-sample/     # Quarkus example project
|   |   |-- README.md
|   |-- allcomponent-springboot-sample/  # Spring Boot example project
|   |   |-- README.md
|-- parent/                              # Parent POM with shared build config
|-- security/
|   |-- quarkus-security/               # Quarkus security module
|   |-- springboot-security/            # Spring Boot security module
|-- starters/
|   |-- camelbee-quarkus-starter/       # Quarkus starter (use as parent)
|   |-- camelbee-springboot-starter/    # Spring Boot starter (use as parent)
|-- ui/                                  # Embedded React UI (route visualization, tracing, metrics)
|-- README.md
```

- `common`: Shared utilities used across core modules.
- `core`: Contains the core modules for CamelBee that provide route tracing, event notification, and REST endpoints.
  - `quarkus-core`: Quarkus-specific core module.
  - `springboot-core`: Spring Boot-specific core module.
- `dependencies`: BOM (Bill of Materials) modules for dependency version management.
- `security`: Security modules for CORS and endpoint protection.
  - `quarkus-security`: Quarkus security configuration.
  - `springboot-security`: Spring Boot security configuration.
- `starters`: Starter modules to use as parent projects for quick integration.
  - `camelbee-quarkus-starter`: Quarkus starter parent project.
  - `camelbee-springboot-starter`: Spring Boot starter parent project.
- `ui`: Embedded React-based UI that is bundled into the core libraries and served directly from your application at the `/camelbee` path. Provides route visualization, message tracing, debugging, replay, filtering, endpoint triggering, and metrics.
- `examples`: Contains example projects demonstrating the usage of CamelBee.
  - `allcomponent-quarkus-sample`: Quarkus example project which uses `camelbee-quarkus-starter` as parent.
  - `allcomponent-springboot-sample`: Spring Boot example project which uses `camelbee-springboot-starter` as parent.

Each subproject has its own README file for detailed information specific to that project.

## Getting Started For Quarkus Projects:

For more details, please refer to the [CamelBee Quarkus Core README](https://github.com/egekaraosmanoglu/camelbee/blob/main/core/quarkus-core/README.md).

## Getting Started For SpringBoot Projects:

For more details, please refer to the [CamelBee SpringBoot Core README](https://github.com/egekaraosmanoglu/camelbee/blob/main/core/springboot-core/README.md).

## License

This project is licensed under the the Apache License, Version 2.0. Feel free to use, modify, and distribute it as per the license terms.

For specific license information for individual subprojects, refer to their respective README files.