package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
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
import build.base.version.Version;
import build.base.version.VersionConstraint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Provides the ability to determine the {@link Artifact}s in which Java Modules have been packaged and vice versa.
 * <p>
 * {@link ModuleCatalog}s provide mappings from Java Module names to {@link Artifact}s specified by
 * {@link Artifact.Constraint}s, whereby each {@link Artifact.Constraint} specifies the Apache Maven Coordinates of one
 * or more {@link Artifact}s using {@link VersionConstraint}s.
 *
 * @see Artifact
 * @see Artifact.Constraint
 *
 * @author brian.oliver
 * @since Sep-2019
 */
public interface ModuleCatalog {

    /**
     * Adds a mapping for the {@link Artifact.Constraint} to a specific module.
     *
     * @param moduleName the name of the module
     * @param constraint the {@link Artifact.Constraint}
     *
     * @return the {@link ModuleCatalog} to permit fluent-style method invocation
     */
    ModuleCatalog add(String moduleName, Artifact.Constraint constraint);

    /**
     * Adds a mapping for the {@link Artifact} to a specific module.
     *
     * @param moduleName the name of the module
     * @param artifact the {@link Artifact.Constraint}
     *
     * @return the {@link ModuleCatalog} to permit fluent-style method invocation
     */
    default ModuleCatalog add(final String moduleName, final Artifact artifact) {
        return add(moduleName, Artifact.Constraint.of(artifact));
    }

    /**
     * Obtains a {@link Stream} of {@link Artifact.Constraint}s defined for the specified module name.
     *
     * @param moduleName the module name
     * @return a {@link Stream} of {@link Artifact}s
     */
    Stream<Artifact.Constraint> constraints(String moduleName);

    /**
     * Attempts to obtain the {@link ModuleReference} for an {@link Artifact}.
     *
     * @param artifact the {@link Artifact}
     * @return the {@link Optional} {@link ModuleReference} for the {@link Artifact} or
     *          {@link Optional#empty()} if the {@link Artifact} is unknown
     */
    Optional<ModuleReference> getModuleReference(Artifact artifact);

    /**
     * Attempts to obtain the {@link Artifact} for a {@link ModuleReference}.
     *
     * @param reference the {@link ModuleReference}
     * @return the {@link Optional} {@link Artifact} for the {@link ModuleReference} or
     *          {@link Optional#empty()} if the {@link ModuleReference} is unknown
     *
     * @see #getArtifact(ModuleReference, Optional)
     */
    default Optional<Artifact> getArtifact(final ModuleReference reference) {
        return getArtifact(reference, Optional.empty());
    }

    /**
     * Attempts to obtain the {@link Artifact} for a {@link ModuleReference}.
     * <p>
     * Should the {@link ModuleReference} match more than one distinct {@link Artifact} (i.e. more than
     * one registered {@link Artifact.Constraint} is satisfied by the requested version, but they don't
     * all resolve to the same {@link Artifact}), the first registered {@link Artifact} is kept and, if a
     * {@link TelemetryRecorder} is supplied, the ambiguity is recorded as a warning.
     *
     * @param reference the {@link ModuleReference}
     * @param recorder  the {@link Optional} {@link TelemetryRecorder} to warn on ambiguous matches
     * @return the {@link Optional} {@link Artifact} for the {@link ModuleReference} or
     * {@link Optional#empty()} if the {@link ModuleReference} is unknown
     */
    default Optional<Artifact> getArtifact(final ModuleReference reference,
                                           final Optional<TelemetryRecorder> recorder) {

        if (reference == null || reference.version().isEmpty()) {
            return Optional.empty();
        }

        final Version version = reference.version()
            .orElseThrow(() -> new MissingModuleVersionException(reference));

        final List<Artifact.Constraint> matches = constraints(reference.name())
            .filter(constraint -> constraint.contains(version))
            .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        final Artifact artifact = matches.getFirst().createArtifact(version);

        recorder.ifPresent(r -> {
            final boolean ambiguous = matches.stream().skip(1)
                .anyMatch(constraint -> !constraint.createArtifact(version).equals(artifact));

            if (ambiguous) {
                r.warn(
                    "Module [%s] version [%s] matches multiple distinct Artifact Constraints %s — keeping [%s]",
                    reference.name(), version, matches, artifact);
            }
        });

        return Optional.of(artifact);
    }

    /**
     * Obtains the module name for an {@link Artifact}.
     *
     * @param artifact the {@link Artifact}
     * @return the {@link Optional} module name for the {@link Artifact} or
     *          {@link Optional#empty()} if the {@link Artifact} is unknown
     */
    default Optional<String> getModuleName(final Artifact artifact) {

        return getModuleReference(artifact)
            .map(ModuleReference::name);
    }

    /**
     * Derives a Module Name given an {@link Artifact} according to the
     * <a href="https://docs.oracle.com/javase/9/docs/api/java/lang/module/ModuleFinder.html#of-java.nio.file.Path...-">Java Platform Specification</a>.
     *
     * @param artifact an {@link Artifact}
     * @return a derived Module Name
     */
    default String getDerivedModuleName(final Artifact artifact) {
        Objects.requireNonNull(artifact, "The Artifact must not be null");

        // start with the Artifact Id (which is usually the filename of the Artifact)
        String moduleName = artifact.artifactId();

        // all non-alphanumeric characters are converted to "."
        moduleName = moduleName.replaceAll("[^A-Za-z0-9]", ".");

        // strip "." at beginning and end
        moduleName = moduleName.replaceAll("^\\.*|\\.*$", "");

        // all multiple "." stripped to single
        moduleName = moduleName.replaceAll("\\.{2,}", ".");

        return moduleName;
    }

    /**
     * Derives a {@link ModuleReference} given an {@link Artifact}.
     *
     * @param artifact an {@link Artifact}
     * @return a derived {@link ModuleReference}
     *
     * @see #getDerivedModuleName(Artifact)
     */
    default ModuleReference getDerivedModuleReference(final Artifact artifact) {
        Objects.requireNonNull(artifact, "The Artifact must not be null");

        final var name = getDerivedModuleName(artifact);

        return ModuleReference.of(name, artifact.version());
    }

    /**
     * A thread-safe heap-based {@link ModuleCatalog}.
     */
    class HeapBased
        implements ModuleCatalog {

        /**
         * A {@link ConcurrentHashMap} of Java Module Names to {@link Artifact.Constraint}s.
         */
        private final ConcurrentHashMap<String, LinkedHashSet<Artifact.Constraint>> constraints;

        /**
         * Constructs a {@link HeapBased} {@link ModuleCatalog}.
         */
        private HeapBased() {
            this.constraints = new ConcurrentHashMap<>();
        }

        @Override
        public ModuleCatalog add(final String moduleName, final Artifact.Constraint constraint) {
            if (moduleName == null || constraint == null) {
                return this;
            }

            this.constraints.compute(moduleName, (__, existing) -> {
                final LinkedHashSet<Artifact.Constraint> constraints = existing == null
                    ? new LinkedHashSet<>()
                    : existing;

                constraints.add(constraint);

                return constraints;
            });

            return this;
        }

        @Override
        public Optional<ModuleReference> getModuleReference(final Artifact artifact) {
            return this.constraints.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(constraint -> constraint.contains(artifact)))
                .map(Map.Entry::getKey)
                .findFirst()
                .map(name -> ModuleReference.of(name, artifact.version()));
        }

        @Override
        public Stream<Artifact.Constraint> constraints(final String moduleName) {
            if (moduleName == null) {
                return Stream.empty();
            }

            final LinkedHashSet<Artifact.Constraint> constraints = this.constraints.get(moduleName);

            return constraints == null
                ? Stream.empty()
                : constraints.stream();
        }

        /**
         * Creates an empty {@link HeapBased} {@link ModuleCatalog}.
         *
         * @return a new {@link HeapBased} {@link ModuleCatalog}
         */
        public static HeapBased create() {
            return new HeapBased();
        }

        /**
         * Creates a {@link HeapBased} {@link ModuleCatalog} based on the specified {@link Properties}.
         * <p>
         * Each property in the {@link Properties} represents a mapping from a Java Module System module name
         * to the coordinates of an Apache Maven Artifact.  For example:
         * {@code org.slf4j = org.slf4j:slf4j-api:jar:1.7.28}
         *
         * @param properties the {@link Properties}
         * @return a new {@link HeapBased} {@link ModuleCatalog}
         *
         * @see Artifact#parse(String)
         */
        public static HeapBased create(final Properties properties) {

            final HeapBased catalog = create();

            properties.stringPropertyNames().forEach(moduleName -> {
                final String coordinates = properties.getProperty(moduleName);
                final Artifact.Constraint constraint = Artifact.Constraint.parse(coordinates);

                catalog.add(moduleName, constraint);
            });

            return catalog;
        }
    }
}
