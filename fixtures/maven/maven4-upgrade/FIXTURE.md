# maven4-upgrade

## What this tests

A plain project built with Maven 4 (4.0.0-rc-5) instead of Maven 3.

## Why it is interesting

Maven 4 changes the POM model (4.1.0 model version), the reactor, the plugin API surface, and the dependency resolution engine. spin.build itself migrated to Maven 4 (PR #44). This fixture confirms that spin can correctly read and handle a project built under Maven 4 — both the Maven 4 pom format and any Maven 4-specific plugin behaviour.

## Expected behavior

`mvn verify` passes under Maven 4. The fixture is intentionally minimal (Calculator + tests) so that any failure is attributable to the Maven 4 runtime, not to the project structure.

## Notes

All other fixtures use Maven 3.9.14. Only this fixture's `.mvn/wrapper/maven-wrapper.properties` points at Maven 4.0.0-rc-5.
