# feature-release-21

## What this tests

Explicit `<release>21</release>` on the compiler plugin (via the `maven.compiler.release` property).

## Why it is interesting

The `--release` flag is the modern replacement for `-source`/`-target`. It also enforces the class-file API surface — you cannot accidentally call APIs added after the target release. spin must read and forward this property correctly.

## Expected behavior

Compiles cleanly targeting Java 21 bytecode. Uses records (standard since Java 16) as a minimal Java 21 feature.
