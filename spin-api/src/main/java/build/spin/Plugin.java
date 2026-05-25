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

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * An {@link Extension} defining zero or more related {@link Task}s for a {@link Project}.
 *
 * @see Project
 * @see Task
 *
 * @author brian.oliver
 * @since Jun-2019
 */
public interface Plugin
    extends Extension {

    /**
     * Obtains a {@link Stream} of {@link Invocable}s defined by the {@link Plugin} for the {@link Project} in which
     * it is defined.
     * <p>
     * By default, this method returns {@link Stream#empty()} as {@link Task}s to execute are automatically detected.
     * <p>
     * Any {@link Invocable}s returned by this method will be included with those automatically detected.
     *
     * @return a {@link Stream} of {@link Invocable}s
     */
    default Stream<Invocable<?>> invocables() {
        return Stream.empty();
    }

    /**
     * Obtains cross-project task {@link Reference}s that must complete before the specified
     * {@link Task} class in this plugin's {@link Project} may execute.
     *
     * <p>This is the mechanism for expressing inter-project compile ordering derived from
     * the module graph: a plugin whose module {@code requires} modules owned by workspace
     * siblings can return the sibling's compile-task {@link Reference} here so that the
     * {@link build.spin.Program} scheduler enforces the correct dependency order without
     * relying on filesystem state at task-inference time.
     *
     * @param forTaskClass the task class about to be scheduled
     * @return a {@link Stream} of cross-project {@link Reference}s that must finish first
     */
    default Stream<Reference> projectDependencies(final Class<? extends Task<?>> forTaskClass) {
        return Stream.empty();
    }

    /**
     * Defines the {@link Extension.MetaClass} for a {@link Plugin}.
     */
    interface MetaClass
        extends Extension.MetaClass {

        @Override
        default String scheme() {
            return "plugin";
        }

        /**
         * Determines if a {@link Plugin} is applicable to the specified {@link Path}.
         *
         * @param path the {@link Path}
         *
         * @return {@code true} if the {@link Plugin} is applicable to the specified {@link Path},
         *         {@code false} otherwise
         */
        boolean isDetectedIn(Path path);

        /**
         * Determines if a {@link Plugin} is applicable to the specified {@link Project}.
         *
         * @param project the {@link Project}
         *
         * @return {@code true} if the {@link Plugin} is applicable to the specified {@link Project},
         *         {@code false} otherwise
         */
        default boolean isDetectedIn(final Project project) {
            return false;
        }

        /**
         * Obtains the concrete {@link Class} of the {@link Plugin} {@link Extension}.
         *
         * @return the {@link Class} {@link Plugin} {@link Extension}
         */
        @SuppressWarnings("unchecked")
        default Class<? extends Plugin> getExtensionClass() {
            return (Class<? extends Plugin>) getClass().getDeclaringClass();
        }
    }

    /**
     * A {@link Comparable} {@link Plugin}.
     *
     * @param <P> the type of the {@link Plugin}
     */
    interface Comparable<P extends Plugin.Comparable<P>>
        extends Plugin, java.lang.Comparable<P> {

    }
}
