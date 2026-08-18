# CamelBee SpringBoot Core Library

## Introduction

The camelbee-springboot-core library is an essential component for integrating a Camel SpringBoot Microservice with the CamelBee ecosystem.
It comes with an **embedded React UI** served directly from your application, providing route visualization, message tracing, debugging, and metrics.
This library provides the necessary functionalities to configure Camel routes with event notifiers, allowing comprehensive tracing of messages exchanged between the routes.

## Installation

There are three ways to integrate CamelBee into your Spring Boot project:

### Option 1: Add the Core Library as a Dependency (Recommended)

The recommended way for existing microservices. Add the CamelBee core library directly as a dependency from Maven Central — no local build needed, and it works alongside your existing parent POM.

> **Supported versions.** The core declares its framework dependencies as `provided`, so **your**
> BOM decides every version — CamelBee adds nothing to your dependency graph but itself. You do not
> need to match the versions this project is built against, and you do not need a custom build to
> stay on an older stack.
>
> - **Camel 4.8+**
> - **Spring Boot 3.3+** — whatever your Camel release pairs with (4.8 → 3.3, 4.16 → 3.5, 4.22 → 4.1)
> - **JDK 17+**
>
> Below those, see [Option 3](#option-3-build-a-custom-core-library-custom-javacamel-versions).

Add the CamelBee core dependency. `spring-boot-starter-web` and `camel-spring-boot-starter` are
`provided` — declare them yourself, at your own versions, if they aren't already in your POM:
```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-core</artifactId>
  <version>4.0.0</version>
</dependency>
<!-- supplied by your application, at your versions -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.apache.camel.springboot</groupId>
  <artifactId>camel-spring-boot-starter</artifactId>
</dependency>
```

A minimal, runnable version of exactly this lives in
[`examples/core-only-springboot-sample`](../../examples/core-only-springboot-sample) — pinned to the
oldest supported stack, so it doubles as proof the floors above are real.

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
> **What you get.** The starter pins the whole stack — you do not choose these, and overriding them
> is not supported:
>
> | Starter | Pins |
> |---|---|
> | `camelbee-springboot-starter` | Spring Boot 4.1.0 · Camel 4.22.0 |
>
> That is the trade-off against [Option 1](#option-1-add-the-core-library-as-a-dependency-recommended): the starter decides your framework versions, so
> your stack moves when CamelBee releases. If you need to stay on your own versions, use the core as
> a dependency instead.


Only suitable for new projects without an existing parent POM. Simply use `camelbee-springboot-starter` as your project's parent POM — it is available on Maven Central and automatically includes the core library, embedded UI, and all required dependencies — including all dependency version management. No local build needed:

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-springboot-starter</artifactId>
  <version>4.0.0</version>
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
  <version>4.0.0</version>
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
> These are the properties you need to get started. For the full list with defaults — authentication,
> redaction, session timeout, tracer limits — see
> [All configuration properties](../../README.md#all-configuration-properties).


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
  # --- Authentication (new in 4.0, ON by default) ---
  # when enabled the UI and REST API require a login
  auth-enabled: true
  # the login name; not a secret
  username: camelbee
  # leave unset and one is GENERATED at startup and written to the log. There is deliberately no
  # default password: a documented one protects nobody while looking as though it does.
  # password: ${CAMELBEE_PASSWORD:}
  # idle window in ms; each request re-issues the token, so an active session never expires
  session-timeout: 120000
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

### Securing the CamelBee endpoints

With `camelbee.context-enabled` set, CamelBee publishes its UI and REST API under `/camelbee`.
**Since 4.0 those endpoints require a login by default** — `camelbee.auth-enabled` defaults to
`true`, and nothing is readable without a token.

That default exists because the API is not read-only:

| Endpoint | What it gives away, or does |
| --- | --- |
| `GET /camelbee/routes` | The complete route topology: route ids, EIP structure and every endpoint URI — including hostnames and queue names. Credentials inside a URI are redacted. |
| `POST /camelbee/tracer/status` | **Turns tracing on.** Without a gate, a caller does not have to wait for someone else to start a session — they can arm capture themselves and then read the traffic. |
| `GET /camelbee/messages` | Every captured message — bodies, headers, timings and exception text — subject to redaction. |
| `DELETE /camelbee/messages` | Discards the collected trace. |

Redaction and the capture filter govern **what is recorded**. Authentication governs **who may read
it**. They are separate controls and you want both.

#### Configuring the login

| Property | Default | Meaning |
| --- | --- | --- |
| `camelbee.auth-enabled` | **`true`** | Require a login. Setting it `false` logs a warning at startup. |
| `camelbee.username` | `camelbee` | The login name. Not a secret. |
| `camelbee.password` | *(generated)* | Blank means one is generated at startup and written to the log. |
| `camelbee.session-timeout` | `120000` (2 min) | Idle window. Each request re-issues the token, so an active session never expires and an abandoned one does. |

**There is deliberately no default password.** A published default — `camelbee/camelbee` in a README
— protects nobody, because the value is in the documentation, while looking as though it does. With
none configured, CamelBee generates one per start and logs it:

```text
WARN  CamelBee UI is protected. Generated password for user 'camelbee': f1d4a6a2-4478-49a9-90e7-…
WARN  Set camelbee.password (or CAMELBEE_PASSWORD) to use your own. Note that each replica
      generates its own, so a multi-instance deployment must configure one.
```

That is fine on a laptop, where you read it out of your own log. For anything shared, set one — and
keep it out of the file:

```yaml
camelbee:
  auth-enabled: true
  username: ${CAMELBEE_USERNAME:camelbee}
  password: ${CAMELBEE_PASSWORD:}
  session-timeout: 120000
```

**Limits worth knowing.**

- **One shared credential is a gate, not an identity.** There is no per-user audit and no revocation
  before the token expires. That is the right weight for a debugging tool; if you need SSO or an
  audit trail, set `camelbee.auth-enabled=false` and put your host framework's own security in front
  of `/camelbee/**` instead — see below.
- **Use TLS.** The login carries the password and every later request carries a bearer token. Both
  are readable in transit unless the endpoint is served over HTTPS.
- **The UI shell is public by necessity.** `index.html` and its assets load without a token, because
  a browser has to load the application before it can show a login form. Every byte of *data* sits
  behind the guard.
- **Health and metrics are not CamelBee's endpoints** and are not covered by this. They belong to
  the host framework; protect them there if they matter.

**Still keep it off the public edge.** Authentication is defence in depth, not a reason to publish a
debugging interface to the internet:

> Do not route `/camelbee` through a public gateway or ingress. Keep it on an internal address, a
> VPN or a port-forward, with the login as the second line rather than the only one.

**Turning the endpoints off entirely** remains the strongest control if CamelBee is present only for
non-production environments — see below.

#### Spring Boot specifics

CamelBee shares your application's HTTP port, so excluding it at the edge is a path rule rather than
a port rule: deny `/camelbee` and `/camelbee/**` on the public ingress that fronts the service.

The guard is a `OncePerRequestFilter`, which is the one place both the REST controllers and the UI's
static resources under `static/camelbee` pass through. It adds no dependency.

**Host-level authentication instead.** If the application already uses Spring Security, use it rather
than CamelBee's single credential:

```java
@Bean
SecurityFilterChain camelBeeSecurity(HttpSecurity http) throws Exception {
  return http
      .securityMatcher("/camelbee", "/camelbee/**")
      .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("CAMELBEE_ADMIN"))
      .httpBasic(Customizer.withDefaults())
      .build();
}
```

Set `camelbee.auth-enabled=false` when you do, so the two do not both challenge.

**Turning the endpoints off entirely.** Start the application with
`camelbee.context-enabled=false`. This removes the **API only** — the UI's static assets ship inside
the core jar under `static/camelbee` and are served by Spring's static resource handling
independently. To remove those too, leave the CamelBee dependency out of the production build, or
block the path at the edge.

## Accessing the Embedded UI

Once your application is running, the embedded CamelBee UI is available at:

`http://localhost:8080/camelbee/`

Deep links work: `/camelbee/settings` and `/camelbee/metrics` are client-side routes with no file behind
them, and the core answers them with the application, so a bookmark, a shared link or a plain reload
lands on the page it names rather than a 404. A missing asset still 404s, deliberately.

This provides route visualization, message tracing, debugging with timeline replay, filtering, and metrics directly in your browser.

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
