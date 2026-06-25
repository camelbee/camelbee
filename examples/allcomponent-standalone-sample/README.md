# CamelBee Standalone AllComponents Project

## Introduction

The CamelBee Standalone AllComponents Project serves as an illustrative demonstration of the camelbee-standalone-core library's capabilities.
This project is a plain standalone Camel application (`camel-main`, no Spring Boot or Quarkus) and showcases how the library integrates with the embedded CamelBee UI, offering an immersive visualization experience.

The sample is infra-free: it builds an EIP-rich topology (REST, timer, direct, bean, multicast, wireTap, enrich, etc.) using only components that need no external brokers or databases, so it runs immediately with no backend services to start.

## Running the Application with Maven

To execute this application, you must first ensure that you have successfully installed the camelbee-standalone-core library by running `mvn clean install` from the topmost parent folder "./camelbee".
Once the library is in place, run the application from this directory with:

`mvn compile exec:java`

The application's own routes are served on the platform-http server (port 8080, see `application.properties`), while the CamelBee UI runs on the separate camel-main management port (8081).

## Visualizing with the Embedded UI

After launching the application, open a web browser and navigate to:

`http://localhost:8081/camelbee`

The embedded CamelBee UI provides route visualization, message tracing, debugging with replay, filtering, endpoint triggering, and metrics directly in your browser.