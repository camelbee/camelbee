# CamelBee Starters

Starter parent POMs for quickly bootstrapping a new CamelBee-enabled project. Each starter automatically includes the matching core library, the embedded UI, and all required dependencies — including full dependency version management:

- `camelbee-quarkus-starter` — for Camel Quarkus projects
- `camelbee-springboot-starter` — for Camel Spring Boot projects
- `camelbee-standalone-starter` — for plain standalone Camel projects (`camel-main`, no Spring Boot or Quarkus); also brings `camel-platform-http-main` and Micrometer/Prometheus metrics support

## Usage

Use the starter as your project's parent POM (suitable for new projects without an existing parent):

```xml
<parent>
  <groupId>io.camelbee</groupId>
  <artifactId>camelbee-quarkus-starter</artifactId> <!-- or -springboot- / -standalone- -->
  <version>3.3.1</version>
</parent>
```

For projects that already have a parent POM, add the core library as a plain dependency instead — see the core READMEs for full setup instructions:

- [Quarkus Core README](../core/quarkus-core/README.md)
- [Spring Boot Core README](../core/springboot-core/README.md)
- [Standalone Core README](../core/standalone-core/README.md)

For working examples using the starters, see the [camelbee-examples](https://github.com/camelbee/camelbee-examples) repository and the [examples](../examples) folder in this repository.
