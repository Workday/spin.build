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

import build.spawn.platform.local.LocalMachine;
import build.spin.common.telemetry.TelemetryPublisher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MavenPublisher}, using a real {@link ReposiliteApplication} Maven repository shared across all
 * tests in this class (see {@link #startReposilite()}/{@link #stopReposilite()}).
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class MavenPublisherTests {

    private static final LocalMachine LOCAL_MACHINE = new LocalMachine(uri ->
        TelemetryPublisher.of(uri, event -> System.err.printf("%s%n", event), false));

    private static ReposiliteApplication reposilite;

    /**
     * Launches a single {@link ReposiliteApplication}, shared across all {@code @Test} methods in this class,
     * since launching a fresh process/port/working-directory per test is expensive and unnecessary here — each
     * test either uses its own distinct artifact coordinate or only asserts idempotent/positive facts about a
     * shared one.
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
     * Ensure {@link MavenPublisher#upload(String, Path)} uploads the file, then its computed {@code .sha1}
     * sidecar, both retrievable afterward from the repository.
     */
    @Test
    void shouldUploadFileAndSha1Sidecar(@TempDir final Path tempDir)
        throws Exception {

        final var file = tempDir.resolve("artifact-1.0.jar");
        Files.writeString(file, "jar-bytes");

        final var publisher = new MavenPublisher(
            new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
            RemoteRepo.of("test", reposilite.repositoryUrl(),
                reposilite.username().get(), reposilite.password().get()));

        final var success = publisher.upload("test/artifact/1.0/artifact-1.0.jar", file);

        assertThat(success)
            .isTrue();

        assertThat(reposilite.get("test/artifact/1.0/artifact-1.0.jar").body())
            .isEqualTo("jar-bytes");

        final var expectedSha1 = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-1").digest("jar-bytes".getBytes(StandardCharsets.UTF_8)));

        assertThat(reposilite.get("test/artifact/1.0/artifact-1.0.jar.sha1").body())
            .isEqualTo(expectedSha1);
    }

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} succeeds when the {@link RemoteRepo} carries valid
     * credentials, proving the {@code Authorization} header was correctly sent and accepted (Reposilite
     * rejects unauthenticated/incorrectly-authenticated writes to a repository).
     * <p>
     * Uses its own artifact coordinate, distinct from {@link #shouldUploadFileAndSha1Sidecar(Path)}, since they
     * share a {@link #reposilite} instance and Reposilite rejects re-publishing an already-deployed path
     * (HTTP 409).
     */
    @Test
    void shouldSucceedWhenCredentialsAreValid(@TempDir final Path tempDir)
        throws Exception {

        final var file = tempDir.resolve("artifact-1.1.jar");
        Files.writeString(file, "jar-bytes");

        final var publisher = new MavenPublisher(
            new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
            RemoteRepo.of("test", reposilite.repositoryUrl(),
                reposilite.username().get(), reposilite.password().get()));

        final var success = publisher.upload("test/artifact/1.1/artifact-1.1.jar", file);

        assertThat(success)
            .isTrue();
    }

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} returns {@code false}, and never uploads the
     * {@code .sha1} sidecar, when the file upload itself fails (here: rejected due to incorrect
     * credentials).
     * <p>
     * Uses its own artifact coordinate, distinct from the other tests in this class, since they publish
     * successfully (including the {@code .sha1} sidecar) against a shared {@link #reposilite} instance — reusing
     * their coordinate here would make the "sidecar absent" assertion below order-dependent.
     */
    @Test
    void shouldReturnFalseAndSkipSha1WhenUploadFails(@TempDir final Path tempDir)
        throws Exception {

        final var file = tempDir.resolve("artifact-1.0.jar");
        Files.writeString(file, "jar-bytes");

        final var publisher = new MavenPublisher(
            new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
            RemoteRepo.of("test", reposilite.repositoryUrl(), reposilite.username().get(), "wrong-password"));

        final var success = publisher.upload("test/failed-upload/1.0/failed-upload-1.0.jar", file);

        assertThat(success)
            .isFalse();

        assertThat(reposilite.get("test/failed-upload/1.0/failed-upload-1.0.jar.sha1").statusCode())
            .isEqualTo(404);
    }
}
