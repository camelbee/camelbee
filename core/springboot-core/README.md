# CamelBee SpringBoot Core Library

## Introduction

The camelbee-springboot-core library is an essential component for integrating a Camel SpringBoot Microservice with the CamelBee ecosystem.
It comes with an **embedded React UI** served directly from your application, providing route visualization, message tracing, debugging, and metrics.
This library provides the necessary functionalities to configure Camel routes with event notifiers, allowing comprehensive tracing of messages exchanged between the routes.

## Installation

There are three ways to integrate CamelBee into your Spring Boot project:

### Option 1: Use CamelBee Starter as Parent (Recommended)

The easiest way to get started. Simply use `camelbee-springboot-starter` as your project's parent POM — it is available on Maven Central and automatically includes the core library, embedded UI, and all required dependencies. No local build needed:

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-starter</artifactId>
  <version>3.0.2</version>
</parent>
```

For working examples using the starter, see the [camelbee-examples](https://github.com/camelbee/camelbee-examples) repository.

### Option 2: Add the Core Library as a Dependency from Maven Central

If your project already has a parent POM, you can add the CamelBee core library directly as a dependency from Maven Central. No local build needed.

> **Note:** Since you are not using the CamelBee starter as parent, you must manage your own dependency versions and compiler settings. Make sure to set the Java version and import the Spring Boot and Camel Spring Boot BOMs (adjust versions to match your project):

```xml
<properties>
  <spring-boot.version>3.5.9</spring-boot.version>
  <camel.version>4.16.0</camel.version>
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
</properties>
```

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>${spring-boot.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.apache.camel.springboot</groupId>
      <artifactId>camel-spring-boot-bom</artifactId>
      <version>${camel.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Then add the CamelBee core dependency:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-core</artifactId>
  <version>3.0.2</version>
</dependency>
```

### Option 3: Build a Custom Core Library (Custom Java/Camel Versions)

If you need to customize Java and Camel Spring Boot versions, you can build and use `camelbee-springboot-core-custom` independently. This approach uses the provided `pom-custom.xml`, which allows you to adjust versions to match your existing project setup.

1. Build the core library with the custom POM:

```bash
mvn -f pom-custom.xml clean install    # run in ./camelbee/core/springboot-core
```

2. Add the dependency to your project's `pom.xml`:

```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-core-custom</artifactId>
  <version>3.0.2</version>
</dependency>
```

## Configuration

### Configure Your Camel Routes with CamelBeeRouteConfigurer

Regardless of which installation option you chose, you must configure each of your Camel routes with `CamelBeeRouteConfigurer` to enable tracing, stream caching, and the embedded UI. Inject the configurer and call it at the beginning of your `configure()` method:

```java
@Component
public class MusicianRoute extends RouteBuilder {

    @Autowired
    CamelBeeRouteConfigurer camelBeeRouteConfigurer;

    @Override
    public void configure() throws Exception {

        camelBeeRouteConfigurer.configureRoute(this);

        // your route definitions...
    }
}
```

### Enable CamelBee Features

To enable specific features of the CamelBee library, add/modify the following properties in your `application.yaml` file:

```yaml
camelbee:
  # when enabled registers the CamelBee event notifier to the Camel context
  notifier-enabled: true
  # when enabled configures stream caching, MDC logging and CamelBeeUnitOfWork for routes
  route-configurer-enabled: true
  # when enabled it allows the CamelBee UI to fetch the topology of the Camel Context.
  context-enabled: true
  # when enabled intercepts/traces request and responses of all camel components and caches messages.
  tracer-enabled: true
  # maximum time the tracer can remain idle before deactivation tracing of messages.
  tracer-max-idle-time: 60000
  # maximum collected trace messages
  tracer-max-messages-count: 10000
  # when enabled it logs the messages exchanged between endpoints
  logging-enabled: true
```


### Enable Metrics

To enable metrics, adjust the following properties in your `application.yaml` file:

```
management:
  server:
    port: 8080
  security:
    enabled: false
  # expose actuator endpoint via HTTP for info,health,camelroutes
  endpoints:
    web:
      exposure:
        include: '*'
      base-path: /
      path-mapping:
        prometheus: metrics
        metrics: metrics-default
```

### Enable CamelBee Spring Beans

Add "org.camelbee" package to your ComponentScan folder of Spring like in the example project below:
```
/**
 * CamelBee Rest microservice example.
 */
@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {"org.camelbee", "io.camelbee.springboot.example"})
public class CamelBeeApplication {

  /**
   * A main method to start this application.
   */
  public static void main(String[] args) {
    SpringApplication.run(CamelBeeApplication.class, args);
  }

}
```

## Accessing the Embedded UI

Once your application is running, the embedded CamelBee UI is available at:

`http://localhost:8080/camelbee/index.html`

This provides route visualization, message tracing, debugging with replay, filtering, endpoint triggering, and metrics directly in your browser.

## Example Implementation

Discover a practical and functional application of this core library within the 'allcomponent-springboot-sample' Maven project showcased below as a successful and operational example:

```shell
camelbee/
|-- core/
|   |-- springboot-core/
|   |   |-- ...
|-- examples/
|   |-- allcomponent-springboot-sample/
|   |   |-- ...
```