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

import build.base.configuration.Option;
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
import build.spin.module.modulesystem.ModuleGraphClassifier;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

                            // push the non-Java Platform required modules onto the stack for processing
                            moduleDescriptor.requires()
                                .peek(requires -> {
                                    if (JavaPlatform.isJavaPlatformModule(requires.name())) {
                                        platformModules.add(ModuleReference.of(requires.name(), jdkVersion));
                                    }
                                })
                                .filter(requires -> !JavaPlatform.isJavaPlatformModule(requires.name()))
                                .map(ModuleDescriptor.Requires::reference)
                                // GraalVM modules are not available in standard JDKs; exclude the entire namespace
                                .filter(r -> !r.name().startsWith("org.graalvm."))
                                .peek(r -> {
                                    // track all required module names for module-path placement
                                    // unconditionally — even if already processed. A module that was
                                    // processed before its dependant ran would otherwise never get
                                    // added to requiredModules, causing it to land on classpath
                                    // instead of module-path (the graphql-java-kickstart bug).
                                    requiredModules.add(r.name());
                                })
                                .filter(r -> !processed.contains(r))
                                .peek(r -> this.recorder.info("[jdeps] Module [%s] requires [%s] — queuing for catalog lookup", moduleDescriptor.name(), r))
                                .forEach(pending::push);

                            return reference;
                        })
                        .orElseGet(() -> {
                            // failed to obtain the ModuleDescriptor (so skipping it)
                            this.recorder.info("[jdeps] Ignoring module [%s] — no ModuleDescriptor available", reference);
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
        // First dedupe pass — by Maven coordinates (groupId + artifactId), newest version
        // wins. This catches the case where two versions of the same Maven artifact have
        // *different* JPMS module names: for example, slf4j-api-1.7.25 is automatic module
        // `slf4j.api` (filename-derived) while slf4j-api-2.0.17 is the proper JPMS module
        // `org.slf4j`. Both own package `org.slf4j`, so any ModuleFinder-based classifier
        // downstream would reject them as a split package. The module-name dedupe below
        // can't catch these because the names differ; the filename-based detection in
        // {@link AbstractCompile#resolveConflicts} can, but only after a package-overlap
        // scan. Keying directly on Maven coordinates is the strongest signal available.
        final var artifactDescriptorsByCoordinates = dedupeByMavenCoordinates(
            artifactDescriptors.values(),
            (kept, dropped) -> this.recorder.info(
                "[jdeps] Maven coordinate duplicate [%s:%s]: keeping [%s], dropping [%s]",
                kept.artifact().groupId(), kept.artifact().artifactId(),
                kept.artifact().version(), dropped.artifact().version()));

        // -----
        // Second dedupe pass — by JPMS module name. Keeps the highest version when two
        // different (groupId, artifactId) entries map to the same module name.
        // (jdeps will fail when duplicate modules are discovered)

        final var artifactDescriptorsByModuleName = new LinkedHashMap<String, ArtifactDescriptor>();

        artifactDescriptorsByCoordinates.values().forEach(descriptor -> {
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
                && !requiredModules.contains(descriptor.reference().name())
                // workspace-local jars with module-info.class go to the module-path, not here
                && !descriptor.path().map(ModuleGraphClassifier::isNamedModule).orElse(false))
            .forEach(descriptor -> descriptor.path().ifPresent(classPathBuilder::add));

        // -----
        // copy the explicit modules into a module path
        final var modulePath = buildPath.resolve("modules/");
        CleanPlugin.delete(modulePath);

        // create the module path
        Files.createDirectories(modulePath);

        // copy the artifacts into the modules path
        // -----
        // detect split packages BEFORE copying to module-path.
        // scan all candidate module-path jars for their packages; any jar that shares
        // a package with another jar must stay on classpath (JPMS forbids split packages).
        final var modulePathCandidates = new ArrayList<ArtifactDescriptor>();
        artifactDescriptorsByModuleName.values().stream()
            .filter(descriptor -> !ignored.contains(descriptor.reference()))
            .filter(descriptor -> moduleDescriptors.get(descriptor.reference()).location().isPresent()
                || requiredModules.contains(descriptor.reference().name())
                || descriptor.path().map(ModuleGraphClassifier::isNamedModule).orElse(false))
            .forEach(modulePathCandidates::add);

        final List<Path> candidatePaths = modulePathCandidates.stream()
            .flatMap(d -> d.path().stream())
            .toList();

        final Consumer<String> log = msg -> this.recorder.info("[jdeps] %s", msg);
        // Seed required-name preference from the root module so split-package conflicts
        // involving modules the root transitively requires (e.g. maven.settings under
        // Maven 4, which shares org.apache.maven.settings.v4 with maven-support) keep the
        // required side on the module-path.
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            candidatePaths,
            java.util.Set.of(this.moduleDescriptor.name()),
            log);

        // copy non-conflicting candidates to module-path; handle conflicts appropriately
        for (final var candidate : modulePathCandidates) {
            candidate.path().ifPresent(source -> {
                if (resolution.superseded().contains(source)) {
                    // older version duplicate — don't put on classpath or module-path
                    return;
                }
                if (resolution.demoted().contains(source)) {
                    classPathBuilder.add(source);
                }
                else {
                    try {
                        final var target = modulePath.resolve(source.getFileName());
                        Files.copy(source, target);
                    }
                    catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

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

        // build jdeps arguments — omit --class-path when empty (jdeps rejects empty values)
        final var jdepsArgs = new java.util.ArrayList<Option>();
        jdepsArgs.add(Executable.of(jdepsPath.toString()));
        jdepsArgs.add(Name.of("jdeps"));
        jdepsArgs.add(Argument.of("--module-path"));
        jdepsArgs.add(Argument.of(modulePath));
        if (!classPath.isEmpty()) {
            jdepsArgs.add(Argument.of("--class-path"));
            jdepsArgs.add(Argument.of(classPath));
        }
        jdepsArgs.add(Argument.of("--list-deps"));
        jdepsArgs.add(Argument.of("--ignore-missing-deps"));
        jdepsArgs.add(Argument.of("--multi-release"));
        jdepsArgs.add(Argument.of(jdk.version().major()));
        jdepsArgs.add(Argument.of(artifactPath));
        jdepsArgs.add(Console.ofSystem());
        jdepsArgs.add(stdoutObserver);

        try (var jdeps = this.machine.launch(Application.class,
            jdepsArgs.toArray(Option[]::new))) {

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

    /**
     * Dedupe a collection of {@link ArtifactDescriptor}s by Maven coordinates
     * ({@code groupId:artifactId}), keeping the highest version when duplicates are present.
     *
     * <p>This is a strictly-stronger signal than module-name dedupe: two versions of the same
     * Maven artifact may publish under <em>different</em> JPMS module names (the slf4j-api 1.x
     * → 2.x rename being the canonical example), so a downstream module-name dedupe pass cannot
     * eliminate them. Coordinate-based dedupe is the only thing that can.
     *
     * @param descriptors the descriptors to dedupe; iteration order is preserved
     * @param onDuplicate invoked as {@code (kept, dropped)} for each duplicate pair
     * @return a {@link LinkedHashMap} keyed by {@code groupId:artifactId} with the winning
     *         descriptor as the value
     */
    static LinkedHashMap<String, ArtifactDescriptor> dedupeByMavenCoordinates(
        final java.util.Collection<ArtifactDescriptor> descriptors,
        final java.util.function.BiConsumer<ArtifactDescriptor, ArtifactDescriptor> onDuplicate) {

        final var byCoordinates = new LinkedHashMap<String, ArtifactDescriptor>();
        for (final var descriptor : descriptors) {
            final var artifact = descriptor.artifact();
            final var coordinates = artifact.groupId() + ":" + artifact.artifactId();
            final var existing = byCoordinates.get(coordinates);
            if (existing == null) {
                byCoordinates.put(coordinates, descriptor);
            }
            else if (artifact.version().compareTo(existing.artifact().version()) > 0) {
                byCoordinates.put(coordinates, descriptor);
                onDuplicate.accept(descriptor, existing);
            }
            else {
                onDuplicate.accept(existing, descriptor);
            }
        }
        return byCoordinates;
    }
}
