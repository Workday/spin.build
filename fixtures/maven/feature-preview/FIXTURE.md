# feature-preview

## What this tests

Compilation with `--enable-preview` and the corresponding surefire `--enable-preview` JVM flag.

## Why it is interesting

Preview features require the flag at both compile time and runtime. A build tool must thread it through the compiler plugin *and* the test runner. Missing either side produces a class loading error at runtime, not a compile error, which can be confusing.

## Expected behavior

- Maven compiles cleanly with `<enablePreview>true</enablePreview>` and `<release>24</release>`
- Tests run without `java.lang.UnsupportedClassVersionError` or similar

## Notes

The source uses pattern matching in switch (standard since Java 21) rather than a true preview feature so the fixture compiles on any JDK 21+. The point is exercising the flag wiring, not a specific preview language feature.
