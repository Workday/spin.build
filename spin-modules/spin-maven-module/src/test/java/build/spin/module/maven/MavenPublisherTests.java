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
 * Tests for {@link MavenPublisher}, using a real {@link ReposiliteApplication} Maven repository.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class MavenPublisherTests {

    private static final LocalMachine LOCAL_MACHINE = new LocalMachine(uri ->
        TelemetryPublisher.of(uri, event -> System.err.printf("%s%n", event), false));

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} uploads the file, then its computed {@code .sha1}
     * sidecar, both retrievable afterward from the repository.
     */
    @Test
    void shouldUploadFileAndSha1Sidecar(@TempDir final Path tempDir)
        throws Exception {

        try (var reposilite = LOCAL_MACHINE.launch(ReposiliteSpecification.create())) {
            reposilite.onStart().get();

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
    }

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} succeeds when the {@link RemoteRepo} carries valid
     * credentials, proving the {@code Authorization} header was correctly sent and accepted (Reposilite
     * rejects unauthenticated/incorrectly-authenticated writes to a repository).
     */
    @Test
    void shouldSucceedWhenCredentialsAreValid(@TempDir final Path tempDir)
        throws Exception {

        try (var reposilite = LOCAL_MACHINE.launch(ReposiliteSpecification.create())) {
            reposilite.onStart().get();

            final var file = tempDir.resolve("artifact-1.0.jar");
            Files.writeString(file, "jar-bytes");

            final var publisher = new MavenPublisher(
                new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
                RemoteRepo.of("test", reposilite.repositoryUrl(),
                    reposilite.username().get(), reposilite.password().get()));

            final var success = publisher.upload("test/artifact/1.0/artifact-1.0.jar", file);

            assertThat(success)
                .isTrue();
        }
    }

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} returns {@code false}, and never uploads the
     * {@code .sha1} sidecar, when the file upload itself fails (here: rejected due to incorrect
     * credentials).
     */
    @Test
    void shouldReturnFalseAndSkipSha1WhenUploadFails(@TempDir final Path tempDir)
        throws Exception {

        try (var reposilite = LOCAL_MACHINE.launch(ReposiliteSpecification.create())) {
            reposilite.onStart().get();

            final var file = tempDir.resolve("artifact-1.0.jar");
            Files.writeString(file, "jar-bytes");

            final var publisher = new MavenPublisher(
                new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
                RemoteRepo.of("test", reposilite.repositoryUrl(), reposilite.username().get(), "wrong-password"));

            final var success = publisher.upload("test/artifact/1.0/artifact-1.0.jar", file);

            assertThat(success)
                .isFalse();

            assertThat(reposilite.get("test/artifact/1.0/artifact-1.0.jar.sha1").statusCode())
                .isEqualTo(404);
        }
    }
}
