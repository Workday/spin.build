package build.spin.module.java.tests;

import build.base.assertion.Eventually;
import build.base.flow.CompletingSubscriber;
import build.base.flow.RecordingSubscriber;
import build.base.foundation.Strings;
import build.base.foundation.stream.Streams;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
import build.base.telemetry.Error;
import build.base.telemetry.Telemetry;
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
import build.spin.module.clean.CleanPlugin;
import build.spin.module.java.Java25CompilerPlugin;
import build.spin.module.java.Java8CompilerPlugin;
import build.spin.module.java.JavaPlatform;
import build.spin.module.junit.Java25JUnitPlugin;
import build.spin.module.junit.Java8JUnitPlugin;
import build.spin.module.maven.MavenRepository;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.testing.RequireJavaVersion;
import build.spin.testing.WorkspaceDiscovery;
import build.spin.testing.WorkspacePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for Java-based Spin-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Jun-2020
 */
@ExtendWith(WorkspaceDiscovery.class)
public class JavaProjectTests {

    @Test
    @WorkspacePath("java-8")
    void shouldDetectJava8Project(final Engine engine, final Workspace workspace) {
        assertThat(engine.options().get(JDKVersion.class).major(), is(8));
        assertThat(workspace.name(), is("java-8"));
        assertThat(workspace.getPlugin(Java8CompilerPlugin.class).isPresent(), is(true));
    }

    @Test
    @WorkspacePath("java-25")
    void shouldDetectJava25Project(final Engine engine, final Workspace workspace) {
        assertThat(engine.options().get(JDKVersion.class).major(), is(25));
        assertThat(workspace.name(), is("java-25"));
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class).isPresent(), is(true));
    }

    @Test
    @WorkspacePath("multi-release")
    @RequireJavaVersion("8")
    void shouldDetectMultipleJavaPlugins(final Engine engine, final Workspace workspace)
        throws Exception {

        // ensure the default Java Version is 8
        assertThat(engine.options().get(JDKVersion.class).major(), is(8));

        assertThat(workspace.name(), is("multi-release"));

        assertThat(workspace.getPlugin(Java8CompilerPlugin.class).isPresent(), is(true));
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class).isPresent(), is(true));

        // TODO: assert that Java25Plugin.Compile requires Java8Plugin.Compile

        assertThat(workspace.getPlugin(Java8JUnitPlugin.class).isPresent(), is(true));
        assertThat(workspace.getPlugin(Java25JUnitPlugin.class).isPresent(), is(true));

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

        assertThat(engine.options().get(JDKVersion.class).major(), is(25));
        assertThat(workspace.name(), is("broken-25"));
        assertThat(workspace.getPlugin(Java25CompilerPlugin.class).isPresent(), is(true));

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
        }
    }

    @Test
    @WorkspacePath("jdeps")
    void getEarliestShouldReturnPresentOptional(final JavaPlatform platform) {
        assertThat("getEarliest() should return a JDK but no JDKs were discovered", platform.getEarliest().isPresent(), is(true));
    }

    @Test
    @WorkspacePath("jdeps")
    void getLatestShouldReturnPresentOptional(final JavaPlatform platform) {
        assertThat("getLatest() should return a JDK but no JDKs were discovered", platform.getLatest().isPresent(), is(true));
    }

    @Test
    @WorkspacePath("jdeps")
    void earliestVersionShouldBeLessThanOrEqualToLatestVersion(final JavaPlatform platform) {
        var earliest = platform.getEarliest().orElseThrow(() -> new AssertionError("getEarliest() returned empty"));
        var latest = platform.getLatest().orElseThrow(() -> new AssertionError("getLatest() returned empty"));
        assertThat("expected getEarliest() [" + earliest.version().get() + "] <= getLatest() [" + latest.version().get() + "]",
            earliest.version().compareTo(latest.version()) <= 0, is(true));
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

        final var artifactModuleDescriptors = new LinkedHashMap<Artifact, ModuleDescriptor>();
        final var artifactPaths = new LinkedHashMap<Artifact, Path>();
        final var classPathBuilder = PathSetBuilder.create();

        var initial = ModuleReference.of("build.spawn.platform.local",
            ModuleDescriptor.Version.parse("0.1.0-SNAPSHOT"));

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
                    classPathBuilder.add(path);
                }

                // obtain the ModuleDescriptor
                if (!artifactModuleDescriptors.containsKey(artifact)) {
                    var moduleDescriptor = repository.getModuleDescriptor(artifact, catalog, versioning).orElseThrow(() -> new AssertionError("Failed to get ModuleDescriptor for artifact [" + artifact + "]"));
                    artifactModuleDescriptors.put(artifact, moduleDescriptor);

                    processed.add(reference);

                    // push the non-Java Platform required modules onto the stack for processing
                    moduleDescriptor.requires()
                        .filter(requires -> !JavaPlatform.isJavaPlatformModule(requires.name()))
                        .map(ModuleDescriptor.Requires::reference)
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

        // modules that cause split-package conflicts when placed on the module-path
        final var excludeFromModulePath = java.util.Set.of("failureaccess", "j2objc.annotations", "slf4j.api", "jboss.logmanager");

        // deduplicate by module name, keeping the highest version (avoids "two versions of module X found" jdeps errors)
        final var deduplicated = new LinkedHashMap<String, Map.Entry<Artifact, Path>>();
        artifactPaths.entrySet().stream()
            .filter(entry -> {
                var descriptor = artifactModuleDescriptors.get(entry.getKey());
                return descriptor == null || !excludeFromModulePath.contains(descriptor.name());
            })
            .forEach(entry -> {
                var descriptor = artifactModuleDescriptors.get(entry.getKey());
                var moduleName = descriptor != null ? descriptor.name() : entry.getKey().artifactId();
                var existing = deduplicated.get(moduleName);
                if (existing == null) {
                    deduplicated.put(moduleName, entry);
                } else {
                    var existingVersion = ModuleDescriptor.Version.parse(existing.getKey().version().get());
                    var newVersion = ModuleDescriptor.Version.parse(entry.getKey().version().get());
                    if (newVersion.compareTo(existingVersion) > 0) {
                        deduplicated.put(moduleName, entry);
                    }
                }
            });

        // copy the artifacts into the modules path
        deduplicated.values().stream()
            .map(Map.Entry::getValue)
            .forEach(source -> {
                try {
                    var target = modulePath.resolve(source.getFileName());
                    Files.copy(source, target);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        // -----

        // obtain the top-level dependency to analyze
        var dependencyPath = artifactPaths.values().stream().findFirst().orElseThrow(() -> new AssertionError("Expected at least one resolved artifact path but artifactPaths was empty"));

        // build the classpath PathSet (containing the dependencies)
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

        try (var jdeps = machine.launch(Application.class,
            Executable.of(jdepsPath.toString()),
            Name.of("jdeps"),
            Argument.of("--module-path"), Argument.of(modulePath),
            Argument.of("--class-path"), Argument.of(classPath),
            Argument.of("--list-deps"),
            Argument.of("--ignore-missing-deps"),
            Argument.of("--multi-release"), Argument.of(jdk.version().major()),
            //            Argument.of("--recursive"),
            //            Argument.of("--add-modules"), Argument.of(moduleNames),
            //            Argument.of(dependencyPath),
            Argument.of("--module"), Argument.of(initial.name()),
            Console.ofSystem(),
            stdoutObserver)) {

            Eventually.assertThat(jdeps.onExit()).isCompleted();
            assertThat(jdeps.exitValue().orElseThrow(() -> new AssertionError("jdeps process has no exit value — it may not have terminated")), is(0));

            // build maps of java platform, module and non-module dependencies
            final LinkedHashSet<ModuleReference> javaPlatformModules = new LinkedHashSet<>();
            final LinkedHashSet<ModuleDescriptor> modules = new LinkedHashSet<>();
            final LinkedHashSet<ModuleDescriptor> nonModules = new LinkedHashSet<>();
            final LinkedHashMap<ModuleDescriptor, Artifact> artifacts = new LinkedHashMap<>();
            final LinkedHashSet<String> unknownModules = new LinkedHashSet<>();

            // a Consumer of Artifacts together with their ModuleDescriptors
            // (to collect and categorize the ModuleDescriptors)
            final Consumer<Map.Entry<Artifact, ModuleDescriptor>> consumeArtifact = entry -> {
                final Artifact artifact = entry.getKey();
                final ModuleDescriptor descriptor = entry.getValue();

                artifacts.put(descriptor, artifact);

                if (descriptor.location().isPresent()) {
                    modules.add(descriptor);
                }
                else {
                    nonModules.add(descriptor);
                }
            };

            // determine a ModuleDescriptor.Version for the Java Development Kit Modules
            final ModuleDescriptor.Version jdkVersion = ModuleDescriptor.Version.parse(jdk.version().get());

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
                            .filter(entry -> entry.getValue().name().equals(moduleName))
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
                        descriptor.name(),
                        descriptor.version().map(ModuleDescriptor.Version::get).orElse("(unknown version)"),
                        descriptor.isAutomatic() ? "automatic module" : "fully-blown module",
                        artifactPaths.get(artifacts.get(descriptor))));
            }

            if (!nonModules.isEmpty()) {
                System.out.println("\nNon-Modules: (for classpath)");
                nonModules.stream()
                    .forEach(descriptor -> System.out.printf("  %s @ %s [%s]\n",
                        descriptor.name(),
                        descriptor.version().map(ModuleDescriptor.Version::get).orElse("(unknown version)"),
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
