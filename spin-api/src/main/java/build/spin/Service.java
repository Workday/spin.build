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

import java.nio.file.FileSystem;

/**
 * An {@link Extension} that can be used and {@link jakarta.inject.Inject}ed into all {@link Resource}s, {@link Plugin}s
 * and {@link Task}s in all {@link Project}s in a {@link Workspace}.
 *
 * @author brian.oliver
 * @since Sep-2019
 */
public interface Service
    extends Extension {

    /**
     * Defines the {@link Extension.MetaClass} for a {@link Service} {@link Extension}.
     */
    interface MetaClass
        extends Extension.MetaClass {

        @Override
        default String scheme() {
            return "service";
        }

        /**
         * Determines if a {@link Service} is detected in the specified {@link FileSystem} and thus should be made
         * available to all {@link Project}s.
         *
         * @param fileSystem the {@link FileSystem}
         *
         * @return {@code true} if the {@link Service} was detected and should be made available to {@link Project}s,
         *         {@code false} otherwise
         */
        boolean isDetectedIn(FileSystem fileSystem);

        /**
         * Obtains the concrete {@link Class} of the {@link Service} {@link Extension}.
         *
         * @return the {@link Class} {@link Service} {@link Extension}
         */
        @SuppressWarnings("unchecked")
        default Class<? extends Service> getExtensionClass() {
            return (Class<? extends Service>) getClass().getDeclaringClass();
        }
    }
}
