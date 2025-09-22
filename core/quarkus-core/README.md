# CamelBee Quarkus Core Library

## Introduction

The camelbee-quarkus-core library is an essential component for integrating a Camel Quarkus Microservice with the CamelBee WebGL application (https://www.camelbee.io) or the local Docker version (accessed at http://localhost:8083 by executing 'docker run -d -p 8083:80 camelbee/webgl'). 
This library provides the necessary functionalities to configure Camel routes with event notifiers, allowing comprehensive tracing of messages exchanged between the routes. 
Additionally, it includes rest controllers for seamless interaction with the CamelBee WebGL application.

## Manual Installation

To manually install the core library, follow the steps below:

### Maven Installation

run `mvn clean install` command in the topmost parent folder "./camelbee"

Once the maven artifact is created, you can include it in your project by adding the following dependency to your pom.xml as the parent project:

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-starter</artifactId>
  <version>2.0.4</version>
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
    <version>2.0.4</version>
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

```
camelbee:
  # when enabled it allows the CamelBe WebGL application to fetch the topology of the Camel Context.
  context-enabled: true
  # when enabled it allows the CamelBe WebGL application to trigger the consumer routes.
  producer-enabled: true
  # tracer-enabled SHOULD BE ONLY ENABLED FOR DEVELOPMENT PURPOSES, NOT FOR PRODUCTION.
  # when enabled intercepts/traces request and responses of all camel components and caches messages.
  tracer-enabled: true
  # maximum time the tracer can remain idle before deactivation tracing of messages.
  tracer-max-idle-time: 60000
  # maximum collected trace messages
  tracer-max-messages-count: 10000
  # when enabled it logs the messages exchanged between endpoints
  logging-enabled: true
```


### Enable Metrics and CORS for https://www.camelbee.io

To enable metrics and configure CORS for https://www.camelbee.io, adjust the following properties to your `application.yaml` file:

```
quarkus:
  http:
    port: 8080
    cors:
      ~: true
      origins: https://www.camelbee.io,http://localhost:8083
      methods: GET,POST,DELETE
      headers: "*"
      exposed-headers: Content-Disposition
      access-control-allow-credentials: true
      access-control-max-age: 24H
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

## Example Implementation

Discover a practical and functional application of this core library within the 'allcomponent-quarkus-sample' Maven project showcased below as a successful and operational example:

```shell
camelbee/
|-- core/
| |-- quarkus-core/
| | |-- ...
|-- examples/
| |-- allcomponent-quarkus-sample/
| | |-- ...
```
