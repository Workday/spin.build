# plugin-jlink

## What this tests

Custom runtime image assembly via maven-jlink-plugin with a named launcher entry point.

## Why it is interesting

jlink produces a self-contained runtime image that bundles only the required JDK modules. spin already uses jlink for its own packaging. This fixture verifies that spin can correctly read and forward jlink configuration from a project pom — particularly the `<launcher>` syntax which embeds both module and main class.

## Expected behavior

`mvn verify` produces a jlink image under `target/`. The image contains a `bin/calculator` launcher script. Tests pass normally via surefire before the image is assembled.

## Notes

Requires a named module (`module-info.java`) — this fixture includes one. The module must `requires` only JDK modules to keep the image small.
