# core-only-springboot-sample

Minimal Spring Boot application that uses **`camelbee-springboot-core` as a plain dependency** — README
[Option 1](../../README.md#option-1-add-the-core-library-as-a-dependency-recommended) — rather than
inheriting from a CamelBee starter.

It is **not** a feature demo. [`allcomponent-springboot-sample`](../allcomponent-springboot-sample) is that.
This is a wiring test: two routes, no infrastructure, and a check that CamelBee serves its API and
UI and reports the real topology.

## Why it exists

The `allcomponent-*` samples all use a CamelBee starter as their parent, so the starter supplies
the framework versions, the dependencies and the build plugins. That makes them structurally unable
to catch a break in the core-as-a-dependency path — which is the path the README recommends for
existing applications.

Two things only this module can catch:

- **The published version floor is real.** It pins Spring Boot 3.3.13 + Camel 4.8.0, the oldest combination the
  [version table](../../README.md#option-1-add-the-core-library-as-a-dependency-recommended)
  claims, while the core it depends on is built against a much newer stack. That makes this a
  binary-compatibility check, not just a source-level one.
- **The README's dependency list is complete.** The core scopes its framework dependencies
  `provided`, so a consumer has to declare them. They are declared in this POM and nowhere else;
  drop one and this module fails.

It has **no `<parent>`** on purpose — it stands alone with its own BOMs and plugin versions, the
way a real consuming project does. It is aggregated by `examples/pom.xml` but inherits nothing
from it.

## Run it

```bash
mvn -pl examples/core-only-springboot-sample -am install   # from the repo root
mvn -f examples/core-only-springboot-sample/pom.xml spring-boot:run
```

Then open <http://localhost:8080/camelbee>.

> Raising the pinned versions in this POM defeats the purpose of the module. If the floor genuinely
> moves, change the README version table first, then follow it here.
