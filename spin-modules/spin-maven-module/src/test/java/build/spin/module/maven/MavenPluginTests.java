package build.spin.module.maven;

/*-
 * #%L
 * Spin Maven Module
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

import build.base.io.PathSet;
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.TypeLiteral;
import build.spawn.platform.local.LocalMachine;
import build.spin.Project;
import build.spin.Resource;
import build.spin.common.injection.ProjectResourceResolver;
import build.spin.common.telemetry.TelemetryPublisher;
import build.spin.module.configuration.Configuration;
import build.spin.module.gpg.SignableResource;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ArtifactDescriptor;
import build.spin.module.modulesystem.ModuleReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MavenPlugin}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class MavenPluginTests {

    private static final ModuleReference REFERENCE = ModuleReference.of("test.module");

    private static final LocalMachine LOCAL_MACHINE = new LocalMachine(uri ->
        TelemetryPublisher.of(uri, event -> System.err.printf("%s%n", event), false));

    private static ReposiliteApplication reposilite;

    /**
     * Launches a single {@link ReposiliteApplication}, shared across all {@code @Test} methods in this class that
     * need one, since launching a fresh process/port/working-directory per test is expensive and unnecessary —
     * every publishing test here reuses the same {@code group/app/1.0} coordinate and content, and only asserts
     * idempotent/positive facts about it.
     */
    @BeforeAll
    static void startReposilite()
        throws Exception {

        reposilite = LOCAL_MACHINE.launch(ReposiliteSpecification.create());
        reposilite.onStart().get();
    }

    @AfterAll
    static void stopReposilite() {
        reposilite.close();
    }

    /**
     * Ensure {@link MavenPlugin.CreatePOMFile} registers the created pom.xml with the {@link SignableResource}
     * when one is present for the {@link Project} — demonstrating the self-registration pattern used by each
     * artifact-producing task ({@link MavenPlugin.PackageModule}, {@link MavenPlugin.PackageModuleSource},
     * {@link MavenPlugin.PackageJavaDoc}, and {@link MavenPlugin.CreatePOMFile}), rather than a single
     * centralized registration task.
     */
    @Test
    void shouldRegisterPomForSigningWhenSignableResourcePresent(@TempDir final Path tempDir)
        throws Exception {

        final var signable = new SignableResource();
        final var project = mockProjectWithResources(signable);

        final var context = InjectionFramework.create().newContext();
        context.addResolver(new ProjectResourceResolver(project));

        final var task = context.create(MavenPlugin.CreatePOMFile.class);

        final var pom = task.create(tempDir, minimalDocument());

        assertThat(signable.artifacts().stream().toList())
            .containsExactly(pom);
    }

    /**
     * Ensure {@link MavenPlugin.CreatePOMFile} registers nothing when no {@link SignableResource} is present
     * for the {@link Project}.
     */
    @Test
    void shouldNotRegisterPomForSigningWhenSignableResourceAbsent(@TempDir final Path tempDir)
        throws Exception {

        final var project = mockProjectWithResources();

        final var context = InjectionFramework.create().newContext();
        context.addResolver(new ProjectResourceResolver(project));

        final var task = context.create(MavenPlugin.CreatePOMFile.class);

        task.create(tempDir, minimalDocument());

        // no SignableResource is present, so there's nothing to assert against other than that
        // create() didn't throw attempting to register with one
    }

    private static Document minimalDocument()
        throws Exception {

        final var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        document.appendChild(document.createElement("project"));
        return document;
    }

    private static Project mockProjectWithResources(final Resource... resources) {
        final var project = mock(Project.class);
        when(project.hierarchy()).thenAnswer(invocation -> Stream.of(project));
        when(project.resources()).thenAnswer(invocation -> Stream.of(resources));
        return project;
    }

    /**
     * Ensure {@link MavenPlugin.Publish} throws when no {@code repository.url} is configured.
     */
    @Test
    void shouldThrowWhenRepositoryUrlNotConfigured(@TempDir final Path tempDir)
        throws IOException {

        final var task = createPublishTask(Optional.empty(), Optional.empty(), Optional.empty());

        final var jarFile = Files.createFile(tempDir.resolve("app-1.0.jar"));
        final var pomFile = Files.createFile(tempDir.resolve("pom.xml"));

        assertThat(assertThrows(IllegalStateException.class, () -> task.publish(
            ArtifactDescriptor.create(REFERENCE, artifact(null), jarFile),
            ArtifactDescriptor.create(REFERENCE, artifact("sources"), jarFile),
            ArtifactDescriptor.create(REFERENCE, artifact("javadoc"), jarFile),
            pomFile,
            Optional.empty())))
            .hasMessageContaining("repository.url");
    }

    /**
     * Ensure {@link MavenPlugin.Publish} uploads the jar, sources jar, javadoc jar, and pom.xml (as
     * {@code artifactId-version.pom}, not the local {@code pom.xml} filename) to the correct Maven-repository
     * relative paths, retrievable afterward from the repository.
     */
    @Test
    void shouldPublishArtifactsAndPomToRepository(@TempDir final Path tempDir)
        throws Exception {

        final var task = createPublishTask(
            reposilite, Optional.of(reposilite.username().get()), Optional.of(reposilite.password().get()));

        final var jarFile = Files.writeString(tempDir.resolve("app-1.0.jar"), "jar");
        final var sourcesFile = Files.writeString(tempDir.resolve("app-1.0-sources.jar"), "sources");
        final var javadocFile = Files.writeString(tempDir.resolve("app-1.0-javadoc.jar"), "javadoc");
        final var pomFile = Files.writeString(tempDir.resolve("pom.xml"), "pom");

        final var published = task.publish(
            ArtifactDescriptor.create(REFERENCE, artifact(null), jarFile),
            ArtifactDescriptor.create(REFERENCE, artifact("sources"), sourcesFile),
            ArtifactDescriptor.create(REFERENCE, artifact("javadoc"), javadocFile),
            pomFile,
            Optional.empty());

        assertThat(published.stream().toList())
            .containsExactlyInAnyOrder(jarFile, sourcesFile, javadocFile, pomFile);

        assertThat(reposilite.get("group/app/1.0/app-1.0.jar").body())
            .isEqualTo("jar");
        assertThat(reposilite.get("group/app/1.0/app-1.0-sources.jar").body())
            .isEqualTo("sources");
        assertThat(reposilite.get("group/app/1.0/app-1.0-javadoc.jar").body())
            .isEqualTo("javadoc");
        assertThat(reposilite.get("group/app/1.0/app-1.0.pom").body())
            .isEqualTo("pom");
        assertThat(reposilite.get("group/app/1.0/app-1.0.jar.sha1").statusCode())
            .isEqualTo(200);
        assertThat(reposilite.get("group/app/1.0/app-1.0.pom.sha1").statusCode())
            .isEqualTo(200);
    }

    /**
     * Ensure {@link MavenPlugin.Publish} also uploads any detached signatures present in the {@link Optional}
     * {@code signatures} {@link PathSet}, matched to their corresponding artifact by filename.
     * <p>
     * Uses its own version ({@code 1.1}), distinct from the other publishing tests in this class, since they
     * share a {@link #reposilite} instance and Reposilite rejects re-publishing an already-deployed path
     * (HTTP 409).
     */
    @Test
    void shouldPublishSignaturesWhenPresent(@TempDir final Path tempDir)
        throws Exception {

        final var task = createPublishTask(
            reposilite, Optional.of(reposilite.username().get()), Optional.of(reposilite.password().get()));

        final var jarFile = Files.writeString(tempDir.resolve("app-1.1.jar"), "jar");
        final var sourcesFile = Files.writeString(tempDir.resolve("app-1.1-sources.jar"), "sources");
        final var javadocFile = Files.writeString(tempDir.resolve("app-1.1-javadoc.jar"), "javadoc");
        final var pomFile = Files.writeString(tempDir.resolve("pom.xml"), "pom");

        final var jarSignature = Files.writeString(tempDir.resolve("app-1.1.jar.asc"), "sig");

        final var signatures = Stream.of(jarSignature)
            .collect(PathSet.collector());

        task.publish(
            ArtifactDescriptor.create(REFERENCE, artifact(null, "1.1"), jarFile),
            ArtifactDescriptor.create(REFERENCE, artifact("sources", "1.1"), sourcesFile),
            ArtifactDescriptor.create(REFERENCE, artifact("javadoc", "1.1"), javadocFile),
            pomFile,
            Optional.of(signatures));

        assertThat(reposilite.get("group/app/1.1/app-1.1.jar.asc").body())
            .isEqualTo("sig");
        assertThat(reposilite.get("group/app/1.1/app-1.1.jar.asc.sha1").statusCode())
            .isEqualTo(200);
    }

    /**
     * Ensure {@link MavenPlugin.Publish} succeeds when the configured {@code repository.username}/
     * {@code repository.password} are valid, proving the {@code Authorization} header was correctly sent
     * and accepted (Reposilite rejects unauthenticated/incorrectly-authenticated writes).
     * <p>
     * Uses its own version ({@code 1.2}), distinct from the other publishing tests in this class, since they
     * share a {@link #reposilite} instance and Reposilite rejects re-publishing an already-deployed path
     * (HTTP 409).
     */
    @Test
    void shouldSucceedWhenCredentialsAreValid(@TempDir final Path tempDir)
        throws Exception {

        final var task = createPublishTask(
            reposilite, Optional.of(reposilite.username().get()), Optional.of(reposilite.password().get()));

        final var jarFile = Files.writeString(tempDir.resolve("app-1.2.jar"), "jar");
        final var sourcesFile = Files.writeString(tempDir.resolve("app-1.2-sources.jar"), "sources");
        final var javadocFile = Files.writeString(tempDir.resolve("app-1.2-javadoc.jar"), "javadoc");
        final var pomFile = Files.writeString(tempDir.resolve("pom.xml"), "pom");

        final var published = task.publish(
            ArtifactDescriptor.create(REFERENCE, artifact(null, "1.2"), jarFile),
            ArtifactDescriptor.create(REFERENCE, artifact("sources", "1.2"), sourcesFile),
            ArtifactDescriptor.create(REFERENCE, artifact("javadoc", "1.2"), javadocFile),
            pomFile,
            Optional.empty());

        assertThat(published.isEmpty())
            .isFalse();
    }

    /**
     * Ensure {@link MavenPlugin.Publish} throws when an upload fails (here: rejected due to incorrect
     * credentials).
     */
    @Test
    void shouldThrowWhenUploadFails(@TempDir final Path tempDir)
        throws Exception {

        final var task = createPublishTask(
            reposilite, Optional.of(reposilite.username().get()), Optional.of("wrong-password"));

        final var jarFile = Files.writeString(tempDir.resolve("app-1.0.jar"), "jar");
        final var pomFile = Files.writeString(tempDir.resolve("pom.xml"), "pom");

        assertThrows(IOException.class, () -> task.publish(
            ArtifactDescriptor.create(REFERENCE, artifact(null), jarFile),
            ArtifactDescriptor.create(REFERENCE, artifact("sources"), jarFile),
            ArtifactDescriptor.create(REFERENCE, artifact("javadoc"), jarFile),
            pomFile,
            Optional.empty()));
    }

    private static MavenPlugin.Publish createPublishTask(final ReposiliteApplication reposilite,
                                                         final Optional<String> username,
                                                         final Optional<String> password) {

        return createPublishTask(Optional.of(reposilite.repositoryUrl()), username, password);
    }

    private static MavenPlugin.Publish createPublishTask(final Optional<String> url,
                                                         final Optional<String> username,
                                                         final Optional<String> password) {

        final var recorder = mock(TelemetryRecorder.class);

        final var context = InjectionFramework.create().newContext();
        context.bind(TelemetryRecorder.class).to(recorder);
        context.bind(new TypeLiteral<Optional<String>>() {
            }).as("repository.url").with(Configuration.class)
            .to(url);
        context.bind(new TypeLiteral<Optional<String>>() {
            }).as("repository.username").with(Configuration.class)
            .to(username);
        context.bind(new TypeLiteral<Optional<String>>() {
            }).as("repository.password").with(Configuration.class)
            .to(password);

        return context.create(MavenPlugin.Publish.class);
    }

    private static Artifact artifact(final String classifier) {
        return artifact(classifier, "1.0");
    }

    private static Artifact artifact(final String classifier, final String version) {
        return Artifact.create("group", "app", version, "jar", classifier);
    }

    /**
     * Ensure {@link MavenPlugin.CreateModuleArchiveBuilder} adds a single compile output's classes at the
     * root of the archive when there's nothing else to reconcile it against — the common, non-multi-release
     * case.
     */
    @Test
    void shouldAddSingleCompileOutputAtArchiveRoot(@TempDir final Path tempDir)
        throws Exception {

        final var classes = Files.createDirectories(tempDir.resolve("classes"));
        Files.writeString(classes.resolve("Main.class"), "main");

        final var jar = buildArchive(tempDir, PathSet.of(classes));

        try (var jarFile = new JarFile(jar.toFile())) {
            assertThat(jarFile.stream().map(JarEntry::getName))
                .contains("Main.class");
        }
    }

    /**
     * Ensure {@link MavenPlugin.CreateModuleArchiveBuilder} includes resource files that already sit
     * alongside compiled classes in a reused build-output directory, not just the {@code .class} files.
     * This is the scenario a reused Maven {@code target/classes} produces in practice: {@code mvn}'s own
     * {@code resources:resources} goal already copied resources in there before {@code compiler:compile}
     * ran, in the same directory. Since {@link MavenPlugin.CreateModuleArchiveBuilder} now packages
     * exactly what {@link build.spin.module.java.AbstractCompile}'s {@code Compile} task returns (see
     * {@link build.spin.option.ReuseExternalBuildOutput}) rather than unconditionally packaging spin's
     * own conventional build directory, whatever else lives in a reused candidate directory must still
     * make it into the archive.
     */
    @Test
    void shouldIncludeResourcesAlreadyPresentInReusedOutputDirectory(@TempDir final Path tempDir)
        throws Exception {

        final var reusedOutput = Files.createDirectories(tempDir.resolve("target/classes"));
        Files.writeString(reusedOutput.resolve("Main.class"), "main");
        Files.writeString(reusedOutput.resolve("application.properties"), "key=value");

        final var jar = buildArchive(tempDir, PathSet.of(reusedOutput));

        try (var jarFile = new JarFile(jar.toFile())) {
            assertThat(jarFile.stream().map(JarEntry::getName))
                .contains("Main.class", "application.properties");
        }
    }

    /**
     * Ensure {@link MavenPlugin.CreateModuleArchiveBuilder} does not duplicate a multi-release variant's
     * output when it's physically nested inside another compile output already being added — as it is when
     * {@link build.spin.module.java.AbstractCompile} moves a non-default-version variant's classes into the
     * default variant's own {@code META-INF/versions/N} subdirectory. Adding both paths independently (the
     * pre-fix behavior) would add every class under {@code META-INF/versions/25} twice, which throws once
     * the underlying {@code JarOutputStream} rejects the resulting duplicate entry name.
     */
    @Test
    void shouldNotDuplicateNestedMultiReleaseVariantOutput(@TempDir final Path tempDir)
        throws Exception {

        final var root = Files.createDirectories(tempDir.resolve("classes"));
        Files.writeString(root.resolve("Main.class"), "main");

        final var versioned = Files.createDirectories(root.resolve("META-INF/versions/25"));
        Files.writeString(versioned.resolve("Main.class"), "main-25");

        final var jar = buildArchive(tempDir, PathSet.of(root), PathSet.of(versioned));

        try (var jarFile = new JarFile(jar.toFile())) {
            final var names = jarFile.stream().map(JarEntry::getName).toList();

            assertThat(names)
                .contains("Main.class", "META-INF/versions/25/Main.class")
                .filteredOn("META-INF/versions/25/Main.class"::equals)
                .hasSize(1);
        }
    }

    /**
     * Ensure {@link MavenPlugin.CreateModuleArchiveBuilder} places a multi-release variant's output under its
     * explicit {@code META-INF/versions/N/} path in the archive, rather than flattening it to the archive
     * root, when that output isn't nested inside another compile output being added — as happens when the
     * default variant reused an external tool's build output (e.g. Maven's own {@code target/classes}) that
     * has no {@code META-INF/versions} tree of its own.
     */
    @Test
    void shouldNamespaceUnnestedMultiReleaseVariantOutput(@TempDir final Path tempDir)
        throws Exception {

        final var mavenClasses = Files.createDirectories(tempDir.resolve("maven-target/classes"));
        Files.writeString(mavenClasses.resolve("Main.class"), "main");

        final var versioned = Files.createDirectories(tempDir.resolve("spin-build/META-INF/versions/25"));
        Files.writeString(versioned.resolve("Main.class"), "main-25");

        final var jar = buildArchive(tempDir, PathSet.of(mavenClasses), PathSet.of(versioned));

        try (var jarFile = new JarFile(jar.toFile())) {
            final var names = jarFile.stream().map(JarEntry::getName).toList();

            assertThat(names)
                .contains("Main.class", "META-INF/versions/25/Main.class")
                .doesNotContain("25/Main.class");
        }
    }

    private static Path buildArchive(final Path tempDir, final PathSet... pathSets)
        throws Exception {

        final var task = new MavenPlugin.CreateModuleArchiveBuilder();
        final var builder = task.create(new Manifest(), Stream.of(pathSets));

        final var archive = tempDir.resolve("archive-" + System.nanoTime() + ".jar");
        builder.build(archive);
        return archive;
    }
}
