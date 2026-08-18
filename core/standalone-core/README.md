# CamelBee Standalone Core Library

## Introduction

The camelbee-standalone-core library integrates a **plain standalone Camel application** (`camel-main`, no Spring Boot or Quarkus) with the CamelBee ecosystem.
It comes with an **embedded React UI** served directly from your application, providing route visualization, message tracing, debugging, and metrics.
This library provides the necessary functionalities to configure Camel routes with event notifiers, allowing comprehensive tracing of messages exchanged between the routes.

Unlike the Quarkus and Spring Boot cores, the standalone runtime has no dependency-injection container. CamelBee is wired in by hand with a single call, and its UI, REST API, and metrics are served as HTTP handlers on the **camel-main management server** (a separate port, `8081` by default) — so they never appear as Camel routes and never pollute the route topology or message tracer.

## Installation

There are three ways to integrate CamelBee into your standalone Camel project:

> **Which option?** Unlike the Quarkus and Spring Boot cores — where adding the dependency is the
> recommended path — a standalone project usually has no parent POM of its own, so the starter is
> simpler here. If you *do* have a parent POM already, use [Option 2](#option-2-add-the-core-library-as-a-dependency);
> it is the same library, and the version floors below apply to it.

### Option 1: Use CamelBee Starter as Parent (Recommended)
> **What you get.** The starter pins the whole stack — Camel **4.22.0** — and overriding it is not
> supported. That is the trade-off against
> [Option 2](#option-2-add-the-core-library-as-a-dependency): the starter decides your Camel version,
> so your stack moves when CamelBee releases.


The simplest path. Use `camelbee-standalone-starter` as your project's parent POM — it automatically includes the core library, the embedded UI, `camel-platform-http-main` (required to expose the management endpoints), and Micrometer/Prometheus metrics, including all dependency version management:

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-starter</artifactId>
  <version>4.0.0</version>
</parent>
```

### Option 2: Add the Core Library as a Dependency

For projects with an existing parent POM. Add the CamelBee core library directly, together with `camel-platform-http-main` (CamelBee uses the camel-main management server to expose its UI and API):

```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-standalone-core</artifactId>
  <version>4.0.0</version>
</dependency>
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-platform-http-main</artifactId>
</dependency>
```

> **Note:** If `camel-platform-http-main` is not on the classpath, CamelBee starts but logs a warning and does not expose its UI/API endpoints.

> **Supported versions.** The core declares its framework dependencies as `provided`, so **your**
> BOM decides every version — CamelBee adds nothing to your dependency graph but itself. You do not
> need to match the versions this project is built against, and you do not need a custom build to
> stay on an older stack.
>
> - **Camel 4.12+**
> - **JDK 17+**
>
> Below those, see [Option 3](#option-3-build-a-custom-core-library-custom-javacamel-versions).
> A minimal runnable example is
> [`examples/core-only-standalone-sample`](../../examples/core-only-standalone-sample).

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
  <version>4.0.0</version>
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
> These are the properties you need to get started. For the full list with defaults — authentication,
> redaction, session timeout, tracer limits — see
> [All configuration properties](../../README.md#all-configuration-properties).


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
# --- Authentication (new in 4.0, ON by default) ---
# when enabled the UI and REST API require a login (default: true)
camelbee.auth-enabled = true
# the login name; not a secret (default: camelbee)
camelbee.username = camelbee
# leave unset and one is GENERATED at startup and written to the log. There is deliberately no
# default password: a documented one protects nobody while looking as though it does.
#camelbee.password = ${CAMELBEE_PASSWORD:}
# idle window in ms; each request re-issues the token, so an active session never expires (default: 120000)
camelbee.session-timeout = 120000
# a single allowed CORS origin for the UI dev server. Unset means CORS is CLOSED, which is what a
# deployed application wants - an open origin lets any page a developer visits read this app's
# topology and traces from their browser.
#camelbee.cors-allowed-origin = http://localhost:5173
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

```properties
camelbee.auth-enabled = true
camelbee.username = ${CAMELBEE_USERNAME:camelbee}
camelbee.password = ${CAMELBEE_PASSWORD:}
camelbee.session-timeout = 120000
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

#### Standalone specifics

CamelBee is served on the **management** server, not the application server, so the simplest control
is not to route that port publicly at all: `camel.server.port` carries your own routes and is what an
ingress should expose; `camel.management.port` (default `8081`) is where CamelBee lives.

**CORS is closed unless you open it.** `camelbee.cors-allowed-origin` exists for one case — running
the UI dev server against a packaged application — and warns loudly when set. Leave it unset in a
deployed application: a wide-open origin lets any page a developer visits read this application's
topology and traced messages from their browser.

**Host-level authentication instead.** `camel-platform-http-main` — already a dependency of the
standalone starter — can protect the whole management server with HTTP Basic or JWT, which is the
better fit if you want one credential store across several tools:

```properties
camelbee.auth-enabled = false
camel.management.authenticationEnabled = true
# scope it to CamelBee, so /observe/health stays open for probes
camel.management.authenticationPath = /camelbee/*
# a filesystem path - NOT a file: URL, which fails at startup
camel.management.basicPropertiesFile = /etc/camelbee/users.properties
```

```properties
# users.properties - Vert.x PropertyFileAuthentication format
user.admin=change-me
role.admin=access
user.roles.admin=admin
```

**Turning the endpoints off entirely.** `camelbee.context-enabled = false` unregisters both the API
and the UI on this runtime.

## Accessing the Embedded UI

Once your application is running, the embedded CamelBee UI is available on the management server at:

`http://localhost:8081/camelbee`

This provides route visualization, message tracing, debugging with timeline replay, filtering, and metrics directly in your browser.

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
