# feature-add-opens

## What this tests

`--add-opens` in the surefire JVM arguments (runtime reflection, not compile-time).

## Why it is interesting

`--add-opens` is a runtime-only flag — unlike `--add-exports`, it does not appear in compiler arguments. Frameworks like Mockito, Spring, and Hibernate rely on it for deep reflection. spin must read surefire `<argLine>` and forward it correctly to the test JVM.

## Expected behavior

Tests access `String.hash` via reflection. Without `--add-opens=java.base/java.lang=ALL-UNNAMED`, the JVM throws `InaccessibleObjectException`.

## Known quirks

The `String.hash` field name is JDK-internal and could change. If the test breaks on a future JDK, substitute another private field in `java.lang`.
