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

import build.spin.option.ServerMode;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link Resource} performing asynchronous, background processing within and on behalf a {@link Project} discovered
 * by a {@code spin} Command Line Application Interface (CLI) {@link Engine} when in <i>server mode</i>.
 * <p>
 * When in operating in <i>Server Mode</i>, the {@code spin} CLI will run until it is either:
 * <ol>
 *     <li>the {@code spin} CLI process itself is terminated or killed externally, or</li>
 *     <li>one or more of the {@link Server#start()} or {@link Daemon#start()} {@link CompletableFuture}s is completed,
 *         either naturally, cancelled or exceptionally.
 * </ol>
 * Prior to final termination, the {@code spin} CLI will attempt to close all {@link AutoCloseable} {@link Extension}s,
 * in depth-first order.
 *
 * @see BackgroundProcessor
 * @see ServerMode
 * @see Server
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public interface Daemon
    extends Resource, BackgroundProcessor {

    /**
     * Defines the {@link MetaClass} for a {@link Daemon} {@link Resource}.
     */
    interface MetaClass
        extends Resource.MetaClass {

        @Override
        default String scheme() {
            return "daemon";
        }

        /**
         * Obtains the concrete {@link Class} of the {@link Daemon} {@link Resource}.
         *
         * @return the {@link Class} {@link Daemon} {@link Resource}
         */
        @SuppressWarnings("unchecked")
        default Class<? extends Daemon> getExtensionClass() {
            return (Class<? extends Daemon>) getClass().getDeclaringClass();
        }
    }
}
