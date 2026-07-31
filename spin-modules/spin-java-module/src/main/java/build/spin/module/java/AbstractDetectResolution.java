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
import build.base.version.VersionOrder;
import build.codemodel.foundation.descriptor.RequiresModuleDescriptor;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.percolate.core.ModuleGraphClassifier;
import build.spin.Project;
import build.spin.Task;
import build.spin.common.task.BuildOutputLocations;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.CompilationResolution;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 *   <li>Resolve each external require transitively via the {@link Artifact.Resolver}
 *       ({@link Artifact.Resolver#resolveTransitive}), so artifact-graph cycles are handled by the
 *       resolver rather than a hand-rolled BFS.</li>
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

        // Step 2 — Resolve external requires via the Artifact.Resolver (handles artifact-graph cycles internally).
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
            final Optional<Artifact> artifact = this.catalog.getArtifact(moduleReference, Optional.of(this.recorder));

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

        // Step 2c — Correct candidates whose resolved version diverges from a project-wide pin.
        // Each top-level `requires` in Step 2 is resolved independently via the resolver, using
        // whatever version that require's own pom pins transitively (e.g. a Helidon dependency
        // transitively pulls in the older protobuf-java/grpc versions pinned by Helidon's own BOM)
        // — even when this project's pom.xml pins a newer version directly, if that artifact is only
        // ever reached transitively (never a direct `requires`), there's no resolver call scoped to
        // this project's own pom to apply that pin. `versioning` already has the correct, workspace-wide
        // answer (it's populated from every pom.xml and already used to correct `requires`-clause
        // versions for jlink/jdeps); reuse it here to re-resolve any candidate that disagrees.
        final List<Path> versionCorrectedCandidates =
            correctPinnedVersions(externalCandidates, this.versioning, this.catalog, this.resolver, this.recorder);

        // Step 2d — Dedupe external candidates by Maven coordinate, keeping the highest version.
        // Each top-level `requires` in Step 2 is resolved independently via the resolver, so two
        // different requires can transitively pull in different versions of the same artifact
        // with no cross-call reconciliation (e.g. this project directly requires a newer
        // graphql-java/grpc/protobuf, while a Helidon dependency transitively pulls in the older
        // versions pinned by Helidon's own BOM). Without this, both versions land on the
        // classpath/module-path together and javac non-deterministically picks the wrong one.
        final List<Path> dedupedExternalCandidates =
            dedupeByMavenCoordinate(versionCorrectedCandidates, this.recorder);

        // Step 3 — Classify all candidates via ModuleGraphClassifier.
        final List<Path> candidates = new ArrayList<>();
        candidates.addAll(siblingCandidates);
        candidates.addAll(dedupedExternalCandidates);

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

    /**
     * Re-resolves any candidate {@link Path} whose actual (on-disk) version diverges from the
     * project-wide pin recorded in {@code versioning}, replacing it with the {@link Path}s resolved
     * for the pinned version instead.
     *
     * <p>A candidate's module name is derived directly from the jar/directory via {@link ModuleFinder}
     * — the same JDK-native mechanism {@link ModuleGraphClassifier} uses — so this works whether the
     * candidate is a proper module, carries an {@code Automatic-Module-Name}, or falls back to a
     * filename-derived automatic module name. Candidates with no version pin, an unparseable on-disk
     * version, or an already-matching version are returned unchanged.
     *
     * <p>Correction runs to a fixed point: paths pulled in by re-resolving a mismatched candidate are
     * themselves checked against their own pin (e.g. re-resolving a mismatched Helidon-transitive
     * {@code grpc-core} may pull in an old {@code protobuf-java} that is itself pinned elsewhere in the
     * workspace). Each module name is re-resolved at most once per call — repeat occurrences of the
     * same module (very likely, since deduping by coordinate happens in a later step) reuse that
     * single correction rather than issuing a redundant resolver call, and this also bounds the
     * fixed-point iteration against cycles in the transitive graph.
     *
     * @param paths the resolved external candidate {@link Path}s
     * @param versioning the project-wide {@link ModuleVersioning}
     * @param catalog the {@link ModuleCatalog}, used to recover the Maven coordinate for a module
     *     name so the pinned version can be re-resolved
     * @param resolver the {@link Artifact.Resolver} used to re-resolve at the pinned version
     * @param recorder the {@link TelemetryRecorder} for diagnostics
     *
     * @return the version-corrected {@link Path}s
     */
    // Visible for testing.
    static List<Path> correctPinnedVersions(final List<Path> paths,
                                            final ModuleVersioning versioning,
                                            final ModuleCatalog catalog,
                                            final Artifact.Resolver resolver,
                                            final TelemetryRecorder recorder) {

        final Set<String> resolvedModuleNames = new HashSet<>();
        final List<Path> corrected = new ArrayList<>();
        final Deque<Path> worklist = new ArrayDeque<>(paths);

        while (!worklist.isEmpty()) {
            final Path path = worklist.poll();
            final Optional<String> moduleName = moduleNameOf(path, recorder);

            if (moduleName.isEmpty()) {
                corrected.add(path);
                continue;
            }

            final Optional<Version> pinnedVersion = versioning.getVersion(moduleName.get());

            if (pinnedVersion.isEmpty()) {
                corrected.add(path);
                continue;
            }

            final Path versionDir = path.getParent();
            final Optional<Version> onDiskVersion = versionDir == null
                ? Optional.empty()
                : Version.tryParse(versionDir.getFileName().toString());

            if (onDiskVersion.isEmpty() || onDiskVersion.get().equals(pinnedVersion.get())) {
                corrected.add(path);
                continue;
            }

            if (!resolvedModuleNames.add(moduleName.get())) {
                // already re-resolved this module during this call — accept as-is rather than issuing
                // a duplicate resolver call or looping forever on a transitive-graph cycle
                corrected.add(path);
                continue;
            }

            final ModuleReference reference = ModuleReference.of(moduleName.get(), pinnedVersion.get());
            final Optional<Artifact> artifact = catalog.getArtifact(reference, Optional.of(recorder));

            if (artifact.isEmpty()) {
                recorder.warn(
                    "Module [%s] is pinned to [%s] but is not present in the ModuleCatalog — "
                        + "keeping on-disk version [%s]",
                    moduleName.get(), pinnedVersion.get(), onDiskVersion.get());
                corrected.add(path);
                continue;
            }

            final var resolved = resolver.resolveTransitive(artifact.get());
            if (resolved.isException()) {
                resolved.exception().ifPresent(e -> recorder.error(
                    "Failed to re-resolve [%s] at pinned version [%s]: %s",
                    moduleName.get(), pinnedVersion.get(), e.getMessage()));
                recorder.warn(
                    "Module [%s] failed to re-resolve at pinned version [%s] — keeping on-disk version [%s]",
                    moduleName.get(), pinnedVersion.get(), onDiskVersion.get());
                corrected.add(path);
            }
            else {
                recorder.warn(
                    "Module [%s] on-disk version [%s] diverges from workspace pin [%s] — correcting",
                    moduleName.get(), onDiskVersion.get(), pinnedVersion.get());
                // re-queue the newly resolved paths so their own pins are checked too, achieving a
                // fixed point rather than a single correction pass
                resolved.ifPresent(worklist::addAll);
            }
        }

        return corrected;
    }

    /**
     * Dedupes resolved artifact {@link Path}s by Maven coordinate, keeping the highest version.
     *
     * <p>Every path resolved via {@link Artifact.Resolver#resolveTransitive} sits in a local Maven
     * repository under the standard {@code <groupId-path>/<artifactId>/<version>/<artifactId>-
     * <version>[-classifier].<ext>} layout. Rather than assuming where the repository root is, this
     * uses the artifact directory's full parent path (i.e. everything except the version and filename
     * segments) as the coordinate key — two paths share a coordinate iff they share that parent path,
     * which is true for the same (groupId, artifactId) and never true otherwise.
     *
     * <p>Versions are compared with {@link VersionOrder#MAVEN}, not {@link Version#compareTo}, so that
     * Maven qualifiers (e.g. {@code rc}, {@code beta}, {@code snapshot}) rank the way Maven itself
     * ranks them rather than lexicographically.
     *
     * @param paths the resolved artifact {@link Path}s to dedupe
     * @param recorder the {@link TelemetryRecorder} for diagnostics
     *
     * @return the deduped {@link Path}s, in first-seen order
     */
    // Visible for testing.
    static List<Path> dedupeByMavenCoordinate(final List<Path> paths, final TelemetryRecorder recorder) {

        final LinkedHashMap<Path, Path> byCoordinate = new LinkedHashMap<>();
        final LinkedHashMap<Path, Version> versionByCoordinate = new LinkedHashMap<>();

        for (final Path path : paths) {
            final Path versionDir = path.getParent();
            final Path artifactDir = versionDir == null ? null : versionDir.getParent();

            if (artifactDir == null) {
                // can't determine a coordinate — treat as its own unique entry
                byCoordinate.put(path, path);
                continue;
            }

            final Optional<Version> version = Version.tryParse(versionDir.getFileName().toString());

            if (version.isEmpty()) {
                // can't parse a version — treat as its own unique entry
                byCoordinate.put(path, path);
                continue;
            }

            final Version existingVersion = versionByCoordinate.get(artifactDir);
            if (existingVersion == null) {
                byCoordinate.put(artifactDir, path);
                versionByCoordinate.put(artifactDir, version.get());
            }
            else {
                final int comparison = VersionOrder.MAVEN.compare(version.get(), existingVersion);
                if (comparison > 0) {
                    recorder.warn(
                        "Coordinate [%s] has multiple resolved versions [%s, %s] on the candidate graph — "
                            + "keeping [%s]",
                        artifactDir, existingVersion, version.get(), version.get());
                    byCoordinate.put(artifactDir, path);
                    versionByCoordinate.put(artifactDir, version.get());
                } else if (comparison < 0) {
                    recorder.warn(
                        "Coordinate [%s] has multiple resolved versions [%s, %s] on the candidate graph — "
                            + "keeping [%s]",
                        artifactDir, existingVersion, version.get(), existingVersion);
                }
                // else: same version resolved via a different transitive path - not a real ambiguity
            }
        }

        return new ArrayList<>(byCoordinate.values());
    }

    private static Optional<String> moduleNameOf(final Path path, final TelemetryRecorder recorder) {
        try {
            return ModuleFinder.of(path).findAll().stream()
                .findFirst()
                .map(ref -> ref.descriptor().name());
        }
        catch (final RuntimeException e) {
            recorder.warn(e, "Failed to derive a module name for candidate [%s] — treating as unnamed", path);
            return Optional.empty();
        }
    }
}
