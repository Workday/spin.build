package build.spin.module.maven;

import build.base.foundation.Exceptional;
import build.base.foundation.UniformResource;
import build.base.telemetry.TelemetryRecorder;
import build.spin.common.telemetry.TelemetryPublisher;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import build.codemodel.injection.Context;
import build.codemodel.injection.InjectionFramework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link MavenRepository}s.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public class MavenRepositoryTests {

    /**
     * The {@link MavenRepository} under test.
     */
    private MavenRepository repository;

    /**
     * The {@link ModuleCatalog} for mapping modules to {@link Artifact}s.
     */
    private ModuleCatalog moduleCatalog;

    /**
     * The {@link ModuleVersioning} to determine the {@link Artifact} versions.
     */
    private ModuleVersioning versioning;

    @BeforeEach
    public void onBeforeEach() {
        final TelemetryRecorder recorder = new TelemetryPublisher(
            UniformResource.createURI("maven", "MavenRepository"),
            System.out::println);

        final Context context = InjectionFramework.create().newContext();
        context.bind(TelemetryRecorder.class).to(recorder);

        this.repository = context.create(MavenRepository.class);

        this.moduleCatalog = ModuleCatalog.HeapBased.create();
        this.versioning = new ModuleVersioning() {
            @Override
            public Optional<ModuleDescriptor.Version> getVersion(final String moduleName) {
                return Optional.empty();
            }

            @Override
            public Optional<ModuleDescriptor.Version> getVersion(final ModuleDescriptor descriptor) {
                return Optional.empty();
            }
        };
    }

    @Disabled
    @Test
    void shouldResolveBaseFoundationDependency() {

        final Artifact artifact = Artifact.parse("build.base:base-foundation:0.19.0");

        final Exceptional<Path> path = this.repository.resolve(artifact);

        assertThat(path.isPresent(), is(true));

        final Exceptional<ModuleDescriptor> moduleDescriptor =
            this.repository.getModuleDescriptor(artifact, this.moduleCatalog, this.versioning);

        assertThat(moduleDescriptor.isPresent(), is(true));
    }

    @Test
    void shouldResolveSLF4JDependency() {

        final Artifact artifact = Artifact.parse("org.slf4j:slf4j-api:1.7.28");

        final Exceptional<Path> optional = this.repository.resolve(artifact);

        assertThat(optional.isPresent(), is(true));

        final Exceptional<ModuleDescriptor> moduleDescriptor =
            this.repository.getModuleDescriptor(artifact, this.moduleCatalog, this.versioning);

        assertThat(moduleDescriptor.isPresent(), is(true));
    }

    @Test
    void shouldResolveMockitoDependency() {

        final Artifact artifact = Artifact.parse("org.mockito:mockito-core:jar:3.7.7");

        final Exceptional<Path> optional = this.repository.resolve(artifact);

        assertThat(optional.isPresent(), is(true));

        final Exceptional<ModuleDescriptor> moduleDescriptor =
            this.repository.getModuleDescriptor(artifact, this.moduleCatalog, this.versioning);

        moduleDescriptor.orElseThrow(() -> new AssertionError("Expected ModuleDescriptor for artifact [" + artifact + "] but none was resolved"));
        assertThat(moduleDescriptor.isPresent(), is(true));
    }

    @Test
    void shouldResolveMavenCoreDependency() {

        final Artifact artifact = Artifact.parse("org.apache.maven:maven-core:jar:3.8.6");

        final Exceptional<Path> optional = this.repository.resolve(artifact);

        assertThat(optional.isPresent(), is(true));

        final Exceptional<ModuleDescriptor> moduleDescriptor =
            this.repository.getModuleDescriptor(artifact, this.moduleCatalog, this.versioning);

        assertThat(moduleDescriptor.isPresent(), is(true));
    }

    @Test
    void shouldResolveDependencyModuleDescriptorsForMavenCore() {

        final Artifact artifact = Artifact.parse("org.apache.maven:maven-core:jar:3.8.6");

        final ModuleDescriptor moduleDescriptor = this.repository
            .getModuleDescriptor(artifact, this.moduleCatalog, this.versioning)
            .orElseThrow(() -> new AssertionError("Expected ModuleDescriptor for artifact [" + artifact + "] but none was resolved"));

        assertThat(moduleDescriptor.requires().count(), is(24L));
    }

    @Test
    void shouldResolveAsmArtifactsAndModuleDescriptors() {

        var asm72Artifact = Artifact.parse("org.ow2.asm:asm:7.2");
        var asm72ModuleDescriptor = this.repository
            .getModuleDescriptor(asm72Artifact, this.moduleCatalog, this.versioning)
            .orElseThrow(() -> new AssertionError("Expected ModuleDescriptor for artifact [" + asm72Artifact + "] but none was resolved"));

        var asm94Artifact = Artifact.parse("org.ow2.asm:asm:9.4");
        var asm94ModuleDescriptor = this.repository
            .getModuleDescriptor(asm94Artifact, this.moduleCatalog, this.versioning)
            .orElseThrow(() -> new AssertionError("Expected ModuleDescriptor for artifact [" + asm94Artifact + "] but none was resolved"));

        assertThat(asm72ModuleDescriptor.name(), is(asm94ModuleDescriptor.name()));
    }

    @Test
    void shouldResolveJacksonDatabindModuleDescriptor() {
        final Artifact artifact = Artifact.parse("com.fasterxml.jackson.core:jackson-databind:2.12.2");

        final Exceptional<Path> path = this.repository.resolve(artifact);

        assertThat(path.isPresent(), is(true));

        this.moduleCatalog.add("com.fasterxml.jackson.databind", artifact);

        final Exceptional<ModuleDescriptor> moduleDescriptor =
            this.repository.getModuleDescriptor(artifact, this.moduleCatalog, this.versioning);

        assertThat(moduleDescriptor.isPresent(), is(true));
    }

    @Test
    void shouldNotResolveModuleReferenceForUnknownModule() {

        final Artifact artifact = Artifact.parse("io.undertow:undertow-core:2.3.2.Final");

        // ensure the ModuleReference can't be resolved (with an empty ModuleCatalog)
        assertThat(this.repository.getModuleReference(artifact, this.moduleCatalog).isEmpty(), is(true));
    }

    @Test
    void shouldResolveUndertowModuleDescriptor() {

        final Artifact artifact = Artifact.parse("io.undertow:undertow-core:2.3.2.Final");

        // include the Artifact in the ModuleCatalog
        final var moduleName = "io.undertow";
        this.moduleCatalog.add(moduleName, artifact);

        // ensure we can resolve the ModuleReference for the Artifact
        final Exceptional<ModuleReference> exceptional = this.repository
            .getModuleReference(artifact, this.moduleCatalog);

        assertThat(exceptional.isPresent(), is(true));

        var moduleReference = exceptional.orElseThrow(() -> new AssertionError("Expected ModuleReference for artifact [" + artifact + "] but none was resolved"));

        assertThat(moduleReference.name(), is(moduleName));
        assertThat(moduleReference.version().orElseThrow(() -> new AssertionError("Expected version on ModuleReference [" + moduleReference.name() + "] but it was empty")).get(), is("2.3.2.Final"));

        // ensure we can resolve the ModuleDescriptor
        final ModuleDescriptor moduleDescriptor = this.repository
            .getModuleDescriptor(artifact, this.moduleCatalog, this.versioning)
            .orElseThrow(() -> new AssertionError("Expected ModuleDescriptor for artifact [" + artifact + "] but none was resolved"));

        assertThat(moduleDescriptor.requires().count(), is(7L));
    }
}
