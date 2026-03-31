package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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

import build.spin.module.modulesystem.ArtifactDescriptor;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleReference;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Represents the result of performing dependency analysis on compiled and packaged module.
 *
 * @see AbstractJavaDependencyAnalysis
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public interface DependencyAnalysis {

    /**
     * Obtains the {@link Dependency} information of the {@link ModuleDescriptor} for which the
     * {@link DependencyAnalysis} was performed.
     *
     * @return the {@link Dependency}
     */
    Dependency dependency();

    /**
     * Obtains a {@link Stream} of {@link ModuleReference}s the Java Platform modules used by the
     * {@link ModuleDescriptor}.
     *
     * @return a {@link Stream} of {@link ModuleReference}s
     */
    Stream<ModuleReference> platformModules();

    /**
     * Obtains a {@link Stream} of {@link Dependency} information for the modules required by the module defined by
     * the {@link ModuleDescriptor}.
     *
     * @return a {@link Stream} of {@link Dependency}s
     */
    Stream<Dependency> dependencies();

    /**
     * Obtains the {@link Stream} of module names for which no {@link ModuleDescriptor} could be determined.
     *
     * @return the {@link Stream} of unknown module names
     */
    Stream<String> unknownModules();

    /**
     * The {@link Path} to the resolved explicit modules.
     *
     * @return the {@link Path}
     */
    Path modulePath();

    /**
     * Dependency information for {@link ModuleDescriptor}, including it's resolved {@link ArtifactDescriptor}.
     */
    interface Dependency {

        /**
         * The {@link ModuleDescriptor}.
         *
         * @return the {@link ModuleDescriptor}
         */
        ModuleDescriptor moduleDescriptor();

        /**
         * The {@link ArtifactDescriptor}.
         *
         * @return the {@link ArtifactDescriptor}
         */
        ArtifactDescriptor artifactDescriptor();
    }
}
