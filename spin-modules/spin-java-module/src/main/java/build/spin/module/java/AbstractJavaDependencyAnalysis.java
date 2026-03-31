package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import build.base.flow.RecordingSubscriber;
import build.base.foundation.Capture;
import build.base.foundation.Strings;
import build.base.foundation.stream.Streams;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
import build.base.table.Table;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.Application;
import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.application.option.Name;
import build.spawn.application.option.StandardOutputSubscriber;
import build.spawn.platform.local.LocalMachine;
import build.spin.Invocable;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.Workspace;
import build.spin.annotation.System;
import build.spin.module.clean.CleanPlugin;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ArtifactDescriptor;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
/**
 * An abstract {@link Task} to perform Java Dependency Analysis using the Java Platform
 * <a href="https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html">jdeps</a> tool
 * on the compiled and packaged {@link Artifact} for a {@link Project}.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public abstract class AbstractJavaDependencyAnalysis
    implements Task<DependencyAnalysis> {

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private JavaPlatform platform;

    @Inject
    private LocalMachine machine;

    @Inject
    private Workspace workspace;

    @Inject
    private ModuleDescriptor moduleDescriptor;

    @Inject
    private ModuleCatalog catalog;

    @Inject
    private ModuleVersioning versioning;

    @Inject
    @System
    private JDKVersion systemJavaVersion;

    @Inject
    private Artifact.Resolver artifactResolver;

    @Override
    public Stream<Reference> dependencies() {
        return this.moduleDescriptor.requires()
            .map(ModuleDescriptor.Requires::name)
            .flatMap(name -> this.workspace.stream()
                .map(project -> {
                    // capture the JavaCompilerPlugin in the Project with the same or lower JDKVersion used by
                    // this JavaPlugin (we can't be dependent on a JDKVersion higher than that required by this JavaPlugin)
                    final Capture<JavaCompilerPlugin> capture = Capture.empty();

                    project.plugins(JavaCompilerPlugin.class)
                        .forEach(plugin -> {
                            if ((capture.isPresent()
                                && plugin.getJavaVersion().compareTo(capture.get().getJavaVersion()) > 0)
                                || !capture.isPresent()) {

                                capture.set(plugin);
                            }
                        });

                    // TODO: if the module requires this module, we have a cycle!

                    // NOTE: using "endsWith" here is super important.
                    // (it allows project names to match module names for automatic modules)
                    return capture
                        .filter(plugin -> name.endsWith(plugin.getModuleDescriptor().name())
                            || project.name().equals(name))
                        .map(plugin ->
                            // locate the PackageModule tasks
                            project.invocables()
                                .filter(definition -> PackageModule.class.isAssignableFrom(definition.getTaskClass()))
                                .findFirst()
                                .map(Invocable::getTaskClass)
                                .map(taskClass -> Reference.of(project, taskClass))
                                .orElse(null))
                        .orElse(null);
                })
                .filter(Objects::nonNull));
    }

    /**
     * Perform {@code jdeps} dependency analysis on the packaged module.
     *
     * @param buildPath the build path for the {@link Project}
     * @param descriptors the {@link ArtifactDescriptor}s for {@link Project} dependencies in the {@link Workspace}
     *
     * @return the {@link DependencyAnalysis} on the packaged module
     * @throws Exception should the {@link Task} execution fail
     */
    public DependencyAnalysis jdeps(final Path buildPath,
                                    final Stream<ArtifactDescriptor> descriptors)
        throws Exception {

        final var jdk = this.platform.getVersion(this.systemJavaVersion.major())
            .orElseThrow(() -> {
                final var jdks = this.platform.stream().toList();
                return new RuntimeException("Failed to obtain Java Development Kit for Java " + this.systemJavaVersion.major() + ". Available Java Development Kits: " + jdks);
            });
        final var javaHome = jdk.home().path();

        // determine a ModuleDescriptor.Version for the Java Development Kit Modules
        final ModuleDescriptor.Version jdkVersion = ModuleDescriptor.Version.parse(jdk.version().get());

        // -----
        // obtain the non-Java Platform artifacts transitively, inside and outside the Workspace
        // (so we can put them on a classpath/modulepath for analysis)
        final var pending = new Stack<ModuleReference>();
        final var processed = new LinkedHashSet<ModuleReference>();
        final var ignored = new LinkedHashSet<ModuleReference>();

        final var platformModules = new LinkedHashSet<ModuleReference>();
        final var artifactDescriptors = new LinkedHashMap<Artifact, ArtifactDescriptor>();
        final var moduleDescriptors = new LinkedHashMap<ModuleReference, ModuleDescriptor>();
        final var requiredModules = new LinkedHashSet<String>();

        // establish a Predicate to filter Artifacts to include for analysis
        // TODO: this must be replaced with the versioning "include/exclude" module resource support
        final Predicate<Artifact> predicate = artifact ->
            artifact.type().equals("jar")
                && !artifact.artifactId().equals("log4j-api-java9");

        // initialize with ArtifactDescriptors using the dependency ArtifactDescriptors
        descriptors
            .filter(descriptor -> predicate.test(descriptor.artifact()))
            .forEach(descriptor -> artifactDescriptors.put(descriptor.artifact(), descriptor));

        // start with the ModuleDescriptor for this project
        pending.push(this.moduleDescriptor.reference());

        while (!pending.isEmpty()) {
            final var module = pending.pop();

            if (!processed.contains(module) && !ignored.contains(module)) {

                // ensure the ModuleReference has a Version with which we can resolve (when required)
                final ModuleReference reference = module.version().isPresent()
                    ? module
                    : ModuleReference.of(module.name(), this.versioning.getVersion(module.name()));

                // obtain the artifact for the module reference
                // (first try to find using the ArtifactDescriptors we've been provided with)
                final var artifact = artifactDescriptors.values().stream()
                    .filter(descriptor -> descriptor.reference().equals(reference))
                    .findFirst()
                    .map(ArtifactDescriptor::artifact)
                    .orElseGet(() -> this.catalog
                        .getArtifact(reference)
                        .orElseThrow(() -> new RuntimeException("Failed to determine Artifact for " + module)));

                // include the Artifact iff it satisfies the inclusion predicate
                if (predicate.test(artifact)) {

                    // resolve the ArtifactDescriptor for the Artifact
                    // (this will only happen for "external" Artifacts)
                    if (!artifactDescriptors.containsKey(artifact)) {
                        final var artifactDescriptor = ArtifactDescriptor.create(reference, artifact,
                            this.artifactResolver.resolve(artifact)
                                .orElseThrow(() -> new IllegalStateException("Failed to resolve artifact [" + artifact + "] for module [" + reference + "]")));

                        artifactDescriptors.put(artifact, artifactDescriptor);
                    }

                    // obtain the ArtifactDescriptor
                    final var artifactDescriptor = artifactDescriptors.get(artifact);
                    if (artifactDescriptor == null) {
                        throw new RuntimeException("Failed to determine ArtifactDescriptor for " + artifact);
                    }

                    // attempt to determine the ModuleDescriptor for the ArtifactDescriptor
                    // (first within the Workspace and if that fails, try to resolve it)
                    this.workspace.stream()
                        .filter(project -> project
                            .getPlugin(JavaCompilerPlugin.class)
                            .filter(plugin -> plugin.getModuleDescriptor().reference().equals(reference))
                            .isPresent())
                        .map(project -> project.getPlugin(JavaCompilerPlugin.class)
                            .map(JavaPlugin::getModuleDescriptor)
                            .orElseThrow(() -> new IllegalStateException("Expected JavaCompilerPlugin to have a ModuleDescriptor for module [" + reference + "] in project [" + project.name() + "]")))
                        .findFirst()
                        .or(() -> this.artifactResolver
                            .getModuleDescriptor(artifact, this.catalog, this.versioning)
                            .optional())
                        .map(moduleDescriptor -> {
                            moduleDescriptors.put(reference, moduleDescriptor);
                            processed.add(reference);

                            // TODO: correct the module reference if it's name is different!
                            // (eg: asm-7.2 has a different jar name but the same module name as asm-9.4!)

                            // TODO: introduce support for excludes
                            final var excludeModules = new HashSet<String>();
                            excludeModules.add("commons.logging");
                            // prefer slf4j 2.x (org.slf4j) over the 1.7.x automatic module name
                            excludeModules.add("slf4j.api");
                            // jboss-logmanager incorrectly exports org.wildfly.common.net (owned by wildfly-common)
                            excludeModules.add("jboss.logmanager");
                            // j2objc-annotations has two module names across versions
                            excludeModules.add("j2objc.annotations");
                            // failureaccess re-exports com.google.common.util.concurrent.internal (owned by guava)
                            excludeModules.add("failureaccess");

                            // push the non-Java Platform required modules onto the stack for processing
                            moduleDescriptor.requires()
                                .peek(requires -> {
                                    if (JavaPlatform.isJavaPlatformModule(requires.name())) {
                                        platformModules.add(ModuleReference.of(requires.name(), jdkVersion));
                                    }
                                })
                                .filter(requires -> !JavaPlatform.isJavaPlatformModule(requires.name()))
                                .map(ModuleDescriptor.Requires::reference)
                                .filter(r -> !processed.contains(r))
                                .filter(r -> !excludeModules.contains(r.name()))
                                // GraalVM modules are not available in standard JDKs; exclude the entire namespace
                                .filter(r -> !r.name().startsWith("org.graalvm."))
                                .peek(r -> {
                                    // we track required dependencies for explicit modules
                                    // (as these must be placed on the module path, regardless of whether they are modules or not)
                                    if (moduleDescriptor.location().isPresent()) {
                                        requiredModules.add(r.name());
                                    }
                                })
//                                .peek(r -> this.recorder.info("[jdeps] Module [%s] requires [%s] — queuing for catalog lookup", moduleDescriptor.name(), r))
                                .forEach(pending::push);

                            return reference;
                        })
                        .orElseGet(() -> {
                            // failed to obtain the ModuleDescriptor (so skipping it)
                            ignored.add(reference);
                            return reference;
                        });
                }
                else {
                    // we're ignoring the module, so mark that it is processed!
                    ignored.add(reference);
                }
            }
        }

        // -----
        // remove duplicate modules to optimize the ArtifactDescriptors for processing
        // (jdeps will fail when duplicate modules are discovered)
        // (use versioning to determine which modules to keep, and if there isn't one, use the highest)

        final var artifactDescriptorsByModuleName = new LinkedHashMap<String, ArtifactDescriptor>();

        artifactDescriptors.forEach((artifact, descriptor) -> {
            final var moduleName = descriptor.reference().name();
            final var existingDescriptor = artifactDescriptorsByModuleName.get(moduleName);

            if (existingDescriptor == null) {
                artifactDescriptorsByModuleName.put(moduleName, descriptor);
            }
            else {
                if (existingDescriptor.reference().version().isPresent()) {
                    if (descriptor.reference().version().isPresent()) {
                        final var existingVersion = existingDescriptor.reference().version().orElseThrow(() -> new IllegalStateException("Impossible: version.isPresent() was true but orElseThrow failed for [" + existingDescriptor.reference() + "]"));
                        final var version = descriptor.reference().version().orElseThrow(() -> new IllegalStateException("Impossible: version.isPresent() was true but orElseThrow failed for [" + descriptor.reference() + "]"));

                        if (version.compareTo(existingVersion) > 0) {
                            // override with the higher version
                            artifactDescriptorsByModuleName.put(moduleName, descriptor);
                        }
                        else {
                            // we discovered a duplicate, but we're ignoring the lower version
                        }

                    }
                    else {
                        // we discovered a duplicate, but the duplicate doesn't have a version, so we ignore it
                    }
                }
                else {
                    // default to using the versioned ArtifactDescriptor (when the existing doesn't have a version)
                    artifactDescriptorsByModuleName.put(moduleName, descriptor);
                }
            }
        });

        // establish the ClassPathBuilder based on the ArtifactDescriptors for non-modules (that aren't required modules)!
        // (the explicit and required modules will be placed on the module path!)
        final var classPathBuilder = PathSetBuilder.create();
        artifactDescriptorsByModuleName.values().stream()
            .filter(descriptor -> !ignored.contains(descriptor.reference()))
            .filter(descriptor -> !moduleDescriptors.get(descriptor.reference()).location().isPresent()
                && !requiredModules.contains(descriptor.reference().name()))
            .forEach(descriptor -> descriptor.path().ifPresent(classPathBuilder::add));

        // -----
        // copy the explicit modules into a module path
        final var modulePath = buildPath.resolve("modules/");
        CleanPlugin.delete(modulePath);

        // create the module path
        Files.createDirectories(modulePath);

        // copy the artifacts into the modules path
        artifactDescriptorsByModuleName.values().stream()
            .filter(descriptor -> !ignored.contains(descriptor.reference()))
            .filter(descriptor -> moduleDescriptors.get(descriptor.reference()).location().isPresent()
                || requiredModules.contains(descriptor.reference().name()))
            .forEach(descriptor -> descriptor.path()
                .ifPresent(source -> {
                    try {
                        final var target = modulePath.resolve(source.getFileName());
                        Files.copy(source, target);
                    }
                    catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                }));

        // -----
        // execute jdeps for the Java Version with the provided modules

        // build the classpath PathSet (containing the dependencies)
        final var classPathSet = classPathBuilder.build();
        final var classPath = Streams.reverse(classPathSet.stream())
            .map(Path::toString)
            .map(Strings::doubleQuoteIfContainsWhiteSpace)
            .reduce("", (left, right) -> left.isEmpty() ? right : left + File.pathSeparator + right);

        final var jdepsPath = javaHome.resolve("bin/jdeps");

        final var recordingObserver = new RecordingSubscriber<String>();
        final var stdoutObserver = StandardOutputSubscriber.of(recordingObserver);

        // the path to this artifact to analyze
        final var artifactPath = artifactDescriptorsByModuleName.get(this.moduleDescriptor.name())
            .path()
            .orElseThrow(() -> new IllegalStateException("No artifact path for module [" + this.moduleDescriptor.name() + "]"));

        try (var jdeps = this.machine.launch(Application.class,
            Executable.of(jdepsPath.toString()),
            Name.of("jdeps"),
            Argument.of("--module-path"), Argument.of(modulePath),
            Argument.of("--class-path"), Argument.of(classPath),
            Argument.of("--list-deps"),
            Argument.of("--ignore-missing-deps"),
            Argument.of("--multi-release"), Argument.of(jdk.version().major()),
            Argument.of(artifactPath),
            Console.ofSystem(),
            stdoutObserver)) {

            jdeps.onExit().get();

            if (jdeps.onExit().isCompletedExceptionally() || jdeps.exitValue().orElse(0) > 0) {
                this.recorder.error("jdeps Execution Failed [%s]", jdeps.exitValue());
                throw new RuntimeException("jdeps execution has failed");
            }

            // build maps of java platform, module and non-module dependencies
            final LinkedHashSet<ModuleDescriptor> modules = new LinkedHashSet<>();
            final LinkedHashSet<ModuleDescriptor> nonModules = new LinkedHashSet<>();
            final LinkedHashMap<ModuleDescriptor, ArtifactDescriptor> artifacts = new LinkedHashMap<>();
            final LinkedHashSet<String> unknownModules = new LinkedHashSet<>();

            // a Consumer of Artifacts together with their ModuleDescriptors
            // (to collect and categorize the ModuleDescriptors)
            final Consumer<Map.Entry<Artifact, ArtifactDescriptor>> consumeArtifact = entry -> {
                final ArtifactDescriptor artifactDescriptor = entry.getValue();
                final ModuleDescriptor descriptor = moduleDescriptors.get(artifactDescriptor.reference());

                if (descriptor != null) {
                    artifacts.put(descriptor, artifactDescriptor);

                    if (descriptor.location().isPresent()) {
                        modules.add(descriptor);
                    }
                    else {
                        nonModules.add(descriptor);
                    }
                }
            };

            recordingObserver.items()
                .map(String::trim)
                .filter(line -> !line.contains(" "))
                .forEach(moduleName -> {
                    if (JavaPlatform.isJavaPlatformModule(moduleName)) {
                        final ModuleReference reference = ModuleReference.of(moduleName, jdkVersion);
                        platformModules.add(reference);
                    }
                    else {
                        artifactDescriptors.entrySet().stream()
                            .filter(entry -> predicate.test(entry.getKey()))
                            .filter(entry -> entry.getValue().reference().name().equals(moduleName))
                            .findFirst()
                            .ifPresentOrElse(consumeArtifact, () -> unknownModules.add(moduleName));
                    }
                });

            // ensure all artifacts provided are consumed as either modules or non-modules
            artifactDescriptors.entrySet().stream()
                .forEach(consumeArtifact);

            final Table platformModulesTable = Table.create();
            platformModulesTable.addRow("Module Name");
            platformModules.stream().forEach(reference -> platformModulesTable.addRow(reference.name()));
            this.recorder.diagnostic("Java Platform Modules (%s)\n%s", jdk.version().get(),
                platformModulesTable);

            if (!modules.isEmpty()) {
                final Table modulesTable = Table.create();
                modulesTable.addRow("Module Name", "Version", "Type");
                modules.stream().forEach(descriptor ->
                    modulesTable.addRow(descriptor.name(),
                        descriptor.version().map(ModuleDescriptor.Version::get).orElse("(unknown version)"),
                        descriptor.isAutomatic() ? "automatic module" : "fully-blown module"));
                this.recorder.diagnostic("Explicit Modules\n%s", modulesTable);
            }

            if (!nonModules.isEmpty()) {
                final Table nonModulesTable = Table.create();
                nonModulesTable.addRow("Module Name (generated)", "Version");
                nonModules.stream().forEach(descriptor -> nonModulesTable.addRow(
                    descriptor.name(),
                    descriptor.version().map(ModuleDescriptor.Version::get).orElse("(unknown version)")));
                this.recorder.diagnostic("Unnamed Modules\n%s", nonModulesTable);
            }

            if (!unknownModules.isEmpty()) {
                final Table unknownModulesTable = Table.create();
                unknownModules.stream().forEach(unknownModulesTable::addRow);
                this.recorder.diagnostic("Unknown Modules\n%s", unknownModulesTable);
            }

            return new DependencyAnalysis() {
                @Override
                public Dependency dependency() {
                    return new Dependency() {
                        @Override
                        public ModuleDescriptor moduleDescriptor() {
                            return moduleDescriptor;
                        }

                        @Override
                        public ArtifactDescriptor artifactDescriptor() {
                            return artifacts.get(moduleDescriptor);
                        }
                    };
                }

                @Override
                public Stream<ModuleReference> platformModules() {
                    return platformModules.stream();
                }

                @Override
                public Stream<Dependency> dependencies() {
                    return artifactDescriptorsByModuleName.values().stream()
                        .map(descriptor -> new Dependency() {
                            @Override
                            public ModuleDescriptor moduleDescriptor() {
                                return moduleDescriptors.get(descriptor.reference());
                            }

                            @Override
                            public ArtifactDescriptor artifactDescriptor() {
                                return descriptor;
                            }
                        });
                }

                @Override
                public Stream<String> unknownModules() {
                    return unknownModules.stream();
                }

                @Override
                public Path modulePath() {
                    return modulePath;
                }
            };
        }
    }
}
