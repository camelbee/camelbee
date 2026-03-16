# CamelBee Quarkus AllComponents Project

## Introduction

The CamelBee Quarkus AllComponents Project serves as an illustrative demonstration of the camelbee-quarkus-core library's capabilities.
This project showcases how the library integrates with the embedded CamelBee UI, offering an immersive visualization experience.

## Running the Application with Maven

### Initiate all the backend components

Before running this application, it is essential to verify that all the backend services it relies on are operational.
You can start these backend services by executing the following docker-compose command in the "./camelbee/examples/allcomponent-quarkus-sample/backends" directory:

`docker-compose -f compose-backends.yml up -d`

### Running the Quarkus Application with Maven

To execute this application, you must first ensure that you have successfully installed the camelbee-quarkus-core library by running `mvn clean install` from the topmost parent folder "./camelbee".
Once the library is in place, follow these steps to run the application:

`mvn clean compile quarkus:dev`

## Visualizing with the Embedded UI

After launching the application, open a web browser and navigate to:

`http://localhost:8080/camelbee`

The embedded CamelBee UI provides route visualization, message tracing, debugging with replay, filtering, endpoint triggering, and metrics directly in your browser.
