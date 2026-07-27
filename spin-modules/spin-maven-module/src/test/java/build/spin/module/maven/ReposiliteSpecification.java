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

import build.base.network.option.Port;
import build.base.option.HostName;
import build.base.option.Password;
import build.base.option.Username;
import build.base.option.WorkingDirectory;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Name;
import build.spawn.jdk.AbstractJDKSpecification;
import build.spawn.jdk.option.Jar;
import build.spawn.platform.local.LocalMachine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link build.spawn.application.Specification} for launching a real, pinned-version
 * <a href="https://reposilite.com/">Reposilite</a> Maven repository server as a {@link ReposiliteApplication} on a
 * {@link LocalMachine}, downloading (once, cached under {@code ~/.m2/repository}) the standalone Reposilite jar as
 * needed.
 * <p>
 * This {@link build.spawn.application.Specification} may safely be reused to launch more than one
 * {@link ReposiliteApplication}: the startup-readiness detection lives entirely in
 * {@link ReposiliteApplication.Customizer}, which is discovered and instantiated fresh for every launch rather than
 * being carried as state on this {@link build.spawn.application.Specification}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
final class ReposiliteSpecification
    extends AbstractJDKSpecification<ReposiliteApplication, ReposiliteSpecification> {

    private static final String VERSION = "3.5.28";

    private static final String DOWNLOAD_URL =
        "https://maven.reposilite.com/releases/com/reposilite/reposilite/" + VERSION
            + "/reposilite-" + VERSION + "-all.jar";

    private static final String DEFAULT_USERNAME = "test";

    private static final String DEFAULT_PASSWORD = "test-secret";

    private ReposiliteSpecification(final Path workingDirectory,
                                    final int port,
                                    final String username,
                                    final String password)
        throws IOException, InterruptedException {

        with(Name.of("Reposilite"));
        with(HostName.localhost());
        with(Port.of(port));
        with(Username.of(username));
        with(Password.of(password));
        // Reposilite writes its own logs/ and latest.log relative to the process's OS working
        // directory (independent of --working-directory, which only controls its data directory) —
        // pin it to the same isolated temp directory so nothing leaks into the caller's cwd
        with(WorkingDirectory.of(workingDirectory.toString()));
        with(Jar.of(ensureJarDownloaded()));
        with(Argument.of("--working-directory"));
        with(Argument.of(workingDirectory.toString()));
        with(Argument.of("--port"));
        with(Argument.of(String.valueOf(port)));
        with(Argument.of("--token"));
        with(Argument.of(username + ":" + password));
    }

    /**
     * Creates a {@link ReposiliteSpecification} for a fresh, isolated Reposilite instance: a new temporary
     * working directory, a free port leased from the {@link LocalMachine}, and a fixed management token.
     *
     * @return a new {@link ReposiliteSpecification}
     * @throws IOException          should the working directory or Reposilite jar fail to be created/downloaded
     * @throws InterruptedException should downloading the Reposilite jar be interrupted
     */
    static ReposiliteSpecification create()
        throws IOException, InterruptedException {

        final var workingDirectory = Files.createTempDirectory("reposilite-test");
        final var port = LocalMachine.get().ports().getAsInt();

        return new ReposiliteSpecification(workingDirectory, port, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    @Override
    public Class<? extends ReposiliteApplication> getApplicationClass() {
        return ReposiliteApplication.class;
    }

    private static Path ensureJarDownloaded()
        throws IOException, InterruptedException {

        final var cacheDirectory = Path.of(System.getProperty("user.home"),
            ".m2", "repository", "com", "reposilite", "reposilite", VERSION);
        final var jar = cacheDirectory.resolve("reposilite-" + VERSION + "-all.jar");

        if (Files.exists(jar)) {
            return jar;
        }

        Files.createDirectories(cacheDirectory);

        final var client = HttpClient.newHttpClient();
        final var request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL)).GET().build();
        final var response = client.send(request, HttpResponse.BodyHandlers.ofFile(jar));

        if (response.statusCode() != 200) {
            Files.deleteIfExists(jar);
            throw new IOException(
                "Failed to download Reposilite " + VERSION + ": HTTP " + response.statusCode());
        }

        return jar;
    }
}
