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

import build.base.commandline.CommandLineParser;
import build.base.configuration.Option;

import java.net.URI;
import java.util.stream.Stream;

/**
 * Provides functionality and capabilities to an {@link Engine}.
 *
 * @see Plugin
 * @see Resource
 * @see Service
 *
 * @author brian.oliver
 * @since Jan-2021
 */
public interface Extension {

    /**
     * Obtains the name of the {@link Extension}.
     * <p>
     * By default, this is the {@link Class#getCanonicalName()} of the {@link Extension} implementation {@link Class}.
     *
     * @return the name of the {@link Extension}
     */
    default String name() {
        return getClass().getCanonicalName();
    }

    /**
     * Provides runtime-type information concerning a {@link Class} of {@link Extension}.
     */
    interface MetaClass {

        /**
         * Obtains the {@link URI} scheme for the {@link Extension}.
         *
         * @return the {@link URI} schema.
         */
        String scheme();

        /**
         * Obtains a {@link Stream} of {@link Option} {@link Class}es provided by the {@link Extension} to include when
         * establishing a {@link CommandLineParser} to parse and inject said {@link Option}s.
         *
         * @return a {@link Stream} of {@link Option} {@link Class}es for the {@link Extension}
         */
        default Stream<Class<? extends Option>> getCommandLineClasses() {
            return Stream.empty();
        }

        /**
         * Obtains the concrete {@link Class} of the {@link Extension}.
         *
         * @return the {@link Class} {@link Extension}
         */
        @SuppressWarnings("unchecked")
        default Class<? extends Extension> getExtensionClass() {
            return (Class<? extends Extension>) getClass().getDeclaringClass();
        }
    }
}
