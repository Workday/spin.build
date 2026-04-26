# deps-transitive-basic

## What this tests

Transitive dependency resolution: only `jackson-databind` is declared; `jackson-core` and `jackson-annotations` arrive on the classpath via transitive resolution.

## Why it is interesting

This is the baseline dependency case. spin must resolve the full transitive closure, not just declared deps. A build tool that only puts direct deps on the compile classpath will fail here with a `ClassNotFoundException` for internal Jackson types.

## Expected behavior

`jackson-core` classes are available at runtime even though the dep is not declared. `ObjectMapper.writeValueAsString` succeeds, confirming the transitive dep was resolved and placed on the classpath.
