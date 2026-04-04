# spin.build — Claude Context

## Codebase Overview

**spin** is a Java build system that infers what to do by inspecting project structure via pluggable Extensions (discovered via `ServiceLoader`), then executes a dependency-ordered graph of `Task`s — no build scripts required. Extensions auto-detect what they apply to, declare task dependencies via annotations (`@From`, `@After`, `@Before`), and are composed via a DI framework (`build.codemodel.injection`, Jakarta Inject compatible).

**Stack:** Java 25, Maven multi-module, Eclipse Aether (artifact resolution), Undertow (HTTP), GraphQL Java, ASM, FreeMarker, JUnit 6, Mockito 5.

**Structure:**
- `spin-api/` — all interfaces and annotations (the public contract)
- `spin-common/` — `DefaultProgram` (program inference + execution), `DefaultInvocable`
- `spin-engine/` — `DefaultEngine` (ServiceLoader discovery, workspace/project tree)
- `spin/` — `Spin.main()` CLI entry point; also self-builds via jlink
- `spin-modules/` — pluggable extension modules (java, maven, junit, git, config, etc.)
- `spin-testing/` — `WorkspaceDiscovery` JUnit 5 extension for integration tests
- `spin-collider/` — subprocess launcher for integration tests requiring a running server

For detailed architecture, see [docs/CODEBASE_MAP.md](docs/CODEBASE_MAP.md).

## Code Conventions (from coding-conventions.md)

- Google Java Style; enforced by Checkstyle
- No nulls — use `Optional`/`Stream`
- No logging framework — use `TelemetryRecorder`
- No static state except constants
- Getters: `age()` not `getAge()`; `Stream` returns have no `get` prefix
- Interface names: no `I` prefix; implementation classes: no `Impl` suffix; use `Default` prefix
- Constructors: private or package-private; use Builder or static factory
- DI via `build.codemodel.injection` everywhere

## Key Gotchas

- Tasks must be `public static` non-abstract inner classes of their Plugin to be discovered via reflection
- `version.properties` is not yet used by the engine (marked "coming soon")
