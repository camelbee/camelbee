# CamelBee SpringBoot Core Library

## Introduction

The camelbee-springboot-core library is an essential component for integrating a Camel SpringBoot Microservice with the CamelBee ecosystem.
It comes with an **embedded React UI** served directly from your application, providing route visualization, message tracing, debugging, and metrics.
This library provides the necessary functionalities to configure Camel routes with event notifiers, allowing comprehensive tracing of messages exchanged between the routes.

## Installation

There are three ways to integrate CamelBee into your Spring Boot project:

### Option 1: Add the Core Library as a Dependency (Recommended)

The recommended way for existing microservices. Add the CamelBee core library directly as a dependency from Maven Central — no local build needed, and it works alongside your existing parent POM.

> **Note:** This library requires Spring Boot 3.x+ and Camel Spring Boot 4.x+. Your existing BOMs should satisfy this — no changes needed if your project already targets these versions.

Add the CamelBee core dependency:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-core</artifactId>
  <version>3.3.1</version>
</dependency>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true

management:
  server:
    port: 8080
  security:
    enabled: false
  endpoints:
    web:
      exposure:
        include: '*'
      base-path: /
      path-mapping:
        prometheus: metrics
        metrics: metrics-default
```

Also add `org.camelbee` to your `@ComponentScan` to pick up CamelBee beans:
```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.camelbee", "your.application.package"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### Option 2: Use CamelBee Starter as Parent (New projects only)

Only suitable for new projects without an existing parent POM. Simply use `camelbee-springboot-starter` as your project's parent POM — it is available on Maven Central and automatically includes the core library, embedded UI, and all required dependencies — including all dependency version management. No local build needed:

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-starter</artifactId>
  <version>3.3.1</version>
</parent>
```

Then add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true

management:
  server:
    port: 8080
  security:
    enabled: false
  endpoints:
    web:
      exposure:
        include: '*'
      base-path: /
      path-mapping:
        prometheus: metrics
        metrics: metrics-default
```

Also add `org.camelbee` to your `@ComponentScan` to pick up CamelBee beans:
```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.camelbee", "your.application.package"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

For working examples using the starter, see the [camelbee-examples](https://github.com/camelbee/camelbee-examples) repository.

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
  <version>3.3.1</version>
</dependency>
```

3. Add the following to your `application.yaml`:
```yaml
camelbee:
  notifier-enabled: true
  route-configurer-enabled: true
  context-enabled: true
  tracer-enabled: true
  tracer-max-idle-time: 60000
  tracer-max-messages-count: 10000
  logging-enabled: true

management:
  server:
    port: 8080
  security:
    enabled: false
  endpoints:
    web:
      exposure:
        include: '*'
      base-path: /
      path-mapping:
        prometheus: metrics
        metrics: metrics-default
```

Also add `org.camelbee` to your `@ComponentScan` to pick up CamelBee beans:
```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.camelbee", "your.application.package"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

## Configuration

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
  # when enabled redacts configured keys out of traced headers and bodies (default: true)
  masking-enabled: true
  # comma-separated key names to redact; replaces the built-in list entirely (default: see below)
  masked-keys: password,token,authorization,apikey,creditcard,cvv,iban,ssn
  # when disabled no message body text is captured at all - the only hard guarantee (default: true)
  tracer-body-enabled: true
  # when enabled it logs the messages exchanged between endpoints
  logging-enabled: true
```


### Enable Metrics

To enable metrics, adjust the following properties in your `application.yaml` file:

```yaml
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

## Related Documentation

- [CamelBee User Guide](../../docs/camelbee_userguide.md) — a tour of the UI's pages and features
- Using Quarkus? See the [Quarkus Core README](../quarkus-core/README.md)
- Using plain Camel (`camel-main`)? See the [Standalone Core README](../standalone-core/README.md)
