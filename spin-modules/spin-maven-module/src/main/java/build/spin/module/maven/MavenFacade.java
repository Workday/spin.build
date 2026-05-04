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
import build.base.foundation.Exceptional;
import build.base.telemetry.TelemetryRecorder;
import build.spin.module.modulesystem.UnresolvableResourceException;
import build.spin.option.NetworkAccess;
import org.apache.maven.api.settings.Server;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.settings.v4.SettingsStaxReader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.stream.XMLStreamException;

import static build.spin.option.NetworkAccess.ONLINE;

/**
 * A facade to simplify programmatic access to and interaction with Apache Maven repositories and tooling.
 * <p>
 * Inspired by the invaluable
 * <a href="https://github.com/apache/maven-resolver/blob/master/maven-resolver-demos/maven-resolver-demo-snippets">examples and demo snippets</a>
 * provided by the Apache Maven team.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
class MavenFacade {

    /**
     * The {@link TelemetryRecorder}.
     */
    private final TelemetryRecorder recorder;

    /**
     * The {@link RepositorySystem}.
     */
    private final RepositorySystem repositorySystem;

    /**
     * The {@link RepositorySystemSession}.
     */
    private final RepositorySystemSession repositorySystemSession;

    /**
     * The {@link RemoteRepository}s.
     */
    private final List<RemoteRepository> remoteRepositories;

    /**
     * Constructs a {@link MavenFacade}.
     *
     * @param recorder the {@link TelemetryRecorder}
     * @param optionsByType the {@link Configuration} to configure the {@link MavenFacade}
     */
    MavenFacade(final TelemetryRecorder recorder,
                final Configuration optionsByType) {

        this.recorder = recorder;

        final Path homePath = FileSystems.getDefault().getPath(System.getProperty("user.home"));
        final Path mavenPath = homePath.resolve(".m2");
        final Path settingsPath = mavenPath.resolve("settings.xml");
        final Path localRepositoryPath = mavenPath.resolve("repository");

        // establish the Repository System (auto-discovers connectors and transports via ServiceLoader)
        this.repositorySystem = new RepositorySystemSupplier().get();

        // establish the Repository System Session
        // SessionBuilderSupplier.configureSessionBuilder already propagates System.getProperties() and env vars
        final RepositorySystemSession.SessionBuilder sessionBuilder =
            new SessionBuilderSupplier(this.repositorySystem).get();

        // configure network access
        sessionBuilder.setOffline(optionsByType.getOptional(NetworkAccess.class).orElse(ONLINE) == NetworkAccess.OFFLINE);

        sessionBuilder.withLocalRepositories(new LocalRepository(localRepositoryPath));

        this.repositorySystemSession = sessionBuilder.build();

        // establish the RemoteRepositories
        this.remoteRepositories = new ArrayList<>();

        try {
            // determine the Apache Maven Settings
            final Settings settings;
            if (Files.exists(settingsPath)) {
                try (var in = Files.newInputStream(settingsPath)) {
                    settings = new SettingsStaxReader().read(in);
                }
            } else {
                settings = Settings.newBuilder().build();
            }

            // obtain the remote repositories from the active profiles in the settings
            settings.getActiveProfiles().stream()
                .flatMap(name -> settings.getProfiles().stream()
                    .filter(profile -> name.equals(profile.getId())))
                .flatMap(profile -> profile.getRepositories().stream())
                .map(repository -> {
                    final RemoteRepository.Builder builder =
                        new RemoteRepository.Builder(repository.getId(), "default", repository.getUrl());

                    // establish the authentication for the repository server
                    final String serverId = repository.getId();
                    final Server server = settings.getServers().stream()
                        .filter(s -> serverId.equals(s.getId()))
                        .findFirst()
                        .orElse(null);

                    if (server != null) {
                        final AuthenticationBuilder authenticationBuilder = new AuthenticationBuilder();
                        authenticationBuilder.addUsername(server.getUsername());
                        authenticationBuilder.addPassword(server.getPassword());
                        builder.setAuthentication(authenticationBuilder.build());
                    }
                    else {
                        this.recorder.warn("Failed to locate specified Service [%s].  Will not use authentication.",
                            serverId);
                    }

                    return builder;
                })
                .map(RemoteRepository.Builder::build)
                .forEach(this.remoteRepositories::add);

            // when no repositories are configured from settings, fall back to Maven Central
            if (this.remoteRepositories.isEmpty()) {
                this.remoteRepositories.add(
                    new RemoteRepository.Builder(
                        "central",
                        "default",
                        "https://repo.maven.apache.org/maven2/").build());
            }
        }
        catch (final IOException | XMLStreamException e) {
            this.recorder.warn(e,
                "Failed to establish settings for Maven.  Defaulting to Maven Central to resolve artifacts");

            this.remoteRepositories.add(
                new RemoteRepository.Builder(
                    "central",
                    "default",
                    "https://repo.maven.apache.org/maven2/").build());
        }
    }

    /**
     * Attempts to resolve an {@link ArtifactResult} for an {@link Artifact} with the specified coordinates.
     *
     * @param coordinates the group:artifact:classifier:type:version coordinates
     * @return an {@link Exceptional} {@link ArtifactResult}
     */
    public synchronized Exceptional<ArtifactResult> resolveArtifact(final String coordinates) {
        try {
            final Artifact artifact = new DefaultArtifact(coordinates);

            final ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(artifact);
            artifactRequest.setRepositories(this.remoteRepositories);

            final ArtifactResult artifactResult = this.repositorySystem
                .resolveArtifact(this.repositorySystemSession, artifactRequest);

            if (!artifactResult.isResolved()) {
                this.recorder.warn("Failed to resolve %s", artifact);
            }

            return Exceptional.of(artifactResult);
        }
        catch (final ArtifactResolutionException e) {
            this.recorder.error(e, "Failed to resolve %s", coordinates);
            return Exceptional.ofException(new UnresolvableResourceException(coordinates, e));
        }
        catch (final RuntimeException e) {
            this.recorder.error(e, "Unexpected error resolving %s", coordinates);
            return Exceptional.ofException(new UnresolvableResourceException(coordinates, e));
        }
    }

    /**
     * Resolves the specified artifact and all of its compile-scope transitive dependencies,
     * returning the full list of local {@link Path}s via Aether's native dependency resolution.
     * Aether handles artifact-graph cycles internally, so no manual BFS is needed.
     *
     * @param coordinates the group:artifact:classifier:type:version coordinates
     * @return an {@link Exceptional} list of resolved {@link Path}s (root + all transitive deps)
     */
    public synchronized Exceptional<List<Path>> resolveTransitiveDependencies(final String coordinates) {
        try {
            final org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(coordinates);
            final CollectRequest collectRequest = new CollectRequest(
                new Dependency(artifact, "compile"),
                this.remoteRepositories);
            final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
            final DependencyResult result = this.repositorySystem
                .resolveDependencies(this.repositorySystemSession, dependencyRequest);

            final List<Path> paths = result.getArtifactResults().stream()
                .filter(ArtifactResult::isResolved)
                .map(r -> r.getArtifact().getPath())
                .filter(Objects::nonNull)
                .toList();

            return Exceptional.of(paths);
        }
        catch (final DependencyResolutionException e) {
            this.recorder.error(e, "Failed to resolve transitive dependencies for %s", coordinates);
            return Exceptional.ofException(new UnresolvableResourceException(coordinates, e));
        }
        catch (final RuntimeException e) {
            this.recorder.error(e, "Unexpected error resolving transitive dependencies for %s", coordinates);
            return Exceptional.ofException(new UnresolvableResourceException(coordinates, e));
        }
    }

    /**
     * Attempts to resolve the {@link ArtifactDescriptorResult} for an {@link Artifact} with the specified coordinates.
     *
     * @param coordinates the group:artifact:classifier:type:version coordinates
     * @return an {@link Exceptional} {@link ArtifactDescriptorResult}
     */
    public synchronized Exceptional<ArtifactDescriptorResult> resolveArtifactDescriptor(final String coordinates) {
        try {
            final Artifact artifact = new DefaultArtifact(coordinates);

            final ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
            descriptorRequest.setArtifact(artifact);
            descriptorRequest.setRepositories(this.remoteRepositories);

            final ArtifactDescriptorResult descriptorResult = this.repositorySystem
                .readArtifactDescriptor(this.repositorySystemSession, descriptorRequest);

            if (!descriptorResult.getExceptions().isEmpty()) {
                this.recorder.warn("Failed to resolve descriptor for %s", artifact);
            }

            return Exceptional.of(descriptorResult);
        }
        catch (final ArtifactDescriptorException e) {
            this.recorder.error(e, "Failed to resolve descriptor for %s", coordinates);
            return Exceptional.ofException(new UnresolvableResourceException(coordinates, e));
        }
        catch (final RuntimeException e) {
            this.recorder.error(e, "Unexpected error resolving descriptor for %s", coordinates);
            return Exceptional.ofException(new UnresolvableResourceException(coordinates, e));
        }
    }
}
