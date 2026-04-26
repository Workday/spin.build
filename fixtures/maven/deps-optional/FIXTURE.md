# deps-optional

## What this tests

A dependency declared `<optional>true</optional>`, which is available in this project but not transitively inherited by consumers.

## Why it is interesting

Optional deps are a common pattern in multi-purpose libraries (e.g. a serialisation library that optionally supports Jackson). The dep is real and usable in this project. The distinction matters to spin when computing downstream classpaths — an optional dep must not appear in the exported dependency list of a module that other modules depend on.

## Expected behavior

Guava compiles and is available at test runtime. The test directly uses `ImmutableList` to confirm this. A downstream project depending on this artifact would NOT get guava transitively.
