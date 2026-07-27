package build.spin.module.java.tests;

import build.base.assertion.Eventually;
import build.base.configuration.Option;
import build.base.flow.CompletingSubscriber;
import build.base.flow.RecordingSubscriber;
import build.base.foundation.Strings;
import build.base.foundation.stream.Streams;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
import build.base.telemetry.Error;
import build.base.telemetry.Telemetry;
import build.base.version.Version;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.percolate.core.ModuleGraphClassifier;
import build.spawn.application.Application;
import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.application.option.Name;
import build.spawn.application.option.StandardOutputSubscriber;
import build.spawn.platform.local.LocalMachine;
import build.spin.AssetCache;
import build.spin.Engine;
import build.spin.Program;
import build.spin.ProgramExecutionException;
import build.spin.Project;
import build.spin.Task;
import build.spin.Workspace;
import build.spin.common.DefaultAssetCache;
import build.spin.common.ProcessFailedException;
import build.spin.module.checkstyle.CheckstylePlugin;
import build.spin.module.clean.CleanPlugin;
import build.spin.module.java.Java25CompilerPlugin;
import build.spin.module.java.Java8CompilerPlugin;
import build.spin.module.java.JavaPlatform;
import build.spin.module.junit.Java25JUnitPlugin;
import build.spin.module.junit.Java8JUnitPlugin;
import build.spin.module.maven.MavenRepository;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.DefaultModuleCatalog;
import build.spin.module.modulesystem.DefaultModuleVersioning;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.module.modulesystem.PomBasedModuleCatalog;
import build.spin.module.modulesystem.PomBasedModuleVersioning;
import build.spin.module.modulesystem.PomBasedTestModuleDescriptor;
import build.spin.module.modulesystem.TestModuleDescriptor;
import build.spin.testing.RequireJavaVersion;
import build.spin.testing.WorkspaceDiscovery;
import build.spin.testing.WorkspacePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for Java-based Spin-based {@link Project}s.
 * 
 * @author brian.oliver
 * @since Jun-2020
 */
@ExtendWith(WorkspaceDiscovery.class)
public class JavaProjectTests {

    private static <T extends Throwable> Optional<T> causeOfType(final Throwable t, final Class<T> type) {
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
        }
        return Optional.empty();
    }

    @Test
    @WorkspacePath("java-8")
    void shouldDetectJava8Project(final Engine engine, final Workspace workspace) {
        assertThat(engine.options().get(JDKVersion.class).major()).isEqualTo(8);
        assertThat(workspace.name()).isEqualTo("java-8");
        assertThat(workspace.getPlugin(Java8CompilerPlugin.class).isPresent()).isTrue();
    }

    @Test
    @WorkspacePath("java-25")
    void shouldDetectJava25Project(final Engine engine, final Workspace workspace) {
        assertThat(engine.options().get(JDKVersion.class).major()).isEqualTo(25);
        assertThat(workspace.name()).isEqualTo("java-25");
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class).isPresent()).isTrue();
    }

    @Test
    @WorkspacePath("javadoc-config")
    void shouldApplyNamedConfigurationToJavadocArguments(final Engine engine, final Workspace workspace)
        throws Exception {

        // create a Program to generate javadoc for the Workspace
        final Program program = engine.createProgram(workspace, Task.Pattern.of("javadoc"));

        // create a Task ExecutionCache for the Program
        final AssetCache cache = DefaultAssetCache.create();

        program.execute(cache);

        // locate the generated "arguments-javadoc-*" file, containing the arguments passed to javadoc
        final Path buildPath = workspace.path().resolve(".build");
        final Path argumentsFile;
        try (var files = Files.list(buildPath)) {
            argumentsFile = files
                .filter(path -> path.getFileName().toString().startsWith("arguments-javadoc-"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "Expected an arguments-javadoc-* file in [" + buildPath + "]"));
        }

        final String contents = Files.readString(argumentsFile);

        // the .spin/build.spin.module.javadoc.properties fixture sets author, version and nodeprecated to true
        assertThat(contents).contains("-author");
        assertThat(contents).contains("-version");
        assertThat(contents).contains("-nodeprecated");

        // ... and sets enable-preview to false, overriding the default (enabled) behavior
        assertThat(contents).doesNotContain("--enable-preview");
    }

    @Test
    @WorkspacePath("no-config")
    void shouldDiscoverJavaWorkspaceWithoutSpinConfig(final Workspace workspace) {
        // Reaching this assertion means workspace discovery completed without
        // throwing UnsatisfiedDependencyException when MavenPlugin (activated via
        // the Java25CompilerPlugin from src/main/java) tried to inject a ModuleCatalog.
        // On a workspace without module-catalog.properties, DefaultModuleCatalog and
        // DefaultModuleVersioning are the only resources that satisfy those injections.
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class)).isPresent();
        assertThat(workspace.resources().filter(ModuleCatalog.class::isInstance).findFirst())
            .get().isInstanceOf(DefaultModuleCatalog.class);
        assertThat(workspace.resources().filter(ModuleVersioning.class::isInstance).findFirst())
            .get().isInstanceOf(DefaultModuleVersioning.class);
    }

    @Test
    @WorkspacePath("pom-based")
    void shouldDiscoverMavenWorkspaceWithoutSpinConfig(final Workspace workspace) {
        // A workspace with a pom.xml but no spin config files should resolve
        // ModuleCatalog/ModuleVersioning to the PomBased* variants (not Default*,
        // whose detection deliberately excludes pom.xml workspaces to leave room
        // for this case).
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class)).isPresent();
        assertThat(workspace.resources().filter(ModuleCatalog.class::isInstance).findFirst())
            .get().isInstanceOf(PomBasedModuleCatalog.class);
        assertThat(workspace.resources().filter(ModuleVersioning.class::isInstance).findFirst())
            .get().isInstanceOf(PomBasedModuleVersioning.class);
    }

    @Test
    @WorkspacePath("pom-based")
    void shouldProvidePomBasedTestModuleDescriptorForMavenWorkspace(final Workspace workspace) {
        // PomBasedTestModuleDescriptor should activate on the same pom.xml-only
        // workspace and expose the test-scoped deps (junit-jupiter-api) from the
        // fixture's pom.xml as requires on the test module descriptor.
        final TestModuleDescriptor testDescriptor = workspace.resources()
            .filter(TestModuleDescriptor.class::isInstance)
            .map(TestModuleDescriptor.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Expected a TestModuleDescriptor resource in workspace [" + workspace.path() + "]"));

        assertThat(testDescriptor).isInstanceOf(PomBasedTestModuleDescriptor.class);

        final JDKModuleDescriptor descriptor = testDescriptor.get(workspace);
        assertThat(descriptor.requiresClauses().map(r -> r.requiresModuleName().toString()))
            .contains("org.junit.jupiter", "junit.jupiter.api");
    }

    @Test
    @WorkspacePath("multi-release")
    @RequireJavaVersion("8")
    void shouldDetectMultipleJavaPlugins(final Engine engine, final Workspace workspace)
        throws Exception {

        // ensure the default Java Version is 8
        assertThat(engine.options().get(JDKVersion.class).major()).isEqualTo(8);

        assertThat(workspace.name()).isEqualTo("multi-release");

        assertThat(workspace.getPlugin(Java8CompilerPlugin.class).isPresent()).isTrue();
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class).isPresent()).isTrue();

        // TODO: assert that Java25Plugin.Compile requires Java8Plugin.Compile

        assertThat(workspace.getPlugin(Java8JUnitPlugin.class).isPresent()).isTrue();
        assertThat(workspace.getPlugin(Java25JUnitPlugin.class).isPresent()).isTrue();

        // TODO: assert that Java25JUnitPlugin.Compile requires Java8JUnitPlugin.Compile

        // create a Program to compile the Workspace
        final Program program = engine.createProgram(workspace, Task.Pattern.of("compile"));

        // create a Task ExecutionCache for the Program
        final AssetCache cache = DefaultAssetCache.create();

        program.execute(cache);
    }

    @Test
    @WorkspacePath("broken-25")
    void shouldCaptureCompilerErrors(final Engine engine, final Workspace workspace) {

        assertThat(engine.options().get(JDKVersion.class).major()).isEqualTo(25);
        assertThat(workspace.name()).isEqualTo("broken-25");
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class).isPresent()).isTrue();

        // create a Program to compile the Workspace
        final Program program = engine.createProgram(workspace, Task.Pattern.of("compile"));

        // create a Task ExecutionCache for the Program
        final AssetCache cache = DefaultAssetCache.create();

        // establish a CompletingObserver to observe the compilation failure
        final CompletingSubscriber<Telemetry> observer = new CompletingSubscriber<>();
        final CompletableFuture<Telemetry> future = observer.when(t -> t instanceof Error);
        engine.subscribe(observer);

        try {
            program.execute(cache);

            fail("The compilation of the source file should have failed");
        }
        catch (final ProgramExecutionException e) {
            Eventually.assertThat(future).isCompleted();

            // all task failures: thrown + suppressed
            final var allFailures = Stream.concat(Stream.of(e), Arrays.stream(e.getSuppressed()))
                .toList();

            // extractOutput embeds stderr into the ProgramExecutionException message — verify it surfaced
            final var compileTaskFailure = allFailures.stream()
                .filter(t -> t.getMessage().contains("Broken.java") && t.getMessage().contains("error:"))
                .findFirst();

            assertThat(compileTaskFailure)
                .as("expected javac errors (Broken.java, error:) to be embedded in a task failure message")
                .isPresent();

            // the ProcessFailedException in the cause chain should carry the raw output
            final var pfe = causeOfType(compileTaskFailure.get(), ProcessFailedException.class);
            assertThat(pfe)
                .as("expected a ProcessFailedException in the cause chain of the compile failure")
                .isPresent();
            assertThat(pfe.get().output()).contains("Broken.java").contains("error:");
        }
    }

    @Test
    @WorkspacePath("checkstyle-violation")
    void shouldCaptureCheckstyleViolations(final Engine engine, final Workspace workspace) {

        assertThat(workspace.name()).isEqualTo("checkstyle-violation");
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class).isPresent()).isTrue();
        assertThat(workspace.getPlugin(CheckstylePlugin.class).isPresent()).isTrue();

        // create a Program to run Checkstyle on the Workspace
        final Program program = engine.createProgram(workspace, Task.Pattern.of("checkstyle"));

        // create a Task ExecutionCache for the Program
        final AssetCache cache = DefaultAssetCache.create();

        // establish a CompletingObserver to observe the checkstyle failure
        final CompletingSubscriber<Telemetry> observer = new CompletingSubscriber<>();
        final CompletableFuture<Telemetry> future = observer.when(t -> t instanceof Error);
        engine.subscribe(observer);

        try {
            program.execute(cache);

            fail("The Checkstyle check of the source file should have failed");
        }
        catch (final ProgramExecutionException e) {
            Eventually.assertThat(future).isCompleted();

            // all task failures: thrown + suppressed
            final var allFailures = Stream.concat(Stream.of(e), Arrays.stream(e.getSuppressed()))
                .toList();

            // extractOutput embeds the captured checkstyle output into the ProgramExecutionException
            // message — verify it surfaced
            final var checkstyleTaskFailure = allFailures.stream()
                .filter(t -> t.getMessage().contains("HasUnusedImport.java")
                    && t.getMessage().contains("Unused import"))
                .findFirst();

            assertThat(checkstyleTaskFailure)
                .as("expected a Checkstyle violation (HasUnusedImport.java, Unused import) to be "
                    + "embedded in a task failure message")
                .isPresent();

            // the ProcessFailedException in the cause chain should carry the raw output
            final var pfe = causeOfType(checkstyleTaskFailure.get(), ProcessFailedException.class);
            assertThat(pfe)
                .as("expected a ProcessFailedException in the cause chain of the checkstyle failure")
                .isPresent();
            assertThat(pfe.get().output()).contains("HasUnusedImport.java").contains("Unused import");
        }
    }

    @Test
    @WorkspacePath("jlink-tainted")
    void shouldDumpCdsBaseArchiveAlongsideTaintedRootModule(final Engine engine, final Workspace workspace)
        throws Exception {

        // create a Program to build a jlink runtime image for the Workspace
        final Program program = engine.createProgram(workspace, Task.Pattern.of("jlink"));

        // create a Task ExecutionCache for the Program
        final AssetCache cache = DefaultAssetCache.create();

        program.execute(cache);

        // jlink() builds one runtime image per staged target platform (including foreign targets,
        // for which dumpBaseCdsArchive is deliberately skipped) — locate the host's own image, since
        // that's the only one dumpBaseCdsArchive actually runs against
        final Path buildPath = workspace.path().resolve(".build");
        final Path packagePath = buildPath.resolve(workspace.name() + "-" + JavaPlatform.hostTarget());
        assertThat(packagePath).as("expected a host-target runtime image at [" + packagePath + "]").isDirectory();

        // the fixture's own root module requires javax.inject, a real automatic module (no
        // module-info.class) — jlink cannot link that into lib/modules, so it must have been left
        // on an external runtime --module-path instead of silently dropped
        final Path modulePath = packagePath.resolve("modules");
        assertThat(modulePath).isDirectory();
        try (var modules = Files.list(modulePath)) {
            assertThat(modules.anyMatch(p -> p.getFileName().toString().startsWith("javax.inject")))
                .as("expected the tainted javax.inject jar under [" + modulePath + "]")
                .isTrue();
        }

        // dumpBaseCdsArchive must have been able to resolve "-m app/app.Main" against that external
        // module-path (rather than failing with a module-not-found error) and produced a base archive
        assertThat(packagePath.resolve("lib/server/classes.jsa")).exists();

        // the generated launch script should point AutoCreateSharedArchive at app.jsa alongside it
        final Path script = packagePath.resolve("bin/jlink-tainted.sh");
        assertThat(script).exists();
        assertThat(Files.readString(script)).contains("-XX:+AutoCreateSharedArchive");
    }

    @Test
    @WorkspacePath("jdeps")
    void getEarliestShouldReturnPresentOptional(final JavaPlatform platform) {
        assertThat(platform.getEarliest().isPresent()).as("getEarliest() should return a JDK but no JDKs were discovered").isTrue();
    }

    @Test
    @WorkspacePath("jdeps")
    void getLatestShouldReturnPresentOptional(final JavaPlatform platform) {
        assertThat(platform.getLatest().isPresent()).as("getLatest() should return a JDK but no JDKs were discovered").isTrue();
    }

    @Test
    @WorkspacePath("jdeps")
    void earliestVersionShouldBeLessThanOrEqualToLatestVersion(final JavaPlatform platform) {
        var earliest = platform.getEarliest().orElseThrow(() -> new AssertionError("getEarliest() returned empty"));
        var latest = platform.getLatest().orElseThrow(() -> new AssertionError("getLatest() returned empty"));
        assertThat(earliest.version().compareTo(latest.version()) <= 0)
            .as("expected getEarliest() [" + earliest.version().get() + "] <= getLatest() [" + latest.version().get() + "]")
            .isTrue();
    }

    /**
     * Attempt to locate and execute jdeps from the {@link JavaPlatform} using the {@link LocalMachine}.
     *
     * @param platform the {@link JavaPlatform}
     * @param machine the {@link LocalMachine}
     * @param repository the {@link MavenRepository}
     */
    @Test
    @WorkspacePath("jdeps")
    void shouldRunJDeps(final JavaPlatform platform,
                        final LocalMachine machine,
                        final MavenRepository repository,
                        final Workspace workspace)
        throws Exception {

        // TODO: we should be able to inject these?
        var catalog = workspace.resources()
            .filter(ModuleCatalog.class::isInstance)
            .map(ModuleCatalog.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected a ModuleCatalog resource in workspace [" + workspace.path() + "]"));

        var versioning = workspace.resources()
            .filter(ModuleVersioning.class::isInstance)
            .map(ModuleVersioning.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected a ModuleVersioning resource in workspace [" + workspace.path() + "]"));

        // -----
        // obtain the non-Java Platform artifacts transitively
        // (so we can put them on a classpath/modulepath for analysis)
        final var pending = new Stack<ModuleReference>();
        final var processed = new LinkedHashSet<ModuleReference>();

        final var artifactModuleDescriptors = new LinkedHashMap<Artifact, JDKModuleDescriptor>();
        final var artifactPaths = new LinkedHashMap<Artifact, Path>();

        var initial = ModuleReference.of("build.spawn.platform.local",
            Version.parse("0.1.0"));

        pending.push(initial);

        while (!pending.isEmpty()) {
            var reference = pending.pop();

            if (!processed.contains(reference)) {

                // obtain the artifact for the module reference
                var artifact = catalog.getArtifact(reference).orElseThrow(() -> new AssertionError("No artifact in catalog for module reference [" + reference + "]"));

                // resolve the path to the artifact
                if (!artifactPaths.containsKey(artifact)) {
                    var path = repository.resolve(artifact).orElseThrow(() -> new AssertionError("Failed to resolve path for artifact [" + artifact + "]"));
                    artifactPaths.put(artifact, path);
                }

                // obtain the ModuleDescriptor
                if (!artifactModuleDescriptors.containsKey(artifact)) {
                    var moduleDescriptor = repository.getModuleDescriptor(artifact, catalog, versioning).orElseThrow(() -> new AssertionError("Failed to get ModuleDescriptor for artifact [" + artifact + "]"));
                    artifactModuleDescriptors.put(artifact, moduleDescriptor);

                    processed.add(reference);

                    // push the non-Java Platform required modules onto the stack for processing
                    moduleDescriptor.requiresClauses()
                        .filter(r -> !JavaPlatform.isJavaPlatformModule(r.requiresModuleName().toString()))
                        .map(r -> ModuleReference.of(r.requiresModuleName().toString(),
                            JDKModuleDescriptor.requiresVersion(r)))
                        .filter(module -> !processed.contains(module))
                        .forEach(pending::push);
                }
            }
        }

        // -----

        // copy the modules into a module path that can be used
        var modulePath = workspace.path().resolve(".build/modules/");
        CleanPlugin.delete(modulePath);

        // create the module path
        Files.createDirectories(modulePath);

        // classify and de-conflict: named modules go to module-path; demoted/superseded go to classpath.
        // Same-module/different-version jars share identical package sets, so resolveConflicts's own
        // version-dedupe tier (grouping by base name, keeping the newest) already avoids the "two
        // versions of module X found" jdeps error without a separate hand-rolled dedupe pass here.
        final var allPaths = artifactPaths.values().stream().toList();
        final var resolution = ModuleGraphClassifier.resolveConflicts(allPaths, java.util.Set.of(), msg -> {});

        // a jar goes on the module path — intentionally named-only here because jdeps does not
        // resolve modules by filename-derived names the way javac does; plain unnamed jars on a
        // jdeps --module-path cause "module not found" errors at analysis time rather than silently
        // becoming automatic modules. ModuleGraphClassifier.classify promotes all non-conflicting
        // jars (including filename-derived automatics) for javac. Everything else — unnamed,
        // superseded, or demoted — belongs on the classpath instead, never both, so the same jar
        // never appears at two different paths and trips jdeps's split-package detection.
        final Predicate<Path> onModulePath = source -> ModuleGraphClassifier.isNamedModule(source)
            && !resolution.superseded().contains(source) && !resolution.demoted().contains(source);

        allPaths.stream()
            .filter(onModulePath)
            .forEach(source -> {
                try {
                    Files.copy(source, modulePath.resolve(source.getFileName()));
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        // -----

        // obtain the top-level dependency to analyze
        var dependencyPath = artifactPaths.values().stream().findFirst().orElseThrow(() -> new AssertionError("Expected at least one resolved artifact path but artifactPaths was empty"));

        // build the classpath PathSet (containing only the jars that didn't go on the module path)
        final var classPathBuilder = PathSetBuilder.create();
        allPaths.stream().filter(onModulePath.negate()).forEach(classPathBuilder::add);
        var classPathSet = classPathBuilder.build();
        var classPath = Streams.reverse(classPathSet.stream())
            .map(Path::toString)
            .map(Strings::doubleQuoteIfContainsWhiteSpace)
            .reduce("", (left, right) -> left.isEmpty() ? right : left + File.pathSeparator + right);

        // build the list of module names to be processed
        var moduleNames = processed.stream()
            .map(ModuleReference::name)
            .reduce("", (left, right) -> left.isEmpty() ? right : left + "," + right);

        var jdk = platform.getVersion(25).orElseThrow(() -> {
            final var jdks = platform.stream().toList();
            return new RuntimeException("Failed to obtain Java Development Kit for Java 25. Available Java Development Kits: " + jdks);
        });
        var javaHome = jdk.home().path();

        var jdepsPath = javaHome.resolve("bin/jdeps");

        var recordingObserver = new RecordingSubscriber<String>();
        var stdoutObserver = StandardOutputSubscriber.of(recordingObserver);

        // --class-path is omitted entirely when empty (e.g. every dependency landed on the
        // module path) — jdeps errors with "no value given for --class-path" on an empty value
        final List<Option> jdepsOptions = new ArrayList<>();
        jdepsOptions.add(Executable.of(jdepsPath.toString()));
        jdepsOptions.add(Name.of("jdeps"));
        jdepsOptions.add(Argument.of("--module-path"));
        jdepsOptions.add(Argument.of(modulePath));
        if (!classPath.isEmpty()) {
            jdepsOptions.add(Argument.of("--class-path"));
            jdepsOptions.add(Argument.of(classPath));
        }
        jdepsOptions.add(Argument.of("--list-deps"));
        jdepsOptions.add(Argument.of("--ignore-missing-deps"));
        jdepsOptions.add(Argument.of("--multi-release"));
        jdepsOptions.add(Argument.of(jdk.version().major()));
        jdepsOptions.add(Argument.of("--module"));
        jdepsOptions.add(Argument.of(initial.name()));
        jdepsOptions.add(Console.ofSystem());
        jdepsOptions.add(stdoutObserver);

        try (var jdeps = machine.launch(Application.class, jdepsOptions.toArray(Option[]::new))) {

            Eventually.assertThat(jdeps.onExit()).isCompleted();
            assertThat(jdeps.exitValue().orElseThrow(() -> new AssertionError("jdeps process has no exit value — it may not have terminated"))).isEqualTo(0);

            // build maps of java platform, module and non-module dependencies
            final LinkedHashSet<ModuleReference> javaPlatformModules = new LinkedHashSet<>();
            final LinkedHashSet<JDKModuleDescriptor> modules = new LinkedHashSet<>();
            final LinkedHashSet<JDKModuleDescriptor> nonModules = new LinkedHashSet<>();
            final LinkedHashMap<JDKModuleDescriptor, Artifact> artifacts = new LinkedHashMap<>();
            final LinkedHashSet<String> unknownModules = new LinkedHashSet<>();

            // a Consumer of Artifacts together with their ModuleDescriptors
            // (to collect and categorize the ModuleDescriptors)
            final Consumer<Map.Entry<Artifact, JDKModuleDescriptor>> consumeArtifact = entry -> {
                final Artifact artifact = entry.getKey();
                final JDKModuleDescriptor descriptor = entry.getValue();

                artifacts.put(descriptor, artifact);

                if (!descriptor.isAutomatic()) {
                    modules.add(descriptor);
                }
                else {
                    nonModules.add(descriptor);
                }
            };

            // determine a Version for the Java Development Kit Modules
            final Version jdkVersion = Version.parse(jdk.version().get());

            recordingObserver.items()
                .map(String::trim)
                .filter(line -> !line.contains(" "))
                .forEach(moduleName -> {
                    if (JavaPlatform.isJavaPlatformModule(moduleName)) {
                        final ModuleReference reference = ModuleReference.of(moduleName, jdkVersion);
                        javaPlatformModules.add(reference);
                    }
                    else {
                        artifactModuleDescriptors.entrySet().stream()
                            .filter(entry -> entry.getValue().moduleName().toString().equals(moduleName))
                            .findFirst()
                            .ifPresentOrElse(consumeArtifact, () -> unknownModules.add(moduleName));
                    }
                });

            // ensure all artifacts provided are consumed as either modules or non-modules
            artifactModuleDescriptors.entrySet().stream()
                .filter(entry -> !modules.contains(entry.getValue()) && !nonModules.contains(entry.getValue()))
                .forEach(consumeArtifact);

            System.out.printf("Java Dependencies Analysis for: %s\n\n", initial);

            System.out.printf("Java Platform Modules: @ %s\n", jdk.version().get());
            javaPlatformModules.stream()
                .forEach(reference -> System.out.printf("  %s\n", reference.name()));

            if (!modules.isEmpty()) {
                System.out.println("\nModules: ");
                modules.stream()
                    .forEach(descriptor -> System.out.printf("  %s @ %s (%s) [%s]\n",
                        descriptor.moduleName().toString(),
                        descriptor.version().map(Version::toString).orElse("(unknown version)"),
                        descriptor.isAutomatic() ? "automatic module" : "fully-blown module",
                        artifactPaths.get(artifacts.get(descriptor))));
            }

            if (!nonModules.isEmpty()) {
                System.out.println("\nNon-Modules: (for classpath)");
                nonModules.stream()
                    .forEach(descriptor -> System.out.printf("  %s @ %s [%s]\n",
                        descriptor.moduleName().toString(),
                        descriptor.version().map(Version::toString).orElse("(unknown version)"),
                        artifactPaths.get(artifacts.get(descriptor))));
            }

            if (!unknownModules.isEmpty()) {
                System.out.println("\nUnknown Modules: ");
                unknownModules.stream()
                    .forEach(name -> System.out.printf("  %s\n", name));
            }
        }
    }
}
