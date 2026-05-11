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

import java.util.Optional;
import java.util.stream.Stream;

/**
 * A cache of {@link Task} {@link Instruction} execution results, known as {@link Asset}s.
 *
 * @author brian.oliver
 * @since Jan-2021
 */
public interface AssetCache {

    /**
     * Obtains the currently {@link Asset} for the specified {@link Task} {@link Reference}.
     *
     * @param <T> the type of the {@link Asset}
     * @param reference the {@link Task} {@link Reference}
     *
     * @return the {@link Optional} {@link Asset}, or {@link Optional#empty()} is unavailable
     */
    <T> Optional<Asset<T>> get(Reference reference);

    /**
     * Adds an {@link Asset} for the specified {@link Task} {@link Invocable} result to the {@link AssetCache},
     * returning the previously cached {@link Asset} for the {@link Invocable}.
     *
     * @param <T> the type of the result
     * @param invocable the {@link Task} {@link Invocable}
     * @param value the {@link Asset} value
     * @return the {@link Optional} existing {@link Asset}
     */
    <T> Optional<Asset<T>> putIfAbsent(Invocable<T> invocable, T value);

    /**
     * Adds an {@link Asset} to the {@link AssetCache}, returning the previously cached {@link Asset} for
     * the {@link Invocable}.
     *
     * @param <T> the type of the result
     * @param asset the {@link Asset}
     * @return the {@link Optional} existing {@link Asset}
     */
    <T> Optional<Asset<T>> putIfAbsent(Asset<T> asset);

    /**
     * Obtains the first {@link Asset} whose {@link Task} class implements the given task interface.
     *
     * <p>Use this to retrieve results without coupling to a specific plugin version:
     * {@code cache.get(JavaCompilerPlugin.DetectSourcePaths.class)} works regardless of whether
     * the project uses {@code Java25CompilerPlugin} or any other compiler plugin.
     *
     * @param <T> the type of the {@link Asset}
     * @param taskInterface the {@link Task} interface to match
     * @return the first matching {@link Asset}, or {@link Optional#empty()} if none
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<Asset<T>> get(final Class<? extends Task<T>> taskInterface) {
        return assets()
            .filter(a -> taskInterface.isAssignableFrom(a.invocable().getTaskClass()))
            .findFirst()
            .map(a -> (Asset<T>) a);
    }

    /**
     * Obtains the first {@link Asset} for the given {@link Project} whose {@link Task} class implements
     * the given task interface.
     *
     * @param <T> the type of the {@link Asset}
     * @param project the {@link Project} to scope the lookup to
     * @param taskInterface the {@link Task} interface to match
     * @return the first matching {@link Asset}, or {@link Optional#empty()} if none
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<Asset<T>> get(final Project project,
                                       final Class<? extends Task<T>> taskInterface) {
        return assets()
            .filter(a -> project.equals(a.invocable().getProject()))
            .filter(a -> taskInterface.isAssignableFrom(a.invocable().getTaskClass()))
            .findFirst()
            .map(a -> (Asset<T>) a);
    }

    /**
     * Determines if an {@link Asset} is available for the specified {@link Task} {@link Reference}.
     *
     * @param reference the {@link Task} {@link Reference}
     * @return {@code true} if there's a captured result, {@code false} otherwise
     */
    default boolean contains(final Reference reference) {
        return get(reference).isPresent();
    }

    /**
     * Obtains a {@link Stream} of the {@link Task} {@link Invocable}s with {@link Asset}s in the {@link AssetCache}
     *
     * @return a {@link Stream} of the {@link Task} {@link Invocable}s
     */
    Stream<Invocable<?>> invocables();

    /**
     * Obtains a {@link Stream} of the {@link Asset}s in the {@link AssetCache}
     *
     * @return a {@link Stream} of the {@link Task} {@link Asset}s
     */
    Stream<Asset<?>> assets();

    /**
     * Obtains a {@link Stream} of the {@link Task} {@link Reference}s with {@link Asset}s in the {@link AssetCache}
     *
     * @return a {@link Stream} of the {@link Task} {@link Reference}s
     */
    default Stream<Reference> references() {
        return invocables().map(Invocable::getReference);
    }
}
