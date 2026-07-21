# CamelBee Common

Shared build configuration packaged as an artifact (`io.camelbee:common`) and unpacked by the parent POM during the build. It is not a code library — it holds the static-analysis and formatting rules applied to every module:

- `config/checkstyle/` — Checkstyle rules (Google style)
- `config/spotbugs/` — SpotBugs exclusion filters
- `formatter/` — Eclipse/IntelliJ Java formatter profiles used by the formatter Maven plugin
