package build.spin;

/*-
 * #%L
 * Spin API
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
import build.base.configuration.Option;
import build.base.flow.Publicist;
import build.base.telemetry.Telemetry;
import build.codemodel.dependency.injection.Context;
import build.codemodel.dependency.injection.InjectionFramework;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides {@link Project} discovery, inference and execution.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public interface Engine
    extends Publicist<Telemetry>, AutoCloseable {

    /**
     * Obtains the {@link Configuration} used by the {@link Engine}.
     *
     * @return the {@link Configuration}
     */
    Configuration options();

    /**
     * Obtains the {@link FileSystem} used by the {@link Engine}.
     *
     * @return the {@link FileSystem}
     */
    FileSystem fileSystem();

    /**
     * Obtains the {@link InjectionFramework} used by the {@link Engine}.
     *
     * @return the {@link InjectionFramework}
     */
    InjectionFramework framework();

    /**
     * Obtains the {@link Context} the {@link Engine} uses for injection.
     *
     * @return the {@link Context}
     */
    Context context();

    /**
     * Obtains a {@link Stream} of {@link Extension.MetaClass}s.
     *
     * @return a {@link Stream} of {@link Extension.MetaClass}s
     */
    Stream<Extension.MetaClass> metaClasses();

    /**
     * Obtains a {@link Stream} of {@link Extension.MetaClass}s assignable to the specified
     * {@link Extension.MetaClass} {@link Class}.
     *
     * @param metaClassClass the {@link Class} of {@link Extension.MetaClass}
     * @param <T>            the type of {@link Extension.MetaClass}
     * @return a {@link Stream} of {@link Extension.MetaClass}s
     */
    <T extends Extension.MetaClass> Stream<T> metaClasses(Class<T> metaClassClass);

    /**
     * Obtains a {@link Stream} of {@link Service}s defined by {@link Engine}.
     *
     * @return the {@link Stream} of {@link Service}s
     */
    Stream<Service> services();

    /**
     * Obtains the display name for a {@link Class} of {@link Plugin} - the simple {@link Class} name,
     * unless another distinct {@link Plugin} {@link Class} discovered by this {@link Engine} shares the
     * same simple name (eg: same {@link Class} name, different package), in which case the fully-qualified
     * name is used instead so the two remain distinguishable.
     * <p>
     * Computed once across every {@link Plugin} discovered by this {@link Engine} for the entire
     * invocation, rather than per-{@link Project} - a {@link Plugin} name clash is a property of what
     * was discovered, not of which {@link Project} happens to be asking.
     *
     * @param pluginClass the {@link Class} of {@link Plugin}
     * @return the display name for the {@link Plugin} {@link Class}
     */
    String pluginDisplayName(Class<? extends Plugin> pluginClass);

    /**
     * Obtains a {@link Stream} of {@link Service}s defined by {@link Engine} assignable to the specified {@link Class}.
     *
     * @return the {@link Stream} of {@link Service}s
     */
    default <T> Stream<T> services(final Class<T> serviceClass) {
        return serviceClass == null
            ? Stream.empty()
            : services()
                .filter(serviceClass::isInstance)
                .map(serviceClass::cast);
    }

    /**
     * Discovers the {@link Path} of a {@link Workspace} by searching up the {@link Path} hierarchy, commencing at
     * the specified {@link Path}.
     * <p>
     * The {@link Workspace} {@link Path} is discovered by consulting the {@link Engine} {@link Resource.MetaClass}es
     * and {@link Plugin.MetaClass}es to determine if their associated {@link Extension}s are present in the
     * specified {@link Path} hierarchy, and furthermore, if a {@link Path} is considered {@link Workspace}.
     *
     * @param path the {@link Path} from which to commence discovering the {@link Workspace}
     * @return the {@link Optional} {@link Path} of the {@link Workspace}
     */
    Optional<Path> getWorkspacePath(Path path);

    /**
     * Discovers and creates a {@link Workspace} given the specified {@link Path} (within a workspace), performing
     * a depth first search of the file system from the specified path to create necessary sub-{@link Project}s.
     *
     * @param path the {@link Workspace} {@link Path}
     * @return an {@link Optional} {@link Workspace}
     *
     * @see #getWorkspacePath(Path)
     */
    Optional<Workspace> createWorkspace(Path path);

    /**
     * Discovers and creates a single federated {@link Workspace} spanning multiple physical roots, performing a
     * depth first search of the file system from each of the specified {@link Path}s to create necessary
     * sub-{@link Project}s.
     * <p>
     * Every {@link Project} discovered under any of the given roots shares one {@link Workspace}: their
     * {@link Project#workspace()} resolves to the same federating root, and {@link Project#stream()} called on
     * that root yields the union of every root's {@link Project} tree. This allows cross-repo sibling resolution
     * (e.g. {@link Project#workspace()}{@code .stream()} scans) to work unmodified across physical root
     * boundaries.
     *
     * @param roots the {@link Path}s of each physical root to include in the federated {@link Workspace}
     * @return an {@link Optional} {@link Workspace}; empty when no root {@link Path} yields a discoverable
     *         {@link Project}
     * @throws IllegalArgumentException if two or more of the specified {@link Path}s overlap (ie: one is nested
     *         within, or equal to, another), as this would create ambiguous, duplicate {@link Project}s
     *
     * @see #createWorkspace(Path)
     */
    Optional<Workspace> createWorkspace(List<Path> roots);

    /**
     * Creates a {@link Program} for the specified {@link Project} with the provided {@link Configuration}.
     *
     * @param project       the {@link Project}
     * @param optionsByType the {@link Configuration}
     * @return a new {@link Program}
     */
    Program createProgram(Project project, Configuration optionsByType);

    /**
     * Creates a {@link Program} for the specified {@link Project} with the provided
     * {@link Option}s.
     *
     * @param project the {@link Project}
     * @param options the {@link Option}s
     * @return a new {@link Program}
     */
    Program createProgram(Project project, Option... options);

    /**
     * Closes the {@link Engine}, invoking any {@link build.codemodel.dependency.injection.PreDestroy} lifecycle
     * methods on instantiated extension singletons in reverse dependency order.
     */
    @Override
    default void close() {
    }

}
