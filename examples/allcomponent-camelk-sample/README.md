# CamelBee AllComponents Camel K Sample

Camel K runs integrations on the **Camel Quarkus** runtime, so CamelBee works on Camel K — but not
with the same jar as the other samples. Camel K pins an older runtime than CamelBee's main build
(Camel 4.8.5 vs 4.21), so this sample uses **`camelbee-quarkus-core-camelk`**: the same sources,
same version, built against Camel K's Camel baseline. See
[Which core, and why](#which-core-and-why) below.

This sample is **not** a Maven module (it is not wired into the reactor). It is a single Camel K
integration file you run with the `kamel` CLI.

> **Verified end to end on 2026-08-02** against Camel K 2.10.1 on minikube: kit built in-cluster,
> pod Running, `GET /camelbee/routes` returning 24 routes, and 338 traced messages covering the
> http, wireTap, mock and seda hops. The version-specific details below are read off that cluster,
> not assumed — re-check them against your own operator (the commands are given).

## What the integration contains

[`MusicianRoute.java`](./MusicianRoute.java) mirrors the topology of the
[standalone sample](../allcomponent-standalone-sample/README.md): an EIP-rich but **infra-free**
graph, so it runs on a cluster with no brokers or databases installed. It exercises REST, timer,
file, bean, seda, `multicast`, `wireTap`, both `enrich` forms, `recipientList`, `routingSlip`,
`dynamicRouter`, static and expression `toD`, `poll()`, `pollEnrich`, a dead-letter channel with
redelivery, a locally caught failure, and an `http` producer that calls the integration back on its
own port — which gives the CamelBee UI a real remote hop with nothing external to install.

Everything is in one file: Camel K compiles a single public class whose name matches the file name,
so the helper beans of the other samples are methods on the route builder here, invoked with
`bean(this, "...")`.

## How it works

- The modeline at the top of [`MusicianRoute.java`](./MusicianRoute.java) adds:
  - `mvn:io.camelbee:camelbee-quarkus-core-camelk:4.0.0` — the CamelBee monitoring beans + embedded
    UI. It brings the REST/Jackson stack transitively (unlike `camelbee-quarkus-core`, where those
    are `provided`), so the modeline does not list them.
  - `camel:http`, `camel:mock`, `camel:seda`, `camel:file`, `camel:timer`, `camel:direct`,
    `camel:log`, `camel:bean` — **all components are declared explicitly**. Every one of these
    works normally on Camel K; the reason to list them is how Camel K decides which component jars
    to put on the classpath. It scans the source for URIs written literally at a `from(...)` /
    `to(...)` call site, and does not follow a constant to its value — so `to(SOUTHBOUND_QUEUE)` is
    invisible to it where `to("seda:southbound")` would not be. This file routes through constants
    (`SOUTHBOUND_QUEUE`, `INPUT_DIR`, `OUTPUT_DIR`), so `seda` and `file` are missed. The kit still
    builds fine and the failure only appears when the pod starts:
    `NoSuchEndpointException: No endpoint could be found for: seda://southbound`. Adding the
    `camel:<scheme>` line is the whole fix — with these declared, the seda queue, the file consumer
    and the `poll()`/`pollEnrich()` hops all run and trace normally. If you extend this file with a
    component whose URI comes from a constant, add a line for it too.
  - `mvn:io.quarkus:quarkus-micrometer-registry-prometheus` — backs the UI's Metrics page. Without
    it the metrics endpoint does not exist; see [The CamelBee UI](#the-camelbee-ui).
- **Zero `quarkus.index-dependency` config is required**: the jar ships a Jandex index
  (`META-INF/jandex.idx`), so Quarkus/Arc auto-discovers its CDI beans.
- `build-property` lines turn on the CamelBee endpoints, which are gated by Quarkus
  `@IfBuildProperty` (build-time). The `property` lines enable tracing at runtime and set the
  sample's own properties (`camelbee.sample.self-url`, timer period/delay) — override them per run
  with `kamel run -p <key>=<value>`.
- `configure()` looks `CamelBeeRouteConfigurer` up from the Camel registry and calls
  `configureRoute(this)`. On Quarkus this is opt-in per route builder, and without it the node-id
  and poll intercept strategies are never installed — traced messages then lose their endpoint ids
  and the `poll()` / `pollEnrich()` hops. The lookup is by type (rather than CDI injection) because
  Camel K compiles this file outside CDI.
- `trait=service.enabled=true` exposes the HTTP port as a Kubernetes Service.

## Which core, and why

Camel K ships its own runtime and does not follow CamelBee's Camel version. Ask your operator what
it actually runs:

```sh
kubectl get camelcatalog -o jsonpath='{.items[0].spec.runtime.metadata}'
```

On Camel K 2.10.1 that reports `camel-quarkus 3.15.3 / camel 4.8.5 / quarkus 3.15.4`, whereas
CamelBee's main build targets Camel 4.21. Hence a second artifact, built from the *same sources* by
[`core/quarkus-core/pom-camelk.xml`](../../core/quarkus-core/pom-camelk.xml):

| | `camelbee-quarkus-core` | `camelbee-quarkus-core-camelk` |
|---------------------|-------------------------|--------------------------------|
| Camel | 4.21 | 4.8.5 |
| REST/Jackson deps | `provided` | transitive |
| `cxf-soap` | `provided` | absent (unused by the core) |
| Jandex index format | v13 | v12 (Camel K's reader caps at v12) |

CamelBee's source needs **no changes** to run on 4.8.5 — 155 of its 157 tests pass there unmodified.
The two exclusions are characterization tests that pin 4.21's exact output; the pom documents them.

**Known difference on Camel K:** `.description()` binds to the *route* on Camel 4.8.5 rather than to
the node, so route and node description text is wrong in the UI here. Cosmetic, but real. Everything
else — topology, tracing, replay, metrics — behaves the same. The recipe strings also differ
(`To[x]` vs `to[x]`, `DynamicTo[x]` vs `DynamicTo[toD[x]]`), but the UI parses both.

The **starters are not usable on Camel K**: `camelbee-quarkus-starter` pulls the 4.21 core.

## Prerequisites

- A Kubernetes cluster with the **Camel K operator** installed, and the `kamel` CLI — if you have
  neither, [Running it locally](#running-it-locally) below sets both up from scratch on minikube.
- **The JDK 21 flavour of the operator** (`apache/camel-k:<version>-21-jdk`). The default image
  builds and runs integrations on JDK 17, and the core needs 21 (`ExchangeUtils` uses pattern
  matching in `switch`), so on a stock operator the integration dies with
  `UnsupportedClassVersionError`. See
  [the Camel K JDK docs](https://camel.apache.org/camel-k/2.10.x/installation/advanced/jdk-version.html).
- **An operator whose runtime matches the published core.** `camelbee-quarkus-core-camelk:4.0.0` is
  on Maven Central, built against the runtime Camel K 2.10.1 ships (`camel-quarkus 3.15.3` /
  Camel 4.8.5), so the operator resolves it in-cluster with nothing for you to build or host. On an
  operator with a *different* runtime you have to rebuild the core against it and make your build
  reachable — see [Rebuilding the core for a different Camel K
  runtime](#rebuilding-the-core-for-a-different-camel-k-runtime). Note that the operator builds
  in-cluster, so a `mvn install`-ed jar sitting in your `~/.m2` is never enough on its own.

## Running it locally

"Locally" means a local Kubernetes cluster — Camel K always needs an operator and a container
registry to build and push the integration image.

> **There is no clusterless dev mode.** `kamel local run` was removed in Camel K 2.x, and this file
> cannot be run with Camel JBang either: its modeline dependencies and the CamelBee core are
> Quarkus-specific, while JBang runs on `camel-main`. To see this exact topology and the CamelBee UI
> without any cluster, run the [standalone sample](../allcomponent-standalone-sample/README.md)
> instead (`mvn compile exec:java`, UI on <http://localhost:8081/camelbee>) — it is the same graph on
> `camel-main`. For the Camel Quarkus runtime with live reload, the
> [Quarkus sample](../allcomponent-quarkus-sample/README.md) runs under `mvn quarkus:dev`.

### 0. Tooling

```sh
brew install minikube kubernetes-cli helm   # or your platform's equivalents
brew install kamel                          # Camel K CLI; otherwise grab a release binary from
                                            # https://github.com/apache/camel-k/releases
kamel version                               # should match the operator version installed below
```

### 1. Start a cluster and a registry

Camel K compiles the integration and bakes it into a container image **inside the cluster**, then
pushes that image to a registry. On minikube the built-in one is enough:

```sh
minikube start --cpus 4 --memory 8192
minikube addons enable registry

# the address the cluster uses to reach that registry - needed in step 2
kubectl -n kube-system get svc registry -o jsonpath='{.spec.clusterIP}{"\n"}'
```

The minikube node is itself a Docker container, so the cluster stops when Docker stops. It does not
restart on its own — `minikube start` resumes it with everything intact.

kind works too, but you have to run a local registry container yourself. Alternatively skip the
local registry and push to Docker Hub / GHCR by setting the registry address, organization and a
pull secret in step 2.

### 2. Install the operator

Install the **`-21-jdk`** image (see [Prerequisites](#prerequisites)):

```sh
helm repo add camel-k https://apache.github.io/camel-k/charts/
helm install camel-k camel-k/camel-k \
  --set operator.image=docker.io/apache/camel-k:2.10.1-21-jdk
```

The registry is **not** a Helm value — the chart has no `platform.build.registry.*` keys. Configure
it by creating the IntegrationPlatform, using the ClusterIP from step 1:

```sh
kubectl apply -f - <<EOF
apiVersion: camel.apache.org/v1
kind: IntegrationPlatform
metadata:
  name: camel-k
spec:
  build:
    registry:
      address: <registry-clusterIP>:80
      insecure: true
EOF

kubectl get integrationplatform -w   # wait for Ready
```

The operator only manages the namespace it is installed in (unless installed cluster-wide), so run
the integration in that same namespace — the commands below assume the current one.

### 3. Run the integration

From this directory:

```sh
kamel run MusicianRoute.java
```

Everything (dependencies, build/runtime properties, service trait) is declared via the modeline, so
no extra flags are needed — unless you are serving the core yourself, in which case add
`--maven-repository` as shown [below](#serving-the-core-from-your-machine). The **first run is slow**
(several minutes): the operator resolves all Maven dependencies and builds a container image from
scratch. Later runs reuse the cached IntegrationKit.

The integration name comes from the file name, so `MusicianRoute.java` becomes `musician-route`.
Watch it start with:

```sh
kubectl get integration -w
kamel logs musician-route
```

`--dev` streams logs and redeploys on file changes. To override a property for a single run:

```sh
kamel run MusicianRoute.java -p camelbee.sample.timer-period=60000
```

### 4. Open the UI and generate traffic

```sh
kubectl port-forward svc/musician-route 8080:80
```

Then open <http://localhost:8080/camelbee>. The timer route generates traffic every 10 seconds; you
can also drive the REST entry points yourself:

```sh
curl -X POST http://localhost:8080/api/musicians \
  -H 'Content-Type: application/json' \
  -d '{"name":"Miles","instrument":"Trumpet"}'
curl http://localhost:8080/api/musicians
```

To feed the file consumer, drop a file into the pod's input directory:

```sh
kubectl exec deploy/musician-route -- sh -c 'echo hello > /tmp/camelbee/inputdir/test.txt'
```

### 5. When something goes wrong

The integration goes through *build* (in-cluster Maven + image build) before it ever runs, and the
two failure modes look different:

```sh
kamel describe integration musician-route   # phase + conditions, incl. why it is not running
kubectl get integrationkits                 # a kit in Error = dependency or compile failure
kubectl logs deploy/camel-k-operator        # operator + in-cluster Maven build log
kubectl logs deploy/musician-route          # the integration itself, once it is running
```

Failures actually hit while validating this sample, and what each means:

- **`Checksum validation failed, no checksums available`** — you are serving a *local* Maven
  repository directly. `mvn install` never writes `.sha1`/`.md5`, and Maven rejects remote artifacts
  without them. Use `deploy:deploy-file` as shown below, which generates them.
- **The same resolution error repeating after you fixed it** — the operator caches failed lookups
  and never re-checks a *release* artifact. Clear it:
  `kubectl exec deploy/camel-k-operator -- rm -rf /etc/maven/m2/io/camelbee`
- **`org.jboss.jandex.UnsupportedVersion: Can't read index version 13`** — the core was built with a
  Jandex newer than the runtime's. `pom-camelk.xml` pins the matching one; rebuild with it.
- **`UnsupportedClassVersionError`** — the operator is not the `-21-jdk` image.
- **`NoSuchEndpointException`** at startup, kit built fine — a component is missing from the
  modeline; see [How it works](#how-it-works).
- **Image push failures** — the registry address in step 2 is wrong or not reachable in-cluster.

### 6. Deploy a new version, or come back later

**Steps 0–2 are one-time.** The tooling, the cluster and the operator survive restarts; only the
integration is redeployed as you work.

*Changed `MusicianRoute.java`:* run the same command again. `kamel run` updates the existing
integration in place (it prints `Integration "musician-route" updated`). A route-only edit reuses
the cached IntegrationKit and is quick; changing the modeline's dependencies or build properties
forces a new kit and takes minutes again.

```sh
kamel run MusicianRoute.java            # add --maven-repository ... if you serve the core yourself
```

*Rebuilt `camelbee-quarkus-core-camelk` without bumping its version:* the operator has already
cached that exact coordinate and will happily reuse the old jar, so clear its Maven cache in
between — this is the step that catches people out:

```sh
mvn -f ../../core/quarkus-core/pom-camelk.xml clean install
# re-publish into the served repository (steps 2-3 of "Serving the core from your machine")
kubectl exec deploy/camel-k-operator -- rm -rf /etc/maven/m2/io/camelbee
kamel delete musician-route
kamel run MusicianRoute.java --maven-repository "http://host.minikube.internal:8000@id=local-m2"
```

*After `minikube stop`:* the node is a Docker container, so it does not come back on its own. Start
Docker, then:

```sh
minikube start                                    # no flags needed - resumes the same node
kubectl get pod -w                                # wait for the pods to be Ready
kubectl port-forward svc/musician-route 8080:80   # for the UI
```

That is all. The operator, the IntegrationPlatform, the cached kits and the pushed images all
survive, so nothing rebuilds and the integration pod restarts by itself within a minute or two.
You do **not** need the local Maven repository server running to resume — that is only consulted
when a new kit has to be built, i.e. after you rebuild the core or change modeline dependencies.

### 7. Clean up

```sh
kamel delete musician-route
minikube stop            # resumes later with 'minikube start', keeping the operator and kit cache
```

To remove everything permanently instead:

```sh
helm uninstall camel-k
minikube delete
```

## Serving the core from your machine

**Not needed for the normal case** — the published core resolves from Maven Central by itself. This
is for the two cases where the operator cannot use it: your operator runs a different Camel K
runtime (so the core has to be
[rebuilt](#rebuilding-the-core-for-a-different-camel-k-runtime) first), or you are testing a local
change to the core.

The operator builds in-cluster and cannot see your `~/.m2`, and a local repository has no checksums
so it cannot be served as-is. Publish into a throwaway repository *with* checksums, serve that over
HTTP, and point `kamel run` at it:

```sh
# 1. build the Camel K variant (from core/quarkus-core)
mvn -f pom-camelk.xml clean install

# 2. copy the artifacts out of ~/.m2 - deploy:deploy-file refuses to publish from inside it
mkdir -p /tmp/camelbee-stage /tmp/camelbee-repo
cp ~/.m2/repository/io/camelbee/camelbee-quarkus-core-camelk/4.0.0/camelbee-quarkus-core-camelk-4.0.0.{jar,pom} \
   /tmp/camelbee-stage/

# 3. publish into a real repository layout (this is what generates the checksums)
mvn org.apache.maven.plugins:maven-deploy-plugin:3.1.2:deploy-file \
  -DgroupId=io.camelbee -DartifactId=camelbee-quarkus-core-camelk -Dversion=4.0.0 -Dpackaging=jar \
  -Dfile=/tmp/camelbee-stage/camelbee-quarkus-core-camelk-4.0.0.jar \
  -DpomFile=/tmp/camelbee-stage/camelbee-quarkus-core-camelk-4.0.0.pom \
  -Durl=file:///tmp/camelbee-repo -DrepositoryId=local-m2

# 4. serve it (leave this running)
cd /tmp/camelbee-repo && python3 -m http.server 8000 --bind 0.0.0.0
```

Then run the integration against it. `host.minikube.internal` is how pods reach your machine:

```sh
kamel run MusicianRoute.java --maven-repository "http://host.minikube.internal:8000@id=local-m2"
```

Check reachability first if it fails — the build runs inside the operator pod, so that is what has
to reach you:

```sh
kubectl exec deploy/camel-k-operator -- \
  curl -s -o /dev/null -w '%{http_code}\n' \
  http://host.minikube.internal:8000/io/camelbee/camelbee-quarkus-core-camelk/4.0.0/camelbee-quarkus-core-camelk-4.0.0.pom
```

Remember to clear the operator's Maven cache (see [step 5](#5-when-something-goes-wrong)) after
republishing the same version, or it will keep serving the previous result.

## The CamelBee UI

The embedded UI ships inside the core jar and is served at `/camelbee`; point it at the same origin.
Useful endpoints:

- `GET  /camelbee/routes`         — route topology
- `POST /camelbee/tracer/status`  — toggle tracing; `Content-Type: application/json` with a JSON
  string body, i.e. `-d '"ACTIVE"'` (a bare `ACTIVE` returns 415)
- `GET  /camelbee/messages`       — traced messages
- `GET  /health`, `GET /metrics` — health and Prometheus metrics (moved off Quarkus's `/q`
  prefix by the modeline, so the UI's default Settings work unchanged)

> **Two things are needed before the Metrics page shows anything**, and missing either looks
> identical in the UI ("Waiting for data…" / "No metrics available"). Both are handled by the
> modeline, so no UI setting has to be changed:
>
> 1. **The extension.** Neither the UI nor Camel K ships a Micrometer registry, so the metrics
>    endpoint would not exist at all:
>    ```
>    // camel-k: dependency=mvn:io.quarkus:quarkus-micrometer-registry-prometheus
>    ```
> 2. **The paths.** Quarkus serves these under `/q` by default, but the UI defaults to `/health`
>    and `/metrics`. Rather than making every user edit SETTINGS, this sample moves the endpoints —
>    the same thing [the Quarkus sample](../allcomponent-quarkus-sample/README.md) does in its
>    `application.yml`. A leading `/` makes each path absolute instead of relative to `/q`:
>    ```
>    // camel-k: build-property=quarkus.smallrye-health.root-path=/health
>    // camel-k: build-property=quarkus.micrometer.export.prometheus.path=/metrics
>    ```
>    They must be `build-property`, not `property`: Quarkus fixes both at build time, so setting
>    them at runtime has no effect. After this `/q/health` and `/q/metrics` return 404 — the
>    endpoints moved, they were not duplicated.
>
> Health itself needs no extra dependency — `camel-quarkus-microprofile-health` comes with the core.

For a guide to the UI's pages and features, see the
[CamelBee User Guide](../../docs/camelbee_userguide.md).

### Before exposing this outside a local cluster

This sample runs with `camelbee.auth-enabled=false` in its modeline, so the UI opens without a login
— convenient for a local kind cluster, and the reason the `kubectl port-forward` step above just
works.

**That is a sample setting, not the default.** CamelBee 4.0 requires a login unless you switch it
off. Remove that modeline entry for anything beyond a local cluster and set a password:

```
// camel-k: property=camelbee.username=camelbee
// camel-k: property=camelbee.password={{env:CAMELBEE_PASSWORD}}
```

Even with a login, this sample uses `trait=service.enabled=true`, which publishes the integration's
HTTP port as a Kubernetes Service — and `/camelbee` rides on that same port. On a shared or
internet-facing cluster:

- **Do not add an Ingress or OpenShift Route that exposes `/camelbee`.** Keep the Service
  `ClusterIP` and reach the UI with `kubectl port-forward`, which is what step 4 above already does.
- If the integration must be exposed publicly for its own API, deny `/camelbee` and `/camelbee/*`
  at the ingress, since CamelBee shares the application port on Quarkus.
- Restrict in-cluster reachability with a `NetworkPolicy` if other workloads should not be able to
  call it.
- Serve it over TLS if it leaves the cluster at all — the password and session token are readable in
  transit otherwise.

To use the cluster's own identity provider instead of CamelBee's single credential, keep
`camelbee.auth-enabled=false` and use Quarkus' HTTP policy layer — the properties are in the
[Quarkus core README](../../core/quarkus-core/README.md#securing-the-camelbee-endpoints), settable
from the modeline with `// camel-k: property=...`. Remember that `camelbee.context-enabled` is a
**build-time** property on Quarkus, so removing the API entirely needs `build-property=` and a
rebuild.
