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

import java.util.Objects;
import java.util.Optional;

/**
 * A reference to a named and {@link Optional}ly {@link ModuleDescriptor.Version}ed {@link ModuleDescriptor}.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public interface ModuleReference {

    /**
     * Obtains the name of the {@link ModuleDescriptor} being referenced.
     *
     * @return the name of the {@link ModuleDescriptor}
     */
    String name();

    /**
     * Obtains the {@link Optional} {@link ModuleDescriptor.Version} of the {@link ModuleDescriptor} being referenced.
     *
     * @return the {@link Optional} {@link ModuleDescriptor.Version}
     */
    Optional<ModuleDescriptor.Version> version();

    /**
     * Creates a {@link ModuleReference} for the specified {@link ModuleDescriptor}.
     *
     * @param descriptor the {@link ModuleDescriptor}
     * @return a new {@link ModuleReference}
     */
    static ModuleReference of(final ModuleDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "The ModuleDescriptor must not be null");

        return of(descriptor.name(), descriptor.version());
    }

    /**
     * Creates a {@link ModuleReference} to the specified named {@link ModuleDescriptor}.
     *
     * @param name the name of the {@link ModuleDescriptor}
     * @return a new {@link ModuleReference}
     */
    static ModuleReference of(final String name) {
        return new Implementation(name, Optional.empty());
    }

    /**
     * Creates a {@link ModuleReference} to the specifically named {@link ModuleDescriptor} and
     * {@link ModuleDescriptor.Version}.
     *
     * @param name the name of the {@link ModuleDescriptor}
     * @param version the {@link ModuleDescriptor.Version}
     * @return a new {@link ModuleReference}
     */
    static ModuleReference of(final String name, final ModuleDescriptor.Version version) {
        return new Implementation(name, Optional.ofNullable(version));
    }

    /**
     * Creates a {@link ModuleReference} to the specifically named {@link ModuleDescriptor} and {@link Optional}
     * {@link ModuleDescriptor.Version}.
     *
     * @param name the name of the module
     * @param version the {@link Optional} {@link ModuleDescriptor.Version}
     * @return a new {@link ModuleReference}
     */
    static ModuleReference of(final String name, final Optional<ModuleDescriptor.Version> version) {
        return new Implementation(name, version);
    }

    /**
     * An implementation of a {@link ModuleReference}.
     */
    class Implementation
        implements ModuleReference {

        /**
         * The name of the referenced module.
         */
        private final String name;

        /**
         * The {@link Optional} {@link ModuleDescriptor.Version} of the referenced module.
         */
        private final Optional<ModuleDescriptor.Version> version;

        /**
         * Constructs an {@link Implementation} of a {@link ModuleReference}.
         *
         * @param name the name of the referenced module
         * @param version the {@link Optional} {@link ModuleDescriptor.Version} of the referenced module
         */
        private Implementation(final String name,
                               final Optional<ModuleDescriptor.Version> version) {
            this.name = Objects.requireNonNull(name, "The ModuleDescriptor.Reference name must not be null");
            this.version = version == null ? Optional.empty() : version;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public Optional<ModuleDescriptor.Version> version() {
            return this.version;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ModuleReference)) {
                return false;
            }
            final ModuleReference that = (ModuleReference) object;
            return this.name.equals(that.name()) && this.version.equals(that.version());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.name, this.version);
        }

        @Override
        public String toString() {
            return "Module [" + name() + "]" + this.version.map(v -> " @" + v.toString()).orElse("");
        }
    }
}
