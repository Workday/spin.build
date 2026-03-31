package build.spin.module.maven;

import build.base.configuration.Configuration;
import build.spin.common.telemetry.TelemetryPublisher;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the native Maven Resolvers for a variety of Artifacts.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public class NativeMavenResolverTests {

    /**
     * The {@link MavenFacade} to interact with Maven-based repositories.
     */
    private MavenFacade maven;

    /**
     * Establish the {@link RepositorySystem} for each test
     */
    @BeforeEach
    public void onBeforeEach() {
        this.maven = new MavenFacade(
            new TelemetryPublisher(URI.create("maven://native"), System.out::println),
            Configuration.empty());
    }

    /**
     * Ensure the Maven Resolver can be resolved.
     */
    @Test
    void shouldResolveMavenResolverArtifact() {
        this.maven.resolveArtifact("org.apache.maven.resolver:maven-resolver-util:1.3.3")
            .orElseThrow(() -> new AssertionError("Failed to resolve artifact [org.apache.maven.resolver:maven-resolver-util:1.3.3]"));
    }

    /**
     * Ensure a collection of sometimes problematic artifacts can be resolved.
     */
    @Test
    void shouldResolveArtifacts() {
        final ArrayList<String> coordinates = new ArrayList<>();
        coordinates.add("net.bytebuddy:byte-buddy:1.10.19");
        coordinates.add("org.mockito:mockito-core:jar:3.7.7");
        coordinates.add("org.apache.maven:maven-core:jar:3.8.6");
        coordinates.add("org.slf4j:slf4j-api:1.7.28");
//        coordinates.add("build.base:base-foundation:0.19.0");

        final long failures = coordinates.stream()
            .map(this.maven::resolveArtifact)
            .filter(exceptional -> exceptional.isException()
                || exceptional.map(ArtifactResult::isMissing)
                .orElse(false))
            .count();

        if (failures > 0) {
            fail(String.format("Failed to resolve %d artifact(s)", failures));
        }
    }

    /**
     * Ensure the Maven Resolver Implementation Artifact Descriptor can be resolved.
     */
    @Test
    void shouldResolveMavenResolverImplementationArtifactDescriptor() {
        this.maven.resolveArtifactDescriptor("org.apache.maven.resolver:maven-resolver-impl:1.3.3")
            .orElseThrow(() -> new AssertionError("Failed to resolve artifact descriptor [org.apache.maven.resolver:maven-resolver-impl:1.3.3]"));
    }

    /**
     * Ensure the Base Network Artifact Descriptor can be resolved.
     */
    @Test
    void shouldResolveBaseNetworkArtifactDescriptor() {
        this.maven.resolveArtifactDescriptor("build.base:base-network:0.19.0")
            .orElseThrow(() -> new AssertionError("Failed to resolve artifact descriptor [build.base:base-network:0.19.0]"));
    }

    /**
     * Ensure a collection of sometimes problematic descriptors for artifacts can be resolved.
     */
    @Test
    void shouldResolveArtifactDescriptors() {
        final ArrayList<String> coordinates = new ArrayList<>();
        coordinates.add("net.bytebuddy:byte-buddy:1.10.19");
        coordinates.add("org.mockito:mockito-core:jar:3.7.7");
        coordinates.add("org.apache.maven:maven-core:jar:3.8.6");
        coordinates.add("org.slf4j:slf4j-api:1.7.28");
//        coordinates.add("build.base:base-foundation:0.19.0");

        final long failures = coordinates.stream()
            .map(this.maven::resolveArtifactDescriptor)
            .filter(exceptional -> exceptional.isException()
                || exceptional.map(result -> !result.getExceptions().isEmpty())
                .orElse(false))
            .count();

        if (failures > 0) {
            fail(String.format("Failed to resolve %d artifact descriptor(s)", failures));
        }
    }
}
