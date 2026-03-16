# CamelBee SpringBoot AllComponents Project

## Introduction

The CamelBee SpringBoot AllComponents Project serves as an illustrative demonstration of the camelbee-springboot-core library's capabilities.
This project showcases how the library integrates with the embedded CamelBee UI, offering an immersive visualization experience.

### Initiate all the backend components

Before running this application, it is essential to verify that all the backend services it relies on are operational.
You can start these backend services by executing the following docker-compose command in the "./camelbee/examples/allcomponent-springboot-sample/backends" directory:

`docker-compose -f compose-backends.yml up -d`

### Running the Springboot Application with Maven

To execute this application, you must first ensure that you have successfully installed the camelbee-springboot-core library by running `mvn clean install` from the topmost parent folder "./camelbee".
Once the library is in place, follow these steps to run the application:

`mvn clean compile spring-boot:run`

## Visualizing with the Embedded UI

After launching the application, open a web browser and navigate to:

`http://localhost:8080/camelbee`

The embedded CamelBee UI provides route visualization, message tracing, debugging with replay, filtering, endpoint triggering, and metrics directly in your browser.