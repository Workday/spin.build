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

import build.base.configuration.Configuration;
import build.base.configuration.ConfigurationBuilder;
import build.base.network.Server;
import build.base.network.option.Port;
import build.base.option.HostName;
import build.base.option.Password;
import build.base.option.Username;
import build.base.option.WorkingDirectory;
import build.spawn.application.Platform;
import build.spawn.application.Process;
import build.spawn.application.option.StandardOutputSubscriber;
import build.spawn.jdk.JDKApplication;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * A real, pinned-version <a href="https://reposilite.com/">Reposilite</a> Maven repository server, launched as a
 * {@link JDKApplication} via {@link ReposiliteSpecification}, providing a genuine repository to publish to rather
 * than a hand-rolled HTTP stub.
 * <p>
 * The launched instance has a single management token ({@link #username()}/{@link #password()}, HTTP Basic auth)
 * and the default {@code releases} repository (public reads, authenticated writes) at {@link #repositoryUrl()}.
 * <p>
 * {@link #onStart()} does not complete until Reposilite has logged its own startup-completion marker, rather than
 * merely once the JVM has started and the Spawn agent has connected back — the HTTP listener accepts connections
 * slightly before the auth/repository route handlers are fully wired, so an early successful connection isn't a
 * reliable readiness signal. This is contributed by {@link Customizer}, which — like {@link JDKApplication.Customizer}
 * — is discovered and instantiated fresh for every launch, so a {@link ReposiliteSpecification} may safely be reused
 * to launch more than one {@link ReposiliteApplication}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
public interface ReposiliteApplication
    extends JDKApplication {

    /**
     * Obtains the {@link HostName} on which the Reposilite instance is listening.
     * <p>
     * A convenience for {@code configuration().get(HostName.class)}.
     *
     * @return the {@link HostName}
     */
    HostName hostname();

    /**
     * Obtains the {@link Port} on which the Reposilite instance is listening.
     * <p>
     * A convenience for {@code configuration().get(Port.class)}.
     *
     * @return the {@link Port}
     */
    Port port();

    /**
     * Obtains the {@link Username} of the management token created for this instance.
     * <p>
     * A convenience for {@code configuration().get(Username.class)}.
     *
     * @return the {@link Username}
     */
    Username username();

    /**
     * Obtains the {@link Password} of the management token created for this instance.
     * <p>
     * A convenience for {@code configuration().get(Password.class)}.
     *
     * @return the {@link Password}
     */
    Password password();

    /**
     * Obtains the base URL of the default {@code releases} repository.
     *
     * @return the repository URL
     */
    default String repositoryUrl() {
        return "http://" + hostname().get() + ":" + port().get() + "/releases";
    }

    /**
     * Fetches the content of the specified path within the {@code releases} repository (an unauthenticated GET,
     * since the repository allows public reads), for verifying an upload.
     *
     * @param relativePath the Maven-repository-layout relative path (including filename)
     * @return the {@link HttpResponse} of {@link String} body
     * @throws IOException          should the HTTP request fail
     * @throws InterruptedException should the HTTP request be interrupted
     */
    default HttpResponse<String> get(final String relativePath)
        throws IOException, InterruptedException {

        final var request = HttpRequest.newBuilder(URI.create(repositoryUrl() + "/" + relativePath)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * The {@link build.spawn.application.Customizer} that delays {@link ReposiliteApplication#onStart()} until
     * Reposilite has logged its own startup-completion marker.
     * <p>
     * A fresh instance is created (via dependency injection) for every launch — see
     * {@code AbstractTemplatedPlatform#launch(Specification)} — so its {@link #ready} future is never shared
     * across launches of a reused {@link ReposiliteSpecification}.
     */
    class Customizer
        implements build.spawn.application.Customizer<ReposiliteApplication> {

        /**
         * Completed once Reposilite has logged its own startup-completion marker; consulted by
         * {@link #onStart(Platform, Class, ReposiliteApplication)} to delay readiness until then.
         */
        private final CompletableFuture<Void> ready;

        /**
         * Constructs a {@link Customizer}, establishing a fresh, incomplete {@link #ready} future for this
         * launch.
         */
        Customizer() {
            this.ready = new CompletableFuture<>();
        }

        @Override
        public void onPreparing(final Platform platform,
                                final Class<? extends ReposiliteApplication> applicationClass,
                                final ConfigurationBuilder configurationBuilder) {

            configurationBuilder.add(StandardOutputSubscriber.of(line -> {
                // Reposilite's own startup-completion marker — more reliable than probing the port, since
                // the HTTP listener accepts connections slightly before the auth/repository route handlers
                // are fully wired
                if (line.contains("Done (")) {
                    this.ready.complete(null);
                }
            }));
        }

        @Override
        public CompletableFuture<? extends ReposiliteApplication> onStart(final Platform platform,
                                                                          final Class<? extends ReposiliteApplication> applicationClass,
                                                                          final ReposiliteApplication application) {

            return this.ready.thenApply(__ -> application);
        }
    }

    /**
     * An implementation of a {@link ReposiliteApplication}.
     */
    class Implementation
        extends JDKApplication.Implementation
        implements ReposiliteApplication {

        /**
         * Constructs a {@link ReposiliteApplication.Implementation}.
         *
         * @param platform         the {@link Platform}
         * @param process          the {@link Process}
         * @param applicationClass the {@link Class} of {@link JDKApplication}
         * @param configuration    the {@link Configuration}
         * @param server           the {@link Server} to which this {@link JDKApplication} will connect
         */
        @Inject
        public Implementation(final Platform platform,
                              final Process process,
                              final Class<? extends JDKApplication> applicationClass,
                              final Configuration configuration,
                              final Server server) {

            super(platform, process, applicationClass, configuration, server);

            // the isolated temporary directory passed to Reposilite as both its --working-directory and OS
            // process working directory; recursively delete it once the process exits
            final var workingDirectory = configuration.get(WorkingDirectory.class).path();
            process().onExit()
                .thenRun(() -> deleteRecursively(workingDirectory));
        }

        @Override
        public HostName hostname() {
            return configuration().get(HostName.class);
        }

        @Override
        public Port port() {
            return configuration().get(Port.class);
        }

        @Override
        public Username username() {
            return configuration().get(Username.class);
        }

        @Override
        public Password password() {
            return configuration().get(Password.class);
        }

        private static void deleteRecursively(final Path path) {
            try (Stream<Path> paths = Files.walk(path)) {
                paths.sorted(Comparator.reverseOrder())
                    .forEach(ReposiliteApplication.Implementation::deleteQuietly);
            } catch (final IOException ignored) {
                // best-effort cleanup
            }
        }

        private static void deleteQuietly(final Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (final IOException ignored) {
                // best-effort cleanup
            }
        }
    }
}
