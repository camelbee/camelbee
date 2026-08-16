# CamelBee Standalone Core Library

## Introduction

The camelbee-standalone-core library integrates a **plain standalone Camel application** (`camel-main`, no Spring Boot or Quarkus) with the CamelBee ecosystem.
It comes with an **embedded React UI** served directly from your application, providing route visualization, message tracing, debugging, and metrics.
This library provides the necessary functionalities to configure Camel routes with event notifiers, allowing comprehensive tracing of messages exchanged between the routes.

Unlike the Quarkus and Spring Boot cores, the standalone runtime has no dependency-injection container. CamelBee is wired in by hand with a single call, and its UI, REST API, and metrics are served as HTTP handlers on the **camel-main management server** (a separate port, `8081` by default) — so they never appear as Camel routes and never pollute the route topology or message tracer.

## Installation

There are three ways to integrate CamelBee into your standalone Camel project:

### Option 1: Use CamelBee Starter as Parent (Recommended)

The simplest path. Use `camelbee-standalone-starter` as your project's parent POM — it automatically includes the core library, the embedded UI, `camel-platform-http-main` (required to expose the management endpoints), and Micrometer/Prometheus metrics, including all dependency version management:

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-starter</artifactId>
  <version>3.3.1</version>
</parent>
```

### Option 2: Add the Core Library as a Dependency

For projects with an existing parent POM. Add the CamelBee core library directly, together with `camel-platform-http-main` (CamelBee uses the camel-main management server to expose its UI and API):

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

> **Note:** If `camel-platform-http-main` is not on the classpath, CamelBee starts but logs a warning and does not expose its UI/API endpoints.

### Option 3: Build a Custom Core Library (Custom Java/Camel Versions)

If you need to customize Java and Camel versions, you can build and use `camelbee-standalone-core-custom` independently. This approach uses the provided `pom-custom.xml`, which allows you to adjust versions to match your existing project setup.

1. Build the core library with the custom POM:

```bash
mvn -f pom-custom.xml clean install    # run in ./camelbee/core/standalone-core
```

2. Add the dependency to your project's `pom.xml`, together with `camel-platform-http-main`:

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

## Wiring CamelBee into Your Application

There is no auto-configuration — attach CamelBee explicitly before the context starts.

With a `camel-main` application, call `CamelBee.register(main)` after configuring routes and before `main.run(...)`:

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

Or, if you bootstrap a `CamelContext` yourself, call `CamelBee.attach(camelContext)` before starting it:

```java
CamelBee.attach(camelContext);
camelContext.start();
```

## Configuration

### Enable CamelBee Features

CamelBee reads the same `camelbee.*` keys as the other runtimes from Camel's `PropertiesComponent`, so they can be set in `application.properties`, system properties, or environment variables:

```properties
# when enabled it allows the CamelBee UI to fetch the topology of the Camel Context (default: true)
camelbee.context-enabled = true
# when enabled registers the CamelBee event notifier to the Camel context (default: true)
camelbee.notifier-enabled = true
# when enabled configures stream caching, MDC logging and CamelBeeUnitOfWork for routes (default: true)
camelbee.route-configurer-enabled = true
# when enabled intercepts/traces request and responses of all camel components and caches messages (default: false)
camelbee.tracer-enabled = true
# when enabled exposes Prometheus metrics on the management server (default: true)
camelbee.metrics-enabled = true
# when enabled it logs the messages exchanged between endpoints (default: false)
camelbee.logging-enabled = false
# maximum time (ms) the tracer can remain idle before tracing of messages is deactivated (default: 300000)
camelbee.tracer-max-idle-time = 300000
# maximum collected trace messages (default: 1000)
camelbee.tracer-max-messages-count = 1000
# when enabled redacts configured keys out of traced headers and bodies (default: true)
camelbee.masking-enabled = true
# comma-separated key names to redact; replaces the built-in list entirely (default: see below)
camelbee.masked-keys = password,token,authorization,apikey,creditcard,cvv,iban,ssn
# when disabled no message body text is captured at all - the only hard guarantee (default: true)
camelbee.tracer-body-enabled = true
```

### Application and Management Ports

Your own routes run on the application server (`camel.server.port`, e.g. `8080`), while CamelBee serves its UI, REST API, and metrics on the camel-main management server. `CamelBee.register(...)` enables the management server on port `8081` with health checks by default; override these with the standard `camel.management.*` properties:

```properties
# the application's own platform-http server (your routes)
camel.server.enabled = true
camel.server.host = 0.0.0.0
camel.server.port = 8080
```

### Redacting sensitive data

Traced bodies and headers are served over HTTP and written to the structured log, so anything
captured is disclosed. Three properties control that, and they are applied **at capture** - nothing
sensitive is stored and filtered later.

| property | default | meaning |
| --- | --- | --- |
| `camelbee.masking-enabled` | **`true`** | Redact configured keys out of headers and bodies. |
| `camelbee.masked-keys` | see below | Comma-separated key names, replacing the defaults entirely. |
| `camelbee.tracer-body-enabled` | `true` | Set `false` to never capture bodies at all. |

Unlike every other `camelbee.*` switch, masking defaults to **on**. The others fail closed by
staying off; this one fails closed by staying on.

Default keys (case-insensitive, and `-`/`_`/`.` are ignored, so one `apikey` entry catches
`X-Api-Key` and `api_key`):

```
password, passwd, secret, token, authorization, auth, apikey, accesskey, privatekey,
credential, creditcard, cardnumber, cardno, cvv, cvc, iban, ssn, pin, otp
```

A key matches if it *contains* a configured entry, so `password` also covers `userPassword`.

**What this does and does not guarantee.** Header masking is exact - the key is known, so a
configured key is always redacted. Body masking is **best effort** pattern matching over JSON, XML
and form-encoded shapes: it cannot redact a field nobody configured, and a body in some other
format is left untouched. Treat it as defence in depth. The only guarantee available is
`camelbee.tracer-body-enabled=false`, which reads no body text at all.

### Tracing one transaction in a busy application

With hundreds of exchanges a second, recording everything is neither readable nor safe. The capture
filter records **only the flow under investigation**:

```
POST /camelbee/tracer/filter      body: order-42        (raw text; empty clears)
```

In the UI it is the amber box next to **Start Tracing**. It is applied when tracing starts, or on
Enter - not per keystroke, because changing it discards what matched the previous value.

- Matching is **per exchange, not per message**: once anything in an exchange matches, the rest of
  that exchange is kept, and so are the branches it spawns (wireTap, multicast, seda...). A branch
  rarely repeats the id its parent matched on, and half a flow is worse than none.
- Matching is case-insensitive and covers both body and headers.
- It runs against the text **after masking**, so a value that redaction removes cannot be filtered
  on. That is deliberate.
- Known limit: messages of an exchange emitted *before* the matching one are not recovered. In
  practice the identifying value is present from the first message.

This is different from the toolbar's grey **Filter messages** box, which only hides rows that were
already recorded and already served. For production, the capture filter is the one that matters.

## Accessing the Embedded UI

Once your application is running, the embedded CamelBee UI is available on the management server at:

`http://localhost:8081/camelbee`

This provides route visualization, message tracing, debugging with replay, filtering, endpoint triggering, and metrics directly in your browser.

Prometheus metrics are exposed on the same management server at:

`http://localhost:8081/observe/metrics`

## Example Implementation

Discover a practical and functional application of this core library within the 'allcomponent-standalone-sample' Maven project showcased below as a successful and operational example:

```shell
camelbee/
|-- core/
|   |-- standalone-core/
|   |   |-- ...
|-- examples/
|   |-- allcomponent-standalone-sample/
|   |   |-- ...
```

## Related Documentation

- [CamelBee User Guide](../../docs/camelbee_userguide.md) — a tour of the UI's pages and features
- Using Quarkus? See the [Quarkus Core README](../quarkus-core/README.md)
- Using Spring Boot? See the [Spring Boot Core README](../springboot-core/README.md)
