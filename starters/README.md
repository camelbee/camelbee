# CamelBee Starters

Starter parent POMs for quickly bootstrapping a new CamelBee-enabled project. Each starter automatically includes the matching core library, the embedded UI, and all required dependencies — including full dependency version management:

- `camelbee-quarkus-starter` — for Camel Quarkus projects
- `camelbee-springboot-starter` — for Camel Spring Boot projects
- `camelbee-standalone-starter` — for plain standalone Camel projects (`camel-main`, no Spring Boot or Quarkus); also brings `camel-platform-http-main` and Micrometer/Prometheus metrics support

## Starter or core? 

The two paths differ in **who decides the framework versions**:

| | You get | Versions decided by | Use when |
|---|---|---|---|
| **Starter as parent** | Core + embedded UI + every required dependency, fully version-managed | **CamelBee** — Spring Boot 4.1 / Camel 4.22, standalone Camel 4.22, Quarkus 3.38 / Camel 4.21 | Starting a new project and happy on a current stack |
| **Core as a dependency** | Just the library | **You** — framework deps are `provided`, so your BOM wins | You already have a parent POM, or you are on an older stack |

> **Upgrading from an earlier CamelBee?** In 4.0.0 the cores moved their framework dependencies to
> `provided` scope. That changes nothing for starter users — the starter declares those dependencies
> itself, so they still resolve at compile scope exactly as before. It only affects the core-as-a-
> dependency path, where you now declare them yourself (each core README lists which).

The core path is not a lesser option: it runs on **Camel 4.8+ / Quarkus 3.15 LTS+ / JDK 17+**, so an
existing service does not have to upgrade its stack to get the current CamelBee. Each core README
carries the exact floor, and `examples/core-only-*-sample` proves it on every build.

## Usage

Use the starter as your project's parent POM (suitable for new projects without an existing parent):

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-starter</artifactId> <!-- or -springboot- / -standalone- -->
  <version>4.0.0</version>
</parent>
```

For projects that already have a parent POM, add the core library as a plain dependency instead — see the core READMEs for full setup instructions:

- [Quarkus Core README](../core/quarkus-core/README.md)
- [Spring Boot Core README](../core/springboot-core/README.md)
- [Standalone Core README](../core/standalone-core/README.md)

For working examples using the starters, see the [camelbee-examples](https://github.com/camelbee/camelbee-examples) repository and the [examples](../examples) folder in this repository.
