# feature-add-exports

## What this tests

`--add-exports` in compiler arguments and the matching surefire JVM flag.

## Why it is interesting

Many real-world projects access JDK internals via `--add-exports`. The flag must appear in both `<compilerArgs>` (javac) and `<argLine>` (JVM at test runtime). Omitting either causes an `InaccessibleObjectException` or compile error. spin must extract and forward both independently.

## Expected behavior

Compiles and tests pass using `sun.security.util` internal API exposed via `--add-exports=java.base/sun.security.util=ALL-UNNAMED`.

## Known quirks

The specific internal API used (`KnownOIDs`, `ObjectIdentifier`) may vary across JDK releases. If this fixture breaks on a newer JDK, substitute any other `sun.*` internal class that requires `--add-exports`.
