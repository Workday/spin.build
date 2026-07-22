# spin.build — Claude Context

## Codebase Overview

**spin** is a script-free Java 25 build system that infers what to build by inspecting project structure via pluggable Extensions (discovered via JPMS `ServiceLoader`), then executes a dependency-ordered graph of `Task`s. Extensions auto-detect applicability, declare task dependencies via annotations (`@From`, `@After`, `@Before`, `@PreProcess`, `@PostProcess`), and are composed via a DI framework (`build.codemodel.dependency.injection`, Jakarta Inject compatible). spin is self-hosting: spin₁ builds spin₂ builds spin₃ during the Maven `prepare-package` phase.

**Stack:** Java 25, Maven multi-module, Eclipse Aether (artifact resolution), GraphQL Java, serve.build (HTTP/LSP), FreeMarker, JUnit 6.

**Structure:**
- `spin-api/` — all interfaces, annotations, and option types (the public contract)
- `spin-common/` — `DefaultProgram` (program inference + execution), `DefaultInvocable`, DI resolvers, utilities
- `spin-engine/` — `DefaultEngine` (ServiceLoader discovery, workspace/project tree), `HeapBasedCache`
- `spin/` — `Spin.main()` CLI entry point; `Launcher` (Maven exec bridge for self-hosting); jlink packaging
- `spin-modules/` — pluggable extension modules:
  - `spin-java-module` — Java compile/link/javadoc, multi-version JARs, jlink runtime images
  - `spin-module-system-module` — `ModuleGraphClassifier` (split-package resolution), `ModuleCatalog`, artifact versioning
  - `spin-maven-module` — Eclipse Aether resolution, Maven packaging (JAR/POM/sources/javadoc)
  - `spin-junit-module` — JUnit 5 test runner (Java 8 and Java 25 variants)
  - `spin-configuration-module` — `.spin/` hierarchical config, `.spinignore` workspace detection
  - `spin-console-module` — GraphQL HTTP workspace inspector (stub)
  - `spin-language-server-module` — LSP server (TCP/stdio, stub)
  - `spin-checkstyle-module`, `spin-clean-module`, `spin-git-module`, `spin-reporting-module`
- `spin-testing/` — `WorkspaceDiscovery` JUnit 5 extension for integration tests
- `spin-collider/` — subprocess launcher for integration tests requiring a running server
- `spin-classifier-maven-plugin/` — Maven plugin that runs `ModuleGraphClassifier` at build time for JPMS test compilation

For detailed architecture, see [docs/CODEBASE_MAP.md](docs/CODEBASE_MAP.md).

## Code Conventions (from coding-conventions.md)

- Google Java Style; enforced by Checkstyle
- No nulls — use `Optional`/`Stream`
- No logging framework — use `TelemetryRecorder`
- No static state except constants
- Getters: `age()` not `getAge()`; `Stream` returns have no `get` prefix
- Interface names: no `I` prefix; implementation classes: no `Impl` suffix; use `Default` prefix
- Constructors: private or package-private; use Builder or static factory
- DI via `build.codemodel.dependency.injection` everywhere

## Key Gotchas

- Tasks must be `public static` non-abstract inner classes of their Plugin to be discovered via reflection
- `version.properties` is not yet used by the engine (marked "coming soon")
