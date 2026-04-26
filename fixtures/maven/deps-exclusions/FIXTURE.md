# deps-exclusions

## What this tests

`<exclusions>` on a direct dependency to drop a transitive dep from the resolved classpath.

## Why it is interesting

Exclusions are common when a transitive dep conflicts with another or is provided by the container. spin must honour exclusion declarations when building the classpath — a build tool that ignores them will include the excluded jar, causing the test to fail (the class would be found instead of absent).

## Expected behavior

`jackson-annotations` is absent from the runtime classpath even though `jackson-databind` normally declares it as a dependency. `Class.forName` for a jackson-annotations type throws `ClassNotFoundException`.
