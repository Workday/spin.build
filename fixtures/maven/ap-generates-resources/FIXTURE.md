# ap-generates-resources

## What this tests

An annotation processor that emits a resource file (`META-INF/registry.txt`) rather than Java source.

## Why it is interesting

Most AP examples generate source. Resource generation uses `Filer.createResource(CLASS_OUTPUT, ...)` and writes to the class output directory rather than the source output directory. Build tools must ensure the resource ends up in the final JAR, not just the generated-sources tree.

## Expected behavior

`@Registered` on `ServiceA` and `ServiceB` causes the AP to write `META-INF/registry.txt` listing both class names. The test loads the resource from the classpath and asserts both entries are present.
