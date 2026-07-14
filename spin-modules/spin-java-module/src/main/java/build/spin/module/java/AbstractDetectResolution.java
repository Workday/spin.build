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

import build.base.telemetry.TelemetryRecorder;
import build.base.version.Version;
import build.codemodel.foundation.descriptor.RequiresModuleDescriptor;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spin.Project;
import build.spin.Task;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.CompilationResolution;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleGraphClassifier;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract {@link Task} that resolves the source-graph dependency closure for a
 * {@link JavaPlugin} {@link Project}, partitioning all candidates into a
 * {@link CompilationResolution} (module-path vs classpath).
 *
 * <p>The algorithm has three steps:
 * <ol>
 *   <li>Walk the workspace-sibling transitive closure starting from the injected
 *       the injected {@link JDKModuleDescriptor}'s {@code requiresClauses()}.</li>
 *   <li>Resolve each external require transitively via Aether ({@link Artifact.Resolver#resolveTransitive}),
 *       so artifact-graph cycles are handled by the resolver rather than a hand-rolled BFS.</li>
 *   <li>Classify all candidates via {@link ModuleGraphClassifier#classify} with the three-tier
 *       split-package policy (required-module preference → proper-over-automatic → demote-all).</li>
 * </ol>
 *
 * <p>Concrete subclasses are empty inner classes of the enclosing plugin, which is what
 * causes the DI framework to bind {@link JDKModuleDescriptor} to the correct plugin-scoped
 * descriptor (main vs test).
 */
public abstract class AbstractDetectResolution
    implements Task<CompilationResolution> {

    @Inject
    private Project project;

    @Inject
    private JDKModuleDescriptor moduleDescriptor;

    @Inject
    private ModuleCatalog catalog;

    @Inject
    private ModuleVersioning versioning;

    @Inject
    private Artifact.Resolver resolver;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    @Inject
    private TargetDirectoryName target;

    @Inject
    private TelemetryRecorder recorder;

    /**
     * Subclasses may override this to inject additional candidate paths (e.g. the main
     * compiled classes directory for a JUnit test plugin running in the same project).
     * These paths are prepended to the sibling candidates before classification.
     *
     * @return a {@link Stream} of additional candidate {@link Path}s
     */
    protected Stream<Path> additionalSiblingCandidates() {
        return Stream.empty();
    }

    /**
     * Subclasses may override this to supply additional infrastructure {@link Artifact}s that
     * are always resolved transitively and included as classification candidates, regardless of
     * whether the project explicitly declares them as dependencies.
     *
     * <p>This is the right place for build-tool runner JARs (e.g. the JUnit Platform
     * ConsoleLauncher) that spin provides itself rather than expecting the project to declare.
     *
     * @return a {@link Stream} of additional {@link Artifact}s to resolve
     */
    protected Stream<Artifact> additionalArtifacts() {
        return Stream.empty();
    }

    /**
     * Resolves the compilation dependency closure and classifies all candidates.
     *
     * @return the {@link CompilationResolution}
     */
    public CompilationResolution create() {

        // Step 1 — Walk the workspace-sibling transitive closure.
        final List<Path> siblingCandidates = new ArrayList<>();
        final LinkedHashMap<String, RequiresModuleDescriptor> externalRequires = new LinkedHashMap<>();
        final Set<String> visited = new HashSet<>();

        // seed the frontier from additional paths first (e.g. main classes for test scope)
        additionalSiblingCandidates().forEach(siblingCandidates::add);

        // seed the frontier from the direct requires of the injected module descriptor
        final List<RequiresModuleDescriptor> frontier = new ArrayList<>();
        this.moduleDescriptor.requiresClauses()
            .filter(r -> !JavaPlatform.isJavaPlatformModule(r.requiresModuleName().toString()))
            .forEach(frontier::add);

        while (!frontier.isEmpty()) {
            final RequiresModuleDescriptor requires = frontier.remove(0);
            final String name = requires.requiresModuleName().toString();

            if (JavaPlatform.isJavaPlatformModule(name) || !visited.add(name)) {
                continue;
            }

            // look for a workspace sibling whose main JavaCompilerPlugin descriptor matches
            final Optional<Project> sibling = this.project.workspace()
                .stream()
                .filter(prj -> prj.plugins(JavaCompilerPlugin.class)
                    .findFirst()
                    .map(JavaCompilerPlugin::getModuleDescriptor)
                    .map(d -> name.equals(d.moduleName().toString()))
                    .orElse(false))
                .findFirst();

            if (sibling.isPresent()) {
                final Optional<Path> siblingClasses = resolveCompiledOutput(
                    sibling.get().path(), this.buildDirectoryName.get(), this.target.get());
                siblingClasses.ifPresent(siblingCandidates::add);

                // enqueue the sibling's own direct requires onto the frontier
                sibling.get().plugins(JavaCompilerPlugin.class)
                    .findFirst()
                    .ifPresent(plugin -> plugin.getModuleDescriptor().requiresClauses()
                        .filter(r -> !JavaPlatform.isJavaPlatformModule(r.requiresModuleName().toString()))
                        .filter(r -> !visited.contains(r.requiresModuleName().toString()))
                        .forEach(frontier::add));
            }
            else {
                // external require — deduplicated by module name via LinkedHashMap
                externalRequires.putIfAbsent(name, requires);
            }
        }

        // Step 2 — Resolve external requires via Aether (handles artifact-graph cycles internally).
        final List<Path> externalCandidates = new ArrayList<>();

        for (final RequiresModuleDescriptor r : externalRequires.values()) {
            final Optional<Version> moduleVersion = this.versioning.getVersion(r.requiresModuleName().toString());
            final Optional<Version> requiresVersion = JDKModuleDescriptor.requiresVersion(r);

            final Version requiredVersion;
            if (moduleVersion.isPresent()) {
                if (requiresVersion.isPresent() && !requiresVersion.get().equals(moduleVersion.get())) {
                    this.recorder.warn(
                        "External require [%s] in [%s] declares version [%s] but the workspace ModuleVersioning "
                            + "catalog resolved [%s] — using the catalog version",
                        r.requiresModuleName().toString(), this.project.name(),
                        requiresVersion.get(), moduleVersion.get());
                }
                requiredVersion = moduleVersion.get();
            }
            else if (requiresVersion.isPresent()) {
                requiredVersion = requiresVersion.get();
            }
            else {
                this.recorder.warn(
                    "Cannot determine version for external require [%s] in [%s] — skipping",
                    r.requiresModuleName().toString(), this.project.name());
                continue;
            }

            final ModuleReference moduleReference = ModuleReference.of(r.requiresModuleName().toString(), requiredVersion);
            final Optional<Artifact> artifact = this.catalog.getArtifact(moduleReference);

            if (artifact.isEmpty()) {
                this.recorder.warn(
                    "Module [%s] not found in ModuleCatalog — skipping", r.requiresModuleName().toString());
                continue;
            }

            final var resolved = this.resolver.resolveTransitive(artifact.get());
            if (resolved.isException()) {
                resolved.exception().ifPresent(e -> this.recorder.error(
                    "Failed to resolve transitive dependencies for [%s]: %s",
                    artifact.get(), e.getMessage()));
            }
            else {
                resolved.ifPresent(externalCandidates::addAll);
            }
        }

        // Step 2b — Resolve additional infrastructure artifacts provided by subclasses.
        additionalArtifacts().forEach(artifact -> {
            final var resolved = this.resolver.resolveTransitive(artifact);
            if (resolved.isException()) {
                resolved.exception().ifPresent(e -> this.recorder.error(
                    "Failed to resolve infrastructure artifact [%s]: %s",
                    artifact, e.getMessage()));
            }
            else {
                resolved.ifPresent(externalCandidates::addAll);
            }
        });

        // Step 3 — Classify all candidates via ModuleGraphClassifier.
        final List<Path> candidates = new ArrayList<>();
        candidates.addAll(siblingCandidates);
        candidates.addAll(externalCandidates);

        final Set<String> directRequireNames = this.moduleDescriptor.requiresClauses()
            .map(r -> r.requiresModuleName().toString())
            .filter(n -> !JavaPlatform.isJavaPlatformModule(n))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        final ModuleGraphClassifier.Classification classification = ModuleGraphClassifier.classify(
            candidates,
            directRequireNames,
            msg -> this.recorder.diagnostic("[classify] %s", msg));

        return new CompilationResolution(classification.modulePath(), classification.classPath());
    }

    // Visible for testing.
    static Optional<Path> resolveCompiledOutput(final Path projectPath,
                                                final String buildDirectoryName,
                                                final String targetDirectoryName) {
        return BuildOutputLocations.spin(projectPath, buildDirectoryName, targetDirectoryName)
            .or(() -> BuildOutputLocations.maven(projectPath, "classes"))
            .or(() -> BuildOutputLocations.gradle(projectPath, "classes/java/main"));
    }
}
