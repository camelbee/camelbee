# CamelBee on Camel K

Camel K runs integrations on the **Camel Quarkus** runtime, so CamelBee's existing
`camelbee-quarkus-core` works on Camel K unchanged — no separate "camelk-core" module is needed.

This sample is **not** a Maven module (it is not wired into the reactor). It is a single Camel K
integration file you run with the `kamel` CLI.

## How it works

- The modeline at the top of [`MusicianRoute.java`](./MusicianRoute.java) adds:
  - `mvn:io.camelbee:camelbee-quarkus-core:3.3.1` — the CamelBee monitoring beans + embedded UI.
  - `camel-quarkus-rest`, `camel-quarkus-jackson`, `quarkus-resteasy-jackson` — the REST stack the
    CamelBee endpoints require (these are `provided` in the core, so consumers add them; versions are
    omitted so Camel K resolves them from its own runtime BOM).
- **Zero `quarkus.index-dependency` config is required**: `camelbee-quarkus-core` now ships a
  Jandex index (`META-INF/jandex.idx`), so Quarkus/Arc auto-discovers its CDI beans when the jar is
  a dependency.
- `build-property` lines turn on the CamelBee endpoints, which are gated by Quarkus
  `@IfBuildProperty` (build-time). The `property` line enables tracing at runtime.
- `trait=service.enabled=true` exposes the HTTP port as a Kubernetes Service.

## Prerequisites

- A Kubernetes cluster with the **Camel K operator** installed, and the `kamel` CLI.
- A Camel K runtime aligned with **camel-quarkus 3.37.x** (the version `camelbee-quarkus-core:3.3.1`
  is built against). If your operator's runtime differs significantly, pin a compatible Camel K
  runtime version (e.g. `kamel run ... --runtime-version <x>`) or rebuild CamelBee against your
  Camel Quarkus version (see below).

### Rebuilding CamelBee against your runtime's Camel Quarkus version

If you cannot pin the runtime, build a custom core matching your operator's Camel Quarkus version
using the provided `pom-custom.xml`:

1. In `camelbee/core/quarkus-core/`, set `quarkus.platform.version` in `pom-custom.xml` to your
   runtime's Camel Quarkus version, then build:

   ```sh
   mvn -f pom-custom.xml clean install
   ```

   This produces `io.camelbee:camelbee-quarkus-core-custom:3.3.1` (it also ships the Jandex index,
   so bean discovery works the same way).

2. Camel K builds integrations **in-cluster**, so the operator cannot see your local `~/.m2` —
   publish the custom jar to a Maven repository the operator can reach (e.g. your org's
   Nexus/Artifactory, configured in the IntegrationPlatform's Maven settings).

3. Reference it in the modeline instead of the published core:

   ```
   // camel-k: dependency=mvn:io.camelbee:camelbee-quarkus-core-custom:3.3.1
   ```

> Note: this integration has not been validated on a live cluster as part of this change — the
> Jandex enabler in `camelbee-quarkus-core` was verified locally (the jar contains
> `META-INF/jandex.idx`).

## Run

```sh
kamel run MusicianRoute.java
```

Everything (dependencies, build/runtime properties, service trait) is declared via the modeline, so
no extra flags are needed. Watch it start:

```sh
kamel get
kamel logs musician-route
```

## Open the CamelBee UI

The embedded UI ships inside `camelbee-quarkus-core` and is served at `/camelbee`.

```sh
kubectl port-forward svc/musician-route 8080:80
```

Then open <http://localhost:8080/camelbee> and point the UI at the same origin. Useful endpoints:

- `GET  /camelbee/routes`         — route topology
- `POST /camelbee/tracer/status`  — body `ACTIVE` / `INACTIVE` to toggle tracing
- `GET  /camelbee/messages`       — traced messages
- `GET  /q/health`, `GET /q/metrics` — Quarkus health/metrics (require extra extensions, see below)

> The modeline covers route topology, tracing and the embedded UI. To also populate the UI's
> health panel and metrics page, add these extensions as extra modeline lines:
>
> ```
> // camel-k: dependency=mvn:org.apache.camel.quarkus:camel-quarkus-microprofile-health
> // camel-k: dependency=mvn:io.quarkus:quarkus-micrometer-registry-prometheus
> ```

## Using the starter instead

To pull the full CamelBee bundle (core + security + common Camel Quarkus deps) replace the first
dependency line with the pom starter:

```
// camel-k: dependency=mvn:io.camelbee:camelbee-quarkus-starter:pom:3.3.1
```
