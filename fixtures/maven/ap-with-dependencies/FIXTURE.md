# ap-with-dependencies

## What this tests

An annotation processor that itself depends on external libraries (`guava`, `auto-service`), declared via `<annotationProcessorPaths>` in the compiler plugin.

## Why it is interesting

`<annotationProcessorPaths>` is a separate classpath from `<dependencies>`. The AP's deps must be available at compile time on the processor path but are not necessarily on the runtime classpath. spin must model this distinction and resolve both paths independently.

## Expected behavior

- `auto-service` generates `META-INF/services/javax.annotation.processing.Processor` for `ImmutableProcessor`
- `@Immutable` on `Config` triggers generation of `ConfigView` which imports `ImmutableList` from guava
- The test confirms the generated `ConfigView.fields()` returns a non-empty list

## Judgment call

Using `auto-service` as the processor-path dep doubles as a test that processor-path deps are not leaked onto the compile or runtime classpath, which is a common build tool bug.
