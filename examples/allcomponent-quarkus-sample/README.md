# CamelBee Quarkus AllComponents Project

## Introduction

The CamelBee Quarkus AllComponents Project serves as an illustrative demonstration of the camelbee-quarkus-core library's capabilities.
This project showcases how the library integrates with the embedded CamelBee UI, offering an immersive visualization experience.

## Running the Application with Maven

### Initiate all the backend components

Before running this application, it is essential to verify that all the backend services it relies on are operational.
You can start these backend services by executing the following docker-compose command:

`docker-compose -f backends/compose-backends.yml up -d`

### Running the Quarkus Application with Maven

To execute this application, you must first ensure that you have successfully installed the camelbee-quarkus-core library by running `mvn clean install` from the topmost parent folder "./camelbee".
Once the library is in place, follow these steps to run the application:

`mvn clean compile quarkus:dev`

> **Note:** Quarkus 3.x requires **Maven 3.9+** (and Java 21). With an older Maven you will get
> `Detected Maven Version (x.y.z) is not supported, it must be in [3.9.0,)`.

## Visualizing with the Embedded UI

After launching the application, open a web browser and navigate to:

`http://localhost:8080/camelbee/index.html`

The embedded CamelBee UI provides route visualization, message tracing, debugging with timeline replay, filtering, and metrics directly in your browser.

For a guide to the UI's pages and features, see the [CamelBee User Guide](../../docs/camelbee_userguide.md).

### No login is required in this sample

CamelBee requires a username and password by default (`camelbee.auth-enabled`, on since 4.0). This
sample deliberately turns that **off** in `application.yml`, so the UI opens straight into the
debugger and the end-to-end suite can drive the REST API without signing in:

```yaml
camelbee:
  auth-enabled: false
```

**That is a sample convenience, not the recommended setting.** In a real application leave
authentication on, so that nobody who can reach the port can read your traced traffic — or switch
tracing on themselves, since the API is not read-only.

To try the login flow here, flip it on and give it a password:

```yaml
camelbee:
  auth-enabled: true
  username: camelbee
  password: s3cret
```

Without a password, one is generated at every start and written to the application log:

```text
WARN  CamelBee UI is protected. Generated password for user 'camelbee': f1d4a6a2-…
```

See the **Securing the CamelBee endpoints** section of the
[Quarkus core README](../../core/quarkus-core/README.md#securing-the-camelbee-endpoints)
for the full picture.

