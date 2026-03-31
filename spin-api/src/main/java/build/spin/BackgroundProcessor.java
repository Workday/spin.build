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

import build.base.foundation.Exceptional;

import java.util.concurrent.CompletableFuture;

/**
 * Provides the ability to perform background processing for an {@link Engine}.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public interface BackgroundProcessor {

    /**
     * Invoked to initialize a {@link BackgroundProcessor} prior to being {@link #start()}ed.
     */
    default void onInitialize() {
        // by default, BackgroundProcessors don't require further initialization
    }

    /**
     * Requests {@link BackgroundProcessor} to start.
     * <p>
     * The returned {@link Exceptional} {@link CompletableFuture} is used to control and notify the caller as to
     * the termination status of the {@link BackgroundProcessor}.
     * <p>
     * A {@link BackgroundProcessor} implementation should complete the returned {@link CompletableFuture} in indicate
     * the desire to terminate and the termination status code for the caller.
     * <p>
     * Completing with the {@link CompletableFuture} with {@link CompletableFuture#complete(Object)} {@link Integer}
     * indicates that termination occurred with the specified <i>exit status code</i>.  Completing exceptionally with
     * {@link CompletableFuture#completeExceptionally(Throwable)} indicates that {@link BackgroundProcessor} failed
     * exceptionally
     * <p>
     * During background execution, the returned {@link CompletableFuture} may be
     * {@link CompletableFuture#cancel(boolean)}ed, outside the {@link BackgroundProcessor} implementation to indicate
     * background processing must stop as soon as possible and shutdown.
     *
     * @return an {@link Exceptional} {@link CompletableFuture} to control {@link BackgroundProcessor} lifecycle,
     *         an {@link Exceptional#empty()} when a {@link BackgroundProcessor} wasn't started, or
     *         an {@link Exceptional} containing a {@link Throwable} when starting failed
     */
    Exceptional<CompletableFuture<Integer>> start();
}
