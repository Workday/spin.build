# module-multi-named

## What this tests

Two named JPMS modules in a Maven multi-module build with a `requires` edge between them: `api` declares an interface, `impl` requires `api` and implements it.

## Why it is interesting

This is the core JPMS multi-module case. The compiler must resolve `api`'s module descriptor before compiling `impl`. Maven's reactor order handles this, but spin must replicate the dependency ordering and put the api module on `impl`'s module path (not just classpath). Surefire in `impl` must open the test package or use `--add-opens` for reflection-based test discovery.

## Expected behavior

`mvn verify` from the root builds both modules in reactor order (api first). `impl` compiles against the `Arithmetic` interface from `api`. Tests in `impl` pass with the `api` jar on the module path.

## Structure

```
module-multi-named/
├── api/    — module build.spin.fixtures.multiNamed.api
└── impl/   — module build.spin.fixtures.multiNamed.impl, requires api
```
