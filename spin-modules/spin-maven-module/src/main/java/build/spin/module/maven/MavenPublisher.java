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

import build.base.telemetry.TelemetryRecorder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A pure-JDK Maven artifact publisher: uploads a {@link Path} (and a computed {@code .sha1} checksum sidecar)
 * to a {@link RemoteRepo} via {@link HttpClient} {@code PUT}, mirroring the download mechanics of
 * {@link PomResolver}.
 */
class MavenPublisher {

    private final TelemetryRecorder recorder;
    private final RemoteRepo repo;
    private final HttpClient httpClient;

    MavenPublisher(final TelemetryRecorder recorder,
                   final RemoteRepo repo) {

        this.recorder = recorder;
        this.repo = repo;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Uploads the specified {@link Path} to the specified Maven-repository-layout {@code relativePath}
     * (eg: {@code group/path/artifactId/version/filename}) with in the {@link RemoteRepo}, then uploads a
     * computed {@code .sha1} checksum sidecar for it.
     *
     * @param relativePath the Maven-repository-layout relative path (including filename) for the upload
     * @param file         the {@link Path} of the file to upload
     * @return {@code true} if both the file and its {@code .sha1} sidecar were uploaded successfully,
     *         {@code false} otherwise
     * @throws IOException          should reading the file or performing the HTTP request(s) fail
     * @throws InterruptedException should the HTTP request(s) be interrupted
     */
    boolean upload(final String relativePath, final Path file)
        throws IOException, InterruptedException {

        final var url = this.repo.url().endsWith("/")
            ? this.repo.url() + relativePath
            : this.repo.url() + "/" + relativePath;

        if (!put(url, HttpRequest.BodyPublishers.ofFile(file))) {
            return false;
        }

        final var sha1 = sha1Hex(file);
        return put(url + ".sha1", HttpRequest.BodyPublishers.ofString(sha1, StandardCharsets.US_ASCII));
    }

    private boolean put(final String url, final HttpRequest.BodyPublisher bodyPublisher)
        throws IOException, InterruptedException {

        final var requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .PUT(bodyPublisher);

        this.repo.authHeader().ifPresent(h -> requestBuilder.header("Authorization", h));

        final var response = this.httpClient.send(
            requestBuilder.build(), HttpResponse.BodyHandlers.discarding());

        final var success = response.statusCode() >= 200 && response.statusCode() < 300;
        if (!success) {
            this.recorder.warn("HTTP %d publishing to %s", response.statusCode(), url);
        }
        return success;
    }

    private static String sha1Hex(final Path file)
        throws IOException {

        try {
            final var digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("The SHA-1 MessageDigest algorithm is not available", e);
        }
    }
}
