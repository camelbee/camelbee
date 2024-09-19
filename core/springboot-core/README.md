# CamelBee SpringBoot Core Library

## Introduction

The camelbee-springboot-core library is an essential component for integrating a Camel SpringBoot Microservice with the CamelBee WebGL application (https://www.camelbee.io). 
This library provides the necessary functionalities to configure Camel routes with interceptors, allowing comprehensive tracing of messages exchanged between the routes. 
Additionally, it includes rest controllers for seamless interaction with the CamelBee WebGL application.

## Manual Installation

To manually install the core library, follow the steps below:

### Maven Installation

run `mvn clean install` command in the topmost parent folder "./camelbee"

Once the maven artifact is created, you can include it in your project by adding the following dependency to your pom.xml as the parent project:

```xml
  <parent>
    <groupId>io.camelbee</groupId>
    <artifactId>camelbee-springboot-starter</artifactId>
    <version>2.0.0</version>
  </parent>
```

### Maven Installation Custom Without CamelBee Starter Project as parent but directly adding the core library

If you prefer not to use `camelbee-springboot-starter` as the parent project, you can build `camelbee-spring-core` separately for your project using the provided `pom-custom.xml`. Follow these steps:

1. Build the core library with the custom POM file:

run `mvn -f pom-custom.xml clean install` command in the "./camelbee/core/springboot-core" folder

Once the custom maven artifact is created, you can include it in your project by adding the following dependency to your pom.xml:
   
```xml
  <dependency>
    <groupId>io.camelbee</groupId>
    <artifactId>camelbee-spring-core-custom</artifactId>
    <version>2.0.0</version>
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
@Component
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

To enable specific features of the CamelBee library, add/modify the following properties in your `application.yaml` file:

```
camelbee:
  # When context-enabled is true, it allows the CamelBee WebGL application to fetch the topology of the Camel Context.
  context-enabled: true
  # When producer-enabled is true, it allows the CamelBee WebGL application to trigger the consumer routes.
  # producer-enabled SHOULD BE ONLY ENABLED FOR DEVELOPMENT AND TESTING PURPOSES, NOT FOR PRODUCTION.
  producer-enabled: true
  # When debugger-enabled is true, it intercepts and traces requests and responses of all Camel components and caches messages.
  # debugger-enabled SHOULD BE ONLY ENABLED FOR DEVELOPMENT AND TESTING PURPOSES, FOR PRODUCTION IT COULD BE USED TEMPORARILY.
  debugger-enabled: true
  # Maximum time the tracer can remain idle before deactivation tracing of messages.
  debugger-max-idle-time: 60000
```


### Enable Metrics and CORS for https://www.camelbee.io

To enable metrics and configure CORS for https://www.camelbee.io, adjust the following properties to your `application.yaml` file:

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
      cors:
        allowed-origins: "https://www.camelbee.io" # Comma-separated list of origins you want to allow
        allowed-methods: GET  # Allowed HTTP methods. Default to GET.
        allowed-headers: "*"  # Allowed HTTP headers. Default to "*".
        allow-credentials: true  # Set to true if you want to allow cookies, authorization headers, etc.
        max-age: 1800  # Maximum age (in seconds) of the cache duration for CORS preflight responses.
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
## Example Implementation

Discover a practical and functional application of this core library within the 'allcomponent-springboot-sample' Maven project showcased below as a successful and operational example:

```shell
camelbee/
|-- core/
| |-- springboot-core/
| | |-- ...
|-- examples/
| |-- allcomponent-springboot-sample/
| | |-- ...
```
