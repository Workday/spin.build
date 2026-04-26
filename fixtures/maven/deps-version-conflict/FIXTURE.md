# deps-version-conflict

## What this tests

Maven nearest-wins version mediation when the same artifact is declared at two different versions.

## Why it is interesting

When two deps require the same transitive artifact at different versions, Maven picks the one declared closest to the root (nearest-wins). spin must replicate this mediation when building its own classpath — using a different strategy (e.g. highest-wins) will produce a different jar than Maven does, which is a silent correctness bug.

## Expected behavior

Maven selects one guava version (the last declaration in this pom wins at the same depth). The test confirms guava is functional regardless of which version was chosen. `mvn dependency:tree` shows the resolved version.
