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

For a guide to the UI's pages and features, see the [CamelBee User Guide](../../docs/camelbee_userguide.md).

### No login is required in this sample

CamelBee requires a username and password by default (`camelbee.auth-enabled`, on since 4.0). This
sample deliberately turns that **off** in `application.properties`, so the UI opens straight into
the debugger and the end-to-end suite can drive the REST API without signing in:

```properties
camelbee.auth-enabled = false
```

**That is a sample convenience, not the recommended setting.** In a real application leave
authentication on, so that nobody who can reach the port can read your traced traffic — or switch
tracing on themselves, since the API is not read-only.

To try the login flow here, flip it on and give it a password:

```properties
camelbee.auth-enabled = true
camelbee.username = camelbee
camelbee.password = s3cret
```

Without a password, one is generated at every start and written to the application log:

```text
WARN  CamelBee UI is protected. Generated password for user 'camelbee': f1d4a6a2-…
```

See the **Securing the CamelBee endpoints** section of the
[Standalone core README](../../core/standalone-core/README.md#securing-the-camelbee-endpoints)
for the full picture.

## Tests

This sample doubles as the integration-test bed for the CamelBee core libraries. Its topology is
deliberately EIP-rich so that every shape the topology extractor and the message tracer have to
handle is exercised by real traffic: query strings on `direct:` targets, `toD`, `poll()`,
`pollEnrich`, both `enrich` forms, `recipientList`, `routingSlip`, `dynamicRouter`, redelivery that
eventually recovers, redelivery that's exhausted and actually reaches the dead-letter channel, a
caught failure, and an `http` hop that calls this application back on its own port (so it stays
infrastructure-free).

### Java integration tests

```
mvn verify
```

Runs `TopologyIntegrationTest` and `MessageTracingIntegrationTest` from `src/integration-test/java`.
They boot this application in-process on ephemeral ports, drive the CamelBee HTTP API, and assert on
what it really returns — the exact Camel `toString()` recipes in `GET /camelbee/routes`, and the
request/response pairs in `GET /camelbee/messages`, including the three attempts of a redelivered
send. The timer and file consumers are stopped for the duration so background traffic cannot land in
the middle of an assertion.

Because they pin Camel's `toString()` output, these tests are the tripwire for a Camel upgrade that
silently changes a recipe and empties the UI's graph.

### UI end-to-end tests

```
cd ../../ui/e2e
npm install && npm run setup   # one-time: deps + chromium
npm run e2e                    # rebuild the jar, start the sample, run the suite
```

Playwright drives the **embedded** UI — the copy bundled inside `camelbee-standalone-core` and served
by the management server, not a dev server — so a passing run means the shipped UI, the shipped REST
API and a real CamelContext agree with each other. Playwright starts and stops this sample itself.

> **The UI is served from the jar.** Editing `ui/src` is not enough; the jar has to be rebuilt or you
> are testing a stale UI. `npm run e2e` does that for you. `npm test` skips the rebuild and is only
> safe when nothing has changed since the last one.

The e2e project is intentionally separate from `ui/package.json` so that `mvn clean install` (vitest
only) never pulls in Playwright.