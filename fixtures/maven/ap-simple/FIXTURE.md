# ap-simple

## What this tests

A basic annotation processor that generates a single source file per annotated type.

## Why it is interesting

This is the baseline AP case. The processor lives in the same source tree as the code it processes, which requires a two-pass compile (AP compiled first, then run on the main sources). spin must correctly order these passes.

## Expected behavior

- `@Logged` on `Widget` causes the AP to generate `WidgetLogger.java`
- The generated class is available to the test which verifies it exists via `Class.forName`

## Notes

The processor is registered via `META-INF/services` (classic SPI), not via `module-info.java`, so it works in an unnamed-module context. The `ap-with-dependencies` fixture covers APs with external processor deps.
