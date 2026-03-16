# CamelBee Quarkus Core Library

## Introduction

The camelbee-quarkus-core library is an essential component for integrating a Camel Quarkus Microservice with the CamelBee ecosystem.
It comes with an **embedded React UI** served directly from your application, providing route visualization, message tracing, debugging, and metrics.
This library provides the necessary functionalities to configure Camel routes with event notifiers, allowing comprehensive tracing of messages exchanged between the routes.

## Manual Installation

To manually install the core library, follow the steps below:

### Maven Installation

run `mvn clean install` command in the topmost parent folder "./camelbee"

Once the maven artifact is created, you can include it in your project by adding the following dependency to your pom.xml as the parent project:

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-starter</artifactId>
  <version>3.0.0</version>
</parent>
```

### Custom Maven Installation: Adding Core Library Directly Without CamelBee Starter Parent

If you'd rather not use `camelbee-quarkus-starter` as your parent project, you can build and use `camelbee-quarkus-core-custom` independently. This approach uses the provided `pom-custom.xml` file, which allows you to customize Java and Camel Quarkus versions to match your existing project setup.

1. Build the core library with the custom POM file:

run `mvn -f pom-custom.xml clean install` command in the "./camelbee/core/quarkus-core" folder

Once the custom maven artifact is created, you can include it in your project by adding the following dependency to your pom.xml:

```xml
  <dependency>
    <groupId>io.camelbee</groupId>
    <artifactId>camelbee-quarkus-core-custom</artifactId>
    <version>3.0.0</version>
  </dependency>
```

## Configuration

### Configure your each Camel Route with org.camelbee.config.CamelBeeRouteConfigurer

To enable the stream caching in your camel routes like below:

```
/**
 * Musician Route.
 *
 * @author ekaraosmanoglu
 */
@ApplicationScoped
public class MusicianRoute extends RouteBuilder {

    ...
    ...

    @Override
    public void configure() throws Exception {

        camelBeeRouteConfigurer.configureRoute(this);
        ...
        ...
```

### Enable CamelBee Features

To enable specific features of the CamelBee library, add/modify the following properties in your `application.yml` file:

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
quarkus:
  http:
    port: 8080
```

### Enable CamelBee Quarkus Beans

Add "camelbee-quarkus-core" dependency to your application.yaml of your Quarkus project, so they will be available.

```
quarkus:
  index-dependency:
    camelbeecore:
      group-id: io.camelbee
      artifact-id: camelbee-quarkus-core
```

## Accessing the Embedded UI

Once your application is running, the embedded CamelBee UI is available at:

`http://localhost:8080/camelbee`

This provides route visualization, message tracing, debugging with replay, filtering, endpoint triggering, and metrics directly in your browser.

## Example Implementation

Discover a practical and functional application of this core library within the 'allcomponent-quarkus-sample' Maven project showcased below as a successful and operational example:

```shell
camelbee/
|-- core/
|   |-- quarkus-core/
|   |   |-- ...
|-- examples/
|   |-- allcomponent-quarkus-sample/
|   |   |-- ...
```