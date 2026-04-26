# plugin-surefire-javaagent-mockito

## What this tests

Surefire configured with a Mockito javaagent via `-javaagent:` in `<argLine>`, using the local Maven repository path.

## Why it is interesting

Mockito inline mocking (and bytecode-manipulation frameworks generally) require the jar to be attached as a javaagent at JVM startup, not just on the classpath. The path is typically expressed as `${settings.localRepository}/...` which must be resolved by the build tool before passing it to the JVM. spin.build has a parallel requirement; see also `spin-modules/spin-junit-module`.

## Expected behavior

Mockito mocks `Repository` without any `InaccessibleObjectException`. Tests pass cleanly.

## Known quirks

The `-javaagent` path embeds the Mockito version. If `mockito.version` is bumped in the pom, the path updates automatically via property interpolation.
