# CamelBee Quarkus Core (Camel K runtime)

## What this module is

`camelbee-quarkus-core-camelk` is the same CamelBee engine and Quarkus wiring as
[`camelbee-quarkus-core`](../quarkus-core/README.md), rebuilt against the older Camel Quarkus
baseline that the **Apache Camel K** runtime actually ships. It exists purely for consumption from a
Camel K integration modeline — see the
[Camel K sample](../../examples/allcomponent-camelk-sample/README.md) for a full, cluster-verified
walkthrough.

This module **owns no sources of its own**. It compiles `quarkus-core`'s and `shared-core`'s sources
against Camel K's platform, so both jars are built from exactly the same code — there is nothing
here to keep in sync by hand.

## Why it exists

Not the Camel/Quarkus version by itself — `camelbee-quarkus-core` already supports Quarkus 3.15 LTS,
which is what Camel K runs. Every reason is about **packaging**:

| | `camelbee-quarkus-core` | `camelbee-quarkus-core-camelk` |
|---|---|---|
| REST/Jackson deps | `provided` | transitive (a modeline only pulls transitive dependencies) |
| Engine sources | separate `camelbee-core` jar, needs `quarkus.index-dependency` | inlined, so one indexed jar holds everything |
| `camel-quarkus-cxf-soap` | `provided` | absent (the core never references CXF; pulling it in would drag the whole SOAP stack into every Camel K image) |
| Jandex index format | v13 | v12 (Camel K's Quarkus 3.15–3.21 bundle Jandex 3.2.x, whose reader stops at v12) |

Retire this module when none of those still apply. The [pom.xml](pom.xml) header has the full
rationale, including why it has to be a real directory (`core/quarkus-core-camelk/pom.xml`) rather
than a second POM file inside `quarkus-core` — Quarkus's `BootstrapMavenContext` treats every
`<module>` entry as a directory, and a module pointing at a bare file breaks every `@QuarkusTest` in
the reactor.

## What it produces

Built as part of the normal reactor build (`mvn clean install` from the repo root, or
`mvn -pl core/quarkus-core-camelk -am install` on its own):

```xml
<dependency>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-core-camelk</artifactId>
  <version>4.0.0</version>
</dependency>
```

It ships a Jandex index (`indexVersion` 12, matching Camel K's Jandex reader), so Quarkus/Arc
auto-discovers its CDI beans with **no** `quarkus.index-dependency` configuration needed on the
consumer side — unlike `camelbee-quarkus-core`.

It is published to Maven Central alongside the other CamelBee artifacts, so a Camel K operator
resolves it in-cluster from a modeline with nothing extra to build or host, as long as the operator
runs the same Camel K runtime this module was built against (see below).

## Consuming it from a Camel K modeline

```java
// camel-k: dependency=mvn:io.camelbee:camelbee-quarkus-core-camelk:4.0.0
// camel-k: dependency=camel:direct
// camel-k: dependency=camel:log
// camel-k: build-property=camelbee.context-enabled=true
// camel-k: build-property=camelbee.tracer-enabled=true
// camel-k: property=camelbee.tracer-enabled=true
// camel-k: trait=service.enabled=true
```

Declare every Camel component you use explicitly — Camel K's dependency auto-detection reads URIs
written literally at a `from(...)`/`to(...)` call site and misses any built from a constant, which
then fails at pod startup rather than at build time. **The starters are not usable on Camel K** —
`camelbee-quarkus-starter` pulls the 4.21 core, not this one.

As on plain Quarkus, call the route configurer as the first statement of every `RouteBuilder`, looked
up from the registry by type (Camel K compiles integration files outside CDI):

```java
CamelBeeRouteConfigurer camelBeeRouteConfigurer = getContext().getRegistry()
    .lookupByNameAndType("camelBeeRouteConfigurer", CamelBeeRouteConfigurer.class);
camelBeeRouteConfigurer.configureRoute(this);
```

The full, runnable version of this — REST, timer, file, seda, `multicast`, `wireTap`, both `enrich`
forms, `recipientList`, `dynamicRouter`, `poll()`/`pollEnrich()`, dead-lettering, and Metrics-page
wiring — is [`examples/allcomponent-camelk-sample`](../../examples/allcomponent-camelk-sample/README.md),
verified end to end against a live Camel K/minikube cluster.

## Rebuilding for a different Camel K runtime

This module is built against **one specific** Camel K runtime, pinned in
[`dependencies/quarkus-camelk/pom.xml`](../../dependencies/quarkus-camelk/pom.xml). If your operator
runs a different one, the published artifact is the wrong build. Read what it actually runs:

```sh
kubectl get camelcatalog -o jsonpath='{.items[0].spec.runtime.metadata}'
```

and put the `camel-quarkus` and `quarkus` versions it reports into that BOM — it is the only place
they live; this module inherits them:

```xml
<quarkus.version>3.15.4</quarkus.version>
<camel-quarkus.version>3.15.3</camel-quarkus.version>
```

Then rebuild from the repo root:

```sh
mvn -pl core/quarkus-core-camelk -am install
```

Two settings in [`pom.xml`](pom.xml) are tied to the runtime rather than to CamelBee itself, and are
worth checking if the rebuilt integration fails to build or start:

- `<indexVersion>12</indexVersion>` — the Jandex index format; a newer runtime can usually still
  read v12, so raising it is rarely necessary.
- `<java.version>17</java.version>` — must not exceed the JDK the operator's image runs, or the
  integration dies with `UnsupportedClassVersionError`. The default operator image is JDK 17.

Because the operator builds **in-cluster**, a jar sitting in your local `~/.m2` is never enough on
its own — see
[Serving the core from your machine](../../examples/allcomponent-camelk-sample/README.md#serving-the-core-from-your-machine)
in the Camel K sample for how to publish a rebuilt jar (with its parent POM chain and checksums) to a
repository the operator can reach.

## Testing

This module runs the full shared-core engine suite plus `quarkus-core`'s wiring tests against the
Camel K baseline (not just the reactor's default Camel version), so a 4.8-incompatible engine change
fails here at compile or test time instead of surfacing later on a cluster. Two engine tests are
excluded — both characterization tests that pin the exact output of a newer Camel version — and
documented in [pom.xml](pom.xml) next to the `<test>` filter that excludes them. One real behavioural
difference is not just a test artifact: `.description()` binds to the *route* on Camel 4.8.5 rather
than to the node it follows, so route/node description text in the UI is wrong on Camel K. Everything
else — topology, tracing, replay, metrics — behaves the same as `camelbee-quarkus-core`.

## Related Documentation

- [Which core, and why](../../examples/allcomponent-camelk-sample/README.md#which-core-and-why) — the
  version table above, with how it was verified against a live cluster
- [CamelBee Quarkus Core README](../quarkus-core/README.md) — the sources this module builds, and
  full configuration/security reference (applies here too)
- [CamelBee User Guide](../../docs/camelbee_userguide.md) — a tour of the UI's pages and features
- [Root README](../../README.md) — project overview and the other integration options
