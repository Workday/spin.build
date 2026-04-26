# plugin-shade

## What this tests

Uber-jar assembly via maven-shade-plugin, producing a `-shaded` classified artifact that bundles guava.

## Why it is interesting

shade rewrites bytecode (relocating packages) and merges resources, which happens in the `package` phase after compilation. spin must not mistake the shaded jar for the primary artifact when computing downstream classpaths.

## Expected behavior

`mvn verify` produces two jars: the thin jar and `*-shaded.jar`. The shaded jar contains guava classes at their original package paths (no relocation configured here). Tests pass against the thin jar.
