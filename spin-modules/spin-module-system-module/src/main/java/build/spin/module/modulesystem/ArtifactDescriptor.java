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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides immutable runtime information concerning an {@link Artifact} for a {@link ModuleReference}, optionally
 * including the {@link Path} to the resolve {@link Artifact}.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public interface ArtifactDescriptor {

    /**
     * Obtains the {@link ModuleReference} for the {@link Artifact}.
     *
     * @return the {@link ModuleReference}
     */
    ModuleReference reference();

    /**
     * Obtains the {@link Artifact}.
     *
     * @return the {@link Artifact}
     */
    Artifact artifact();

    /**
     * The {@link Optional} {@link Path} to the resolved {@link Artifact}.
     *
     * @return the {@link Optional} {@link Path}
     */
    Optional<Path> path();

    /**
     * Creates an {@link ArtifactDescriptor} for the specified {@link ModuleReference} and {@link Artifact}.
     *
     * @param reference the {@link ModuleReference}
     * @param artifact the {@link Artifact}
     * @return a new {@link ArtifactDescriptor}
     */
    static ArtifactDescriptor create(final ModuleReference reference,
                                     final Artifact artifact) {

        return create(reference, artifact, Optional.empty());
    }

    /**
     * Creates an {@link ArtifactDescriptor} for the specified {@link ModuleReference} and {@link Artifact}
     * including the {@link Optional} {@link Path} for the resolved {@link Artifact}.
     *
     * @param reference the {@link ModuleReference}
     * @param artifact the {@link Artifact}
     * @param path the {@link Optional} {@link Path}
     *
     * @return a new {@link ArtifactDescriptor}
     */
    static ArtifactDescriptor create(final ModuleReference reference,
                                     final Artifact artifact,
                                     final Optional<Path> path) {

        return new Implementation(reference, artifact, path);
    }

    /**
     * Creates an {@link ArtifactDescriptor} for the specified {@link ModuleReference} and {@link Artifact}
     * including the {@link Path} of the resolved {@link Artifact}.
     *
     * @param reference the {@link ModuleReference}
     * @param artifact the {@link Artifact}
     * @param path the {@link Path}
     *
     * @return a new {@link ArtifactDescriptor}
     */
    static ArtifactDescriptor create(final ModuleReference reference,
                                     final Artifact artifact,
                                     final Path path) {

        return create(reference, artifact, Optional.ofNullable(path));
    }

    /**
     * The default implementation of an {@link ArtifactDescriptor}.
     */
    class Implementation
        implements ArtifactDescriptor {

        /**
         * The {@link ModuleReference}.
         */
        private final ModuleReference reference;

        /**
         * The {@link Artifact}.
         */
        private final Artifact artifact;

        /**
         * The {@link Optional} {@link Path}.
         */
        private final Optional<Path> path;

        private Implementation(final ModuleReference reference,
                               final Artifact artifact,
                               final Optional<Path> path) {

            this.reference = Objects.requireNonNull(reference, "The ModuleReference must not be null");
            this.artifact = Objects.requireNonNull(artifact, "The Artifact must not be null");
            this.path = path == null ? Optional.empty() : path;
        }

        @Override
        public ModuleReference reference() {
            return this.reference;
        }

        @Override
        public Artifact artifact() {
            return this.artifact;
        }

        @Override
        public Optional<Path> path() {
            return this.path;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final Implementation that = (Implementation) object;
            return this.reference.equals(that.reference)
                && this.artifact.equals(that.artifact)
                && this.path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.reference, this.artifact, this.path);
        }

        @Override
        public String toString() {
            return "ArtifactDescriptor{" +
                "reference=" + this.reference +
                ", artifact=" + this.artifact +
                ", path=" + this.path +
                '}';
        }
    }
}
