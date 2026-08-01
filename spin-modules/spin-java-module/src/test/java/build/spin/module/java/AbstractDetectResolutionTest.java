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

import build.base.foundation.Exceptional;
import build.base.foundation.UniformResource;
import build.base.telemetry.Telemetry;
import build.base.telemetry.TelemetryRecorder;
import build.base.version.Version;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spin.common.telemetry.TelemetryPublisher;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractDetectResolutionTest {

    @TempDir
    Path projectRoot;

    private static TelemetryRecorder recorder() {
        return new TelemetryPublisher(
            UniformResource.createURI("test", "AbstractDetectResolutionTest"),
            System.out::println);
    }

    // an Artifact.Resolver stub whose other members every test in this class leaves
    // unexercised — only resolveTransitive varies per test.
    private static Artifact.Resolver stubResolver(
        final Function<Artifact, Exceptional<List<Path>>> resolveTransitive) {

        return new Artifact.Resolver() {
            @Override
            public Exceptional<Path> resolve(final Artifact artifact) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Exceptional<ModuleReference> getModuleReference(
                final Artifact artifact, final ModuleCatalog catalog) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Exceptional<JDKModuleDescriptor> getModuleDescriptor(
                final Artifact artifact, final ModuleCatalog catalog, final ModuleVersioning versioning) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Exceptional<List<Path>> resolveTransitive(final Artifact artifact) {
                return resolveTransitive.apply(artifact);
            }
        };
    }

    // creates a minimal jar with the given Automatic-Module-Name at <repoRoot>/<groupPath>/<artifactId>/<version>/<artifactId>-<version>.jar
    private static Path createArtifactJar(final Path repoRoot,
                                          final String groupPath,
                                          final String artifactId,
                                          final String version,
                                          final String automaticModuleName) throws IOException {

        final Path versionDir = repoRoot.resolve(groupPath).resolve(artifactId).resolve(version);
        Files.createDirectories(versionDir);

        final Path jar = versionDir.resolve(artifactId + "-" + version + ".jar");
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Automatic-Module-Name"), automaticModuleName);

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            // no entries needed — an automatic module is derived purely from the manifest/filename
        }

        return jar;
    }

    @Test
    void resolveCompiledOutput_noneExists_returnsEmpty() {
        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes")).isEmpty();
    }

    @Test
    void resolveCompiledOutput_spinOutputExists_returnsSpinPath() throws IOException {
        final Path spinOutput = projectRoot.resolve(".build/main/classes");
        Files.createDirectories(spinOutput);

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(spinOutput);
    }

    @Test
    void resolveCompiledOutput_mavenClassesExist_returnsMavenPath() throws IOException {
        final Path mavenClasses = projectRoot.resolve("target/classes");
        Files.createDirectories(mavenClasses);

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(mavenClasses);
    }

    @Test
    void resolveCompiledOutput_gradleClassesExist_returnsGradlePath() throws IOException {
        final Path gradleClasses = projectRoot.resolve("build/classes/java/main");
        Files.createDirectories(gradleClasses);

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(gradleClasses);
    }

    @Test
    void resolveCompiledOutput_spinAndMavenExist_prefersFresherMaven() throws IOException {
        final Path spinOutput = projectRoot.resolve(".build/main/classes");
        final Path mavenClasses = projectRoot.resolve("target/classes");
        Files.createDirectories(spinOutput);
        Files.createDirectories(mavenClasses);
        setModifiedTime(spinOutput, FileTime.fromMillis(1_000));
        setModifiedTime(mavenClasses, FileTime.fromMillis(2_000));

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(mavenClasses);
    }

    @Test
    void resolveCompiledOutput_spinAndMavenExist_prefersFresherSpin() throws IOException {
        final Path spinOutput = projectRoot.resolve(".build/main/classes");
        final Path mavenClasses = projectRoot.resolve("target/classes");
        Files.createDirectories(spinOutput);
        Files.createDirectories(mavenClasses);
        setModifiedTime(spinOutput, FileTime.fromMillis(2_000));
        setModifiedTime(mavenClasses, FileTime.fromMillis(1_000));

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(spinOutput);
    }

    @Test
    void resolveCompiledOutput_mavenIsStaleLeftoverFromEarlierSpinBuild_prefersCurrentSpinOutput() throws IOException {
        // the exact scenario this freshness comparison exists for: a project once built by spin
        // directly still has a stale target/classes from that era, but is now built by Maven and
        // recompiled since — the current .build/ output must win, not whichever tool happens to be
        // checked first.
        final Path spinOutput = projectRoot.resolve(".build/main/classes");
        final Path mavenClasses = projectRoot.resolve("target/classes");
        Files.createDirectories(mavenClasses);
        final Path staleClass = mavenClasses.resolve("Stale.class");
        Files.createFile(staleClass);
        setModifiedTime(staleClass, FileTime.fromMillis(1_000));
        setModifiedTime(mavenClasses, FileTime.fromMillis(1_000));

        Files.createDirectories(spinOutput);
        final Path freshClass = spinOutput.resolve("Fresh.class");
        Files.createFile(freshClass);
        setModifiedTime(freshClass, FileTime.fromMillis(5_000));
        setModifiedTime(spinOutput, FileTime.fromMillis(5_000));

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(spinOutput);
    }

    private static void setModifiedTime(final Path path, final FileTime time) throws IOException {
        Files.setLastModifiedTime(path, time);
    }

    @Test
    void dedupeByMavenCoordinate_sameCoordinateDifferentVersions_keepsHighestVersion() {
        final Path repo = Path.of("/repo");
        final Path older = repo.resolve("io/helidon/grpc/grpc-core/1.0/grpc-core-1.0.jar");
        final Path newer = repo.resolve("io/helidon/grpc/grpc-core/2.0/grpc-core-2.0.jar");

        assertThat(AbstractDetectResolution.dedupeByMavenCoordinate(List.of(older, newer), recorder()))
            .containsExactly(newer);
    }

    @Test
    void dedupeByMavenCoordinate_sameCoordinateSameVersion_keepsFirstWithoutWarning() {
        final Path repo = Path.of("/repo");
        final Path first = repo.resolve("build/base/base-mereology/0.29.0/base-mereology-0.29.0.jar");
        final Path second = repo.resolve("build/base/base-mereology/0.29.0/base-mereology-0.29.0.jar");
        final List<Telemetry> emitted = new ArrayList<>();
        final TelemetryRecorder recorder = new TelemetryPublisher(
            UniformResource.createURI("test", "AbstractDetectResolutionTest"),
            emitted::add);

        assertThat(AbstractDetectResolution.dedupeByMavenCoordinate(List.of(first, second), recorder))
            .containsExactly(first);
        assertThat(emitted).isEmpty();
    }

    @Test
    void dedupeByMavenCoordinate_differentCoordinates_keepsBoth() {
        final Path repo = Path.of("/repo");
        final Path grpc = repo.resolve("io/helidon/grpc/grpc-core/1.0/grpc-core-1.0.jar");
        final Path protobuf = repo.resolve("com/google/protobuf/protobuf-java/3.0/protobuf-java-3.0.jar");

        assertThat(AbstractDetectResolution.dedupeByMavenCoordinate(List.of(grpc, protobuf), recorder()))
            .containsExactly(grpc, protobuf);
    }

    @Test
    void correctPinnedVersions_onDiskVersionDivergesFromPin_reResolvesAtPinnedVersion() throws IOException {
        final Path repo = projectRoot.resolve("repo");
        final Path helidonPinned = createArtifactJar(
            repo, "io/grpc", "grpc-core", "1.10.0", "io.grpc");
        final Path directlyPinned = createArtifactJar(
            repo, "io/grpc", "grpc-core", "1.60.0", "io.grpc");

        final ModuleVersioning versioning = moduleName ->
            "io.grpc".equals(moduleName) ? Optional.of(Version.parse("1.60.0")) : Optional.empty();

        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("io.grpc", Artifact.create("io.grpc", "grpc-core", "1.60.0", "jar"));

        final Artifact.Resolver resolver = stubResolver(artifact -> "1.60.0".equals(artifact.version().get())
            ? Exceptional.of(List.of(directlyPinned))
            : Exceptional.ofException(new IllegalStateException("unexpected artifact " + artifact)));

        final List<Path> corrected = AbstractDetectResolution.correctPinnedVersions(
            List.of(helidonPinned), versioning, catalog, resolver, recorder());

        assertThat(corrected).containsExactly(directlyPinned);
    }

    @Test
    void correctPinnedVersions_pinnedReferenceIsAmbiguousInCatalog_warns() throws IOException {
        final Path repo = projectRoot.resolve("repo");
        final Path onDisk = createArtifactJar(
            repo, "io/grpc", "grpc-core", "1.10.0", "io.grpc");
        final Path resolvedAtPin = repo.resolve("resolved-at-pin.jar");

        final ModuleVersioning versioning = moduleName ->
            "io.grpc".equals(moduleName) ? Optional.of(Version.parse("1.60.0")) : Optional.empty();

        // two distinct artifacts both satisfy the pinned reference — this is the exact ambiguity
        // ModuleCatalog#getArtifact(reference, Optional<TelemetryRecorder>) is meant to warn about,
        // but correctPinnedVersions calls the recorder-less overload, so the warning never fires.
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("io.grpc", Artifact.create("io.grpc", "grpc-core", "1.60.0", "jar"))
            .add("io.grpc", Artifact.create("io.grpc", "grpc-core-shaded", "1.60.0", "jar"));

        final Artifact.Resolver resolver = stubResolver(_ -> Exceptional.of(List.of(resolvedAtPin)));

        final List<Telemetry> emitted = new ArrayList<>();
        final TelemetryRecorder recorder = new TelemetryPublisher(
            UniformResource.createURI("test", "AbstractDetectResolutionTest"),
            emitted::add);

        AbstractDetectResolution.correctPinnedVersions(
            List.of(onDisk), versioning, catalog, resolver, recorder);

        assertThat(emitted)
            .as("an ambiguous catalog match for the pinned reference should warn")
            .anyMatch(t -> t.toString().contains("matches multiple distinct Artifact Constraints"));
    }

    @Test
    void correctPinnedVersions_ambiguousCatalogMatchThenReResolutionFails_doesNotClaimTheAmbiguousArtifactWasKept()
        throws IOException {

        final Path repo = projectRoot.resolve("repo");
        final Path onDisk = createArtifactJar(
            repo, "io/grpc", "grpc-core", "1.10.0", "io.grpc");

        final ModuleVersioning versioning = moduleName ->
            "io.grpc".equals(moduleName) ? Optional.of(Version.parse("1.60.0")) : Optional.empty();

        // same ambiguity as correctPinnedVersions_pinnedReferenceIsAmbiguousInCatalog_warns, but this
        // time re-resolving the artifact getArtifact picked fails, so the method actually falls back
        // to the on-disk 1.10.0 jar — not the "kept" grpc-core:1.60.0 the ambiguity warning names.
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("io.grpc", Artifact.create("io.grpc", "grpc-core", "1.60.0", "jar"))
            .add("io.grpc", Artifact.create("io.grpc", "grpc-core-shaded", "1.60.0", "jar"));

        final Artifact.Resolver resolver = stubResolver(
            _ -> Exceptional.ofException(new IllegalStateException("resolution failure")));

        final List<Telemetry> emitted = new ArrayList<>();
        final TelemetryRecorder recorder = new TelemetryPublisher(
            UniformResource.createURI("test", "AbstractDetectResolutionTest"),
            emitted::add);

        final List<Path> corrected = AbstractDetectResolution.correctPinnedVersions(
            List.of(onDisk), versioning, catalog, resolver, recorder);

        assertThat(corrected)
            .as("re-resolution failed, so the on-disk jar should be kept")
            .containsExactly(onDisk);
        assertThat(emitted)
            .as("the log should say the on-disk version was kept after the failed re-resolution, "
                + "not just leave the earlier 'keeping [grpc-core:1.60.0]' ambiguity message unqualified")
            .anyMatch(t -> t.toString().contains("keeping on-disk version"));
    }

    @Test
    void dedupeByMavenCoordinate_qualifiersDisagreeWithLexicographicOrder_usesMavenRanking() {
        final Path repo = Path.of("/repo");
        // lexicographically "cr" < "milestone" (c < m), but Maven ranks milestone(3) below rc/cr(4) —
        // the milestone build is the older one and should be discarded in favor of the rc build.
        final Path milestone = repo.resolve("io/helidon/grpc/grpc-core/1.0-milestone/grpc-core-1.0-milestone.jar");
        final Path releaseCandidate = repo.resolve("io/helidon/grpc/grpc-core/1.0-cr/grpc-core-1.0-cr.jar");

        assertThat(AbstractDetectResolution.dedupeByMavenCoordinate(List.of(milestone, releaseCandidate), recorder()))
            .containsExactly(releaseCandidate);
    }

    @Test
    void correctPinnedVersions_pinnedModuleNotInCatalog_keepsOnDiskVersion() throws IOException {
        final Path repo = projectRoot.resolve("repo");
        final Path onDisk = createArtifactJar(repo, "io/grpc", "grpc-core", "1.10.0", "io.grpc");

        final ModuleVersioning versioning = moduleName ->
            "io.grpc".equals(moduleName) ? Optional.of(Version.parse("1.60.0")) : Optional.empty();

        // catalog has no entry for the pinned reference
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create();

        final Artifact.Resolver resolver = stubResolver(_ -> {
            throw new UnsupportedOperationException("should not resolve — no catalog entry for the pin");
        });

        final List<Path> corrected = AbstractDetectResolution.correctPinnedVersions(
            List.of(onDisk), versioning, catalog, resolver, recorder());

        assertThat(corrected).containsExactly(onDisk);
    }

    @Test
    void correctPinnedVersions_reResolutionFails_keepsOnDiskVersion() throws IOException {
        final Path repo = projectRoot.resolve("repo");
        final Path onDisk = createArtifactJar(repo, "io/grpc", "grpc-core", "1.10.0", "io.grpc");

        final ModuleVersioning versioning = moduleName ->
            "io.grpc".equals(moduleName) ? Optional.of(Version.parse("1.60.0")) : Optional.empty();

        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("io.grpc", Artifact.create("io.grpc", "grpc-core", "1.60.0", "jar"));

        final Artifact.Resolver resolver = stubResolver(
            _ -> Exceptional.ofException(new IllegalStateException("resolution failure")));

        final List<Path> corrected = AbstractDetectResolution.correctPinnedVersions(
            List.of(onDisk), versioning, catalog, resolver, recorder());

        assertThat(corrected).containsExactly(onDisk);
    }

    @Test
    void correctPinnedVersions_transitiveCorrectionItselfDivergesFromPin_correctsToFixedPoint() throws IOException {
        final Path repo = projectRoot.resolve("repo");
        // grpc-core is pinned to 1.60.0; the on-disk copy (pulled in transitively by Helidon) is 1.10.0.
        final Path grpcOnDisk = createArtifactJar(repo, "io/grpc", "grpc-core", "1.10.0", "io.grpc");
        // re-resolving grpc-core at 1.60.0 pulls in a protobuf-java that is itself stale (2.0.0),
        // even though protobuf-java is pinned workspace-wide to 3.0.0.
        final Path grpcCorrected = createArtifactJar(repo, "io/grpc", "grpc-core", "1.60.0", "io.grpc");
        final Path protobufStale = createArtifactJar(
            repo, "com/google/protobuf", "protobuf-java", "2.0.0", "com.google.protobuf");
        final Path protobufCorrected = createArtifactJar(
            repo, "com/google/protobuf", "protobuf-java", "3.0.0", "com.google.protobuf");

        final ModuleVersioning versioning = moduleName -> switch (moduleName) {
            case "io.grpc" -> Optional.of(Version.parse("1.60.0"));
            case "com.google.protobuf" -> Optional.of(Version.parse("3.0.0"));
            default -> Optional.empty();
        };

        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("io.grpc", Artifact.create("io.grpc", "grpc-core", "1.60.0", "jar"))
            .add("com.google.protobuf", Artifact.create("com.google.protobuf", "protobuf-java", "3.0.0", "jar"));

        final Artifact.Resolver resolver = stubResolver(artifact -> switch (artifact.version().get()) {
            case "1.60.0" -> Exceptional.of(List.of(grpcCorrected, protobufStale));
            case "3.0.0" -> Exceptional.of(List.of(protobufCorrected));
            default -> Exceptional.ofException(new IllegalStateException("unexpected artifact " + artifact));
        });

        final List<Path> corrected = AbstractDetectResolution.correctPinnedVersions(
            List.of(grpcOnDisk), versioning, catalog, resolver, recorder());

        assertThat(corrected).containsExactly(grpcCorrected, protobufCorrected);
    }

    @Test
    void correctPinnedVersions_onDiskVersionAlreadyMatchesPin_returnsUnchanged() throws IOException {
        final Path repo = projectRoot.resolve("repo");
        final Path jar = createArtifactJar(repo, "io/grpc", "grpc-core", "1.60.0", "io.grpc");

        final ModuleVersioning versioning = moduleName ->
            "io.grpc".equals(moduleName) ? Optional.of(Version.parse("1.60.0")) : Optional.empty();

        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create();

        final Artifact.Resolver resolver = stubResolver(_ -> {
            throw new UnsupportedOperationException("should not re-resolve when already pinned");
        });

        final List<Path> corrected = AbstractDetectResolution.correctPinnedVersions(
            List.of(jar), versioning, catalog, resolver, recorder());

        assertThat(corrected).containsExactly(jar);
    }
}
