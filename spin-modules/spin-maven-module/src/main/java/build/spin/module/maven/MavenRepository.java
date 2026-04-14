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
import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.injection.PostInject;
import build.spin.Service;
import build.spin.module.java.JavaPlatform;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import jakarta.inject.Inject;
import org.eclipse.aether.graph.Dependency;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link Artifact.Resolver} {@link Service} for an Apache Maven-based Repository.
 * <p>
 * This implementation is based on the
 * <a href="https://github.com/apache/maven-resolver/blob/master/maven-resolver-demos/maven-resolver-demo-snippets">Apache Maven Demo Snippets</a>
 * provided by Apache.
 *
 * @author brian.oliver
 * @since Sep-2019
 */
public class MavenRepository
    implements Service, Artifact.Resolver {

    /**
     * The {@link TelemetryRecorder} for the {@link Service}.
     */
    private final TelemetryRecorder recorder;

    /**
     * The {@link MavenFacade} to actually interact with Apache Maven and Apache Maven-based Repositories.
     */
    private MavenFacade maven;

    /**
     * The {@link ModuleReference}s by {@link Artifact}.
     */
    private final ConcurrentHashMap<Artifact, Exceptional<ModuleReference>> moduleReferences;

    /**
     * The {@link ModuleDescriptor}s extracted by {@link Artifact}.  These have been extracted and are in their
     * unmodified state from underlying {@link Artifact}s.
     */
    private final ConcurrentHashMap<Artifact, Exceptional<ModuleDescriptor>> extractedDescriptors;

    /**
     * The {@link ModuleDescriptor}s resolved by {@link Artifact}.   These are extracted and unmodified
     * from underlying {@link Artifact}s.
     */
    private final ConcurrentHashMap<Artifact, Exceptional<ModuleDescriptor>> resolvedDescriptors;

    /**
     * The previously resolved {@link ModuleDescriptor}s by {@link Artifact} and {@link JDKVersion}.
     */
    private final ConcurrentHashMap<Artifact, Exceptional<ModuleDescriptor>> moduleDescriptors;

    /**
     * Constructs the {@link MavenRepository}.
     *
     * @param recorder the {@link TelemetryRecorder}
     */
    @Inject
    private MavenRepository(final TelemetryRecorder recorder) {
        this.recorder = recorder;
        this.moduleReferences = new ConcurrentHashMap<>();
        this.extractedDescriptors = new ConcurrentHashMap<>();
        this.resolvedDescriptors = new ConcurrentHashMap<>();
        this.moduleDescriptors = new ConcurrentHashMap<>();
    }

    /**
     * Initialize {@link MavenRepository} {@link Service} after creation.
     */
    @PostInject
    public void onInjected() {
        // establish the MavenFacade to interact with Maven Repositories
        this.maven = new MavenFacade(this.recorder, Configuration.empty());
    }

    @Override
    public Exceptional<Path> resolve(final Artifact artifact) {

        return this.maven.resolveArtifact(artifact.toString())
            .map(result -> result.getArtifact().getPath());
    }

    @Override
    public Exceptional<List<Path>> resolveTransitive(final Artifact artifact) {
        return this.maven.resolveTransitiveDependencies(artifact.toString());
    }

    @Override
    public Exceptional<ModuleReference> getModuleReference(final Artifact artifact,
                                                           final ModuleCatalog catalog) {

        // attempt to use the previously resolved the ModuleReference for the Artifact
        final Exceptional<ModuleReference> existingReference = this.moduleReferences.get(artifact);

        if (existingReference != null
            && existingReference.map(reference -> reference.version().isPresent()).orElse(true)) {
            return existingReference;
        }

        // attempt to obtain a previously extracted ModuleDescriptor for the Artifact
        Exceptional<ModuleDescriptor> extractedDescriptor = this.extractedDescriptors.get(artifact);

        if (extractedDescriptor == null) {
            extractedDescriptor = Exceptional.empty();
        }

        // obtain the ModuleReference from the extracted ModuleDescriptor
        Exceptional<ModuleReference> reference = extractedDescriptor.map(ModuleDescriptor::reference);

        if (reference.isEmpty()) {
            // attempt to resolve the path to the Artifact itself and use that to extract a ModuleDescriptor
            // (and thus ModuleReference)
            final Exceptional<Path> path = resolve(artifact);

            extractedDescriptor = path.flatMap(p -> ModuleDescriptor.extract(artifact, p));

            // retain the extracted ModuleDescriptor
            final Exceptional<ModuleDescriptor> existingDescriptor = this.extractedDescriptors
                .putIfAbsent(artifact, extractedDescriptor);

            // obtain the ModuleReference from the extracted ModuleDescriptor
            if (existingDescriptor != null) {
                extractedDescriptor = existingDescriptor;
            }

            reference = extractedDescriptor.map(ModuleDescriptor::reference);
        }

        // attempt to obtain the ModuleReference using the ModuleCatalog
        final Optional<ModuleReference> catalogedReference = catalog.getModuleReference(artifact);

        if (catalogedReference.isPresent()) {
            if (reference.isPresent()) {
                if (!catalogedReference.get().equals(reference.orElseThrow(() -> new IllegalStateException("reference absent despite isPresent() check for artifact: " + artifact)))) {
                    // TODO: warn that the ModuleCatalog-based information is inconsistent with the extracted information
                    // (ignore the ModuleCatalog)
                }

                // retain the resolved ModuleReference
                // (iff the reference provides a version)
                if (reference.orElseThrow(() -> new IllegalStateException("reference absent despite isPresent() check for artifact: " + artifact)).version().isPresent()) {
                    this.moduleReferences.putIfAbsent(artifact, reference);
                }
            } else {
                // TODO: information... using the ModuleCatalog-based information
                //  (the extracted ModuleDescriptor is not available)
                reference = Exceptional.ofOptional(catalogedReference);
            }
        } else if (reference.isPresent()) {
            // TODO: warn that the ModuleCatalog doesn't contain an entry for the Artifact

            // retain the resolved ModuleReference
            // (iff the reference provides a version)
            if (reference.orElseThrow(() -> new IllegalStateException("reference absent despite isPresent() check for artifact: " + artifact)).version().isPresent()) {
                // update the ModuleCatalog
                reference.ifPresent(r -> catalog.add(r.name(), artifact));

                // retain the resolved ModuleReference
                this.moduleReferences.putIfAbsent(artifact, reference);
            }
        } else {
            // warn that the Artifact couldn't be resolvable, and it doesn't exist in the ModuleCatalog
            this.recorder.warn("Module Name for [%s] is not resolvable by the Artifact.Resolver or Module Catalog",
                artifact.toString());
        }

        return reference;
    }

    @Override
    public Exceptional<ModuleDescriptor> getModuleDescriptor(final Artifact artifact,
                                                             final ModuleCatalog catalog,
                                                             final ModuleVersioning versioning) {

        // attempt to use the previously resolved ModuleDescriptor for the Artifact
        final var existing = this.resolvedDescriptors.get(artifact);

        if (existing != null) {
            return existing;
        }

        // attempt to locate a previously extracted ModuleDescriptor for the Artifact
        var extracted = this.extractedDescriptors.get(artifact);

        if (extracted == null) {
            // attempt to resolve and extract the ModuleDescriptor
            final Exceptional<Path> path = resolve(artifact);

            extracted = path.flatMap(p -> ModuleDescriptor.extract(artifact, p));
        }

        // retain the extracted ModuleDescriptor
        final var extractable = extracted;

        extracted = this.extractedDescriptors
            .compute(artifact, (__, current) -> current == null || current.isException()
                ? extractable
                : current);

        // terminate when extracting the ModuleDescriptor had failed
        // (there's nothing we can do to recover)
        if (extracted.isException()) {
            // retain the failure to resolve the ModuleDescriptor
            final var resolved = this.resolvedDescriptors.putIfAbsent(artifact, extracted);

            return resolved == null ? extracted : resolved;
        }

        // when the descriptor is present, it's not automatic, it's not synthetic, it has a version, and it has
        // complete "requires" version information, we know it's been successfully resolved as a fully-blown-module
        if (extracted.map(descriptor -> !descriptor.isAutomatic()
                && !descriptor.isSynthetic()
                && descriptor.version().isPresent()
                && descriptor.requires()
                .map(ModuleDescriptor.Requires::version)
                .allMatch(Optional::isPresent))
            .orElse(false)) {

            // store the extracted ModuleDescriptor as it's completely resolved
            final var resolved = extracted;

            return this.resolvedDescriptors.compute(artifact, (__, current) -> current == null ? resolved : current);
        } else {
            // here it means the extracted descriptor is for an automatic module, or it's information is incomplete
            // (due to the java compiler not being able to detect it, from a non-modular dependency), which means we
            // need to resolve and reverse-engineer the "requires" information using the pom.xml to "enhance" it
            // (to thus create an "enhanced" descriptor)
            final ModuleDescriptor.Builder builder = extracted
                .map(descriptor -> {
                    // copy everything but the non-Java-platform "requires" definitions into a new builder
                    final ModuleDescriptor.Builder bldr = ModuleDescriptor.Builder
                        .create(descriptor.name())
                        .setLocation(descriptor.location())
                        .setEnhanced(true);

                    bldr.setOpen(descriptor.isOpen());
                    bldr.setAutomatic(descriptor.isAutomatic());
                    bldr.setMandated(descriptor.is(ModuleDescriptor.Modifier.MANDATED));
                    bldr.setSynthetic(descriptor.isSynthetic());

                    // include the Java Platform dependencies
                    descriptor.requires()
                        .filter(requires -> JavaPlatform.isJavaPlatformModule(requires.name()))
                        .forEach(requires -> {
                            final var modifiers = EnumSet.noneOf(ModuleDescriptor.Requires.Modifier.class);
                            requires.modifiers().forEach(modifiers::add);

                            bldr.requires(
                                requires.name(),
                                modifiers,
                                requires.version().orElse(null));
                        });

                    // choose the correct version
                    final Optional<ModuleDescriptor.Version> overridden = versioning
                        .getVersion(descriptor.name());

                    if (!(descriptor.version().equals(overridden))
                        && overridden.isPresent()) {
                        bldr.setVersion(overridden.get());

                        overridden.ifPresent(version ->
                            this.recorder.warn("Overriding %s version %s with %s",
                                descriptor.name(),
                                descriptor.version()
                                    .map(ModuleDescriptor.Version::toString)
                                    .orElse("(unspecified)"),
                                overridden.get()));
                    } else {
                        descriptor.version()
                            .map(bldr::setVersion);
                    }

                    descriptor.provides()
                        .forEach(provides -> bldr.provides(provides.service(), provides.providers()));

                    descriptor.exports()
                        .forEach(exports -> bldr.exports(exports.source(),
                            exports.modifier().orElse(null),
                            exports.targets()));

                    descriptor.uses().forEach(bldr::uses);

                    return bldr;
                }).orElseGet(() -> {
                    // create a ModuleDescriptor.Builder for the unknown module (deriving the module name if required)
                    // (we still may be able to build a ModuleDescriptor using the pom.xml)
                    final var moduleName = catalog.getModuleName(artifact)
                        .orElseGet(() -> {
                            final var derivedName = catalog.getDerivedModuleName(artifact);

                            this.recorder.warn(
                                "[%s] is not found in the Module Catalog.  Defaulting to the derived module name [%s])",
                                artifact.toString(), derivedName);

                            return derivedName;
                        });

                    return ModuleDescriptor.Builder.create(moduleName)
                        .setEnhanced(true)
                        .noLocation();
                });

            builder.setVersion(ModuleDescriptor.Version.parse(artifact.version().get()));

            // attempt to resolve the pom.xml for the Artifact
            // (we can use this to reverse-engineer the ModuleDescriptor)

            final var resolved = this.maven.resolveArtifactDescriptor(artifact.toString())
                .flatMap(result -> {

                    // TODO: check if the result had any exceptions!

                    // we only want non-test, non-optional compile, runtime and provided dependencies
                    final List<Dependency> dependencies = result.getDependencies()
                        .stream()
                        .filter(dependency -> (dependency.getScope().equals("compile")
                            || dependency.getScope().equals("runtime")
                            || dependency.getScope().equals("provided"))
                            && !dependency.isOptional())
                        .toList();

                    if (dependencies.isEmpty()) {
                        // there's no dependencies, so we may continue with the ModuleDescriptor Builder
                        return Exceptional.of(builder);
                    } else {
                        // include the "compile", "provided" and "runtime" dependencies
                        dependencies.stream()
                            .filter(dependency -> dependency.getScope().equals("compile")
                                || dependency.getScope().equals("runtime")
                                || dependency.getScope().equals("provided"))
                            .forEach(dependency -> {
                                final boolean isStatic = dependency.getScope().equals("provided")
                                    || dependency.isOptional();

                                final Artifact requiredArtifact = Artifact.create(
                                    dependency.getArtifact().getGroupId(),
                                    dependency.getArtifact().getArtifactId(),
                                    dependency.getArtifact().getVersion(),
                                    dependency.getArtifact().getExtension(),
                                    dependency.getArtifact().getClassifier());

                                // include the required ModuleReference
                                final var reference = getModuleReference(requiredArtifact, catalog)
                                    .orElseGet(() -> {
                                        // TODO: warn that we could resolve the required dependency so we're defaulting to a derived module name
                                        return catalog.getDerivedModuleReference(requiredArtifact);
                                    });

                                // include the required ModuleReference
                                final EnumSet<ModuleDescriptor.Requires.Modifier> modifiers = isStatic
                                    ? EnumSet.of(ModuleDescriptor.Requires.Modifier.STATIC)
                                    : EnumSet.of(ModuleDescriptor.Requires.Modifier.TRANSITIVE);

                                final ModuleDescriptor.Version parsed = reference.version().get();
                                final Optional<ModuleDescriptor.Version> overridden = versioning
                                    .getVersion(reference.name());

                                if (!parsed.equals(overridden.orElse(null))) {
                                    overridden.ifPresent(version ->
                                        this.recorder.warn("Overriding %s version %s with %s",
                                            reference.name(),
                                            parsed,
                                            version));
                                }

                                // use the overridden version (when available)
                                final ModuleDescriptor.Version version = overridden.orElse(parsed);

                                builder.requires(reference.name(), modifiers, version);

                                // update the Catalog with mapping (if it's not already known)
                                if (catalog.getModuleName(requiredArtifact).isEmpty()) {
                                    catalog.add(reference.name(), requiredArtifact);
                                }
                            });

                        return Exceptional.of(builder);
                    }
                })
                .map(ModuleDescriptor.Builder::build);

            this.resolvedDescriptors.putIfAbsent(artifact, resolved);

            return resolved;
        }
    }

    /**
     * The {@link Service.MetaClass} for {@link MavenRepository}.
     */
    public static class MetaClass
        implements Service.MetaClass {

        @Override
        public boolean isDetectedIn(final FileSystem fileSystem) {
            // obtain the user home (in which to detect a .m2 directory)
            final Path home = fileSystem.getPath(System.getProperty("user.home"));

            return Files.exists(home.resolve(".m2"));
        }
    }
}
