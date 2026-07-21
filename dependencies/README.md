# CamelBee Dependencies (BOMs)

Bill of Materials modules that centralize dependency version management for each supported runtime:

- `quarkus` (`camelbee-quarkus-dependencies`) — imports the Quarkus platform and Camel Quarkus BOMs, plus versions for everything the Quarkus modules and samples need
- `springboot` (`camelbee-springboot-dependencies`) — imports the Spring Boot and Camel Spring Boot BOMs
- `standalone` (`camelbee-standalone-dependencies`) — imports the plain Camel BOM plus Micrometer/Jackson versions for `camel-main` applications

These act as the parents of the corresponding core, security, starter, and example modules, so a runtime's framework versions are bumped in exactly one place.
