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

import build.spin.common.telemetry.TelemetryPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MavenPublisher}, using a small synthetic PUT-accepting HTTP server fixture.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class MavenPublisherTests {

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} uploads the file, then its computed {@code .sha1}
     * sidecar, to the correct relative paths.
     */
    @Test
    void shouldUploadFileAndSha1Sidecar(@TempDir final Path tempDir)
        throws Exception {

        try (var server = new PutServerFixture(200)) {
            final var file = tempDir.resolve("artifact-1.0.jar");
            Files.writeString(file, "jar-bytes");

            final var publisher = new MavenPublisher(
                new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
                RemoteRepo.of("test", "http://localhost:" + server.port()));

            final var success = publisher.upload("test/artifact/1.0/artifact-1.0.jar", file);

            assertThat(success)
                .isTrue();

            assertThat(server.requests())
                .extracting(PutServerFixture.Request::path)
                .containsExactly(
                    "/test/artifact/1.0/artifact-1.0.jar",
                    "/test/artifact/1.0/artifact-1.0.jar.sha1");

            final var expectedSha1 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest("jar-bytes".getBytes(StandardCharsets.UTF_8)));

            assertThat(server.requests().get(1).body())
                .isEqualTo(expectedSha1);
        }
    }

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} includes an {@code Authorization} header, Base64
     * encoding the configured username/password as HTTP Basic auth, when the {@link RemoteRepo} carries
     * credentials.
     */
    @Test
    void shouldIncludeAuthorizationHeaderWhenConfigured(@TempDir final Path tempDir)
        throws Exception {

        try (var server = new PutServerFixture(200)) {
            final var file = tempDir.resolve("artifact-1.0.jar");
            Files.writeString(file, "jar-bytes");

            final var publisher = new MavenPublisher(
                new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
                RemoteRepo.of("test", "http://localhost:" + server.port(), "alice", "secret"));

            publisher.upload("test/artifact/1.0/artifact-1.0.jar", file);

            final var expectedHeader = "Basic " + Base64.getEncoder().encodeToString("alice:secret".getBytes());

            assertThat(server.requests())
                .extracting(PutServerFixture.Request::authorization)
                .containsOnly(Optional.of(expectedHeader));
        }
    }

    /**
     * Ensure {@link MavenPublisher#upload(String, Path)} returns {@code false}, and never attempts to upload
     * the {@code .sha1} sidecar, when the file upload itself fails.
     */
    @Test
    void shouldReturnFalseAndSkipSha1WhenUploadFails(@TempDir final Path tempDir)
        throws Exception {

        try (var server = new PutServerFixture(500)) {
            final var file = tempDir.resolve("artifact-1.0.jar");
            Files.writeString(file, "jar-bytes");

            final var publisher = new MavenPublisher(
                new TelemetryPublisher(URI.create("maven://publish-test"), System.out::println),
                RemoteRepo.of("test", "http://localhost:" + server.port()));

            final var success = publisher.upload("test/artifact/1.0/artifact-1.0.jar", file);

            assertThat(success)
                .isFalse();

            assertThat(server.requests())
                .hasSize(1);
        }
    }
}
