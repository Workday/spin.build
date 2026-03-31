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

/**
 * A {@link Project}-specific {@link Extension} that may be {@link jakarta.inject.Inject}ed into {@link Plugin}s and
 * {@link Task}s for a specific {@link Project} together with {@link Plugin}s and {@link Task}s for descendant
 * {@link Project}s.
 * <p>
 * Unlike {@link Service}s that are {@link jakarta.inject.Inject}able into all {@link Plugin}s and {@link Task}s,
 * regardless of the {@link Project} in which they are detected, {@link Resource}s are {@link Project} specific.
 *
 * @author brian.oliver
 * @since Nov-2019
 */
public interface Resource
    extends Extension {

    /**
     * Determines if the specified {@link Path} must be ignored when considering {@link Project}.
     *
     * @param path the {@link Path}
     * @return {@code true} if the {@link Resource} requests the {@link Path} to be ignored
     *         {@code false} otherwise
     */
    default boolean isIgnored(final Path path) {
        return false;
    }

    /**
     * Defines the {@link Extension.MetaClass} for a {@link Resource} {@link Extension}.
     */
    interface MetaClass
        extends Extension.MetaClass {

        @Override
        default String scheme() {
            return "resource";
        }

        /**
         * Determines if the specified {@link Path} defines a {@link Workspace}.
         *
         * @param path the {@link Path}
         * @return {@code true} if the {@link Resource} considers the {@link Path} to be a {@link Workspace}
         *         {@code false} otherwise
         */
        default boolean isWorkspace(final Path path) {
            return false;
        }

        /**
         * Determines if a {@link Resource} is detected in the specified {@link Project} and thus should be made
         * available the said {@link Project} and all descendant {@link Project}s.
         *
         * @param project the {@link Project}
         *
         * @return {@code true} if the {@link Resource} was detected and should be made available to the
         *         specified {@link Project} (and descendants), {@code false} otherwise
         */
        boolean isDetectedIn(Project project);

        /**
         * Obtains the concrete {@link Class} of the {@link Resource} {@link Extension}.
         *
         * @return the {@link Class} {@link Resource} {@link Extension}
         */
        @SuppressWarnings("unchecked")
        default Class<? extends Resource> getExtensionClass() {
            return (Class<? extends Resource>) getClass().getDeclaringClass();
        }
    }
}
