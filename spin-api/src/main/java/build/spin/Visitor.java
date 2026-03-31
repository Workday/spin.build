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

/**
 * Provides the ability to visit {@link Object}s.
 *
 * @param <T> the type of {@link Object}
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public interface Visitor<T> {

    /**
     * Invoked to determine if {@link #visit(Object)}ing the {@link Object} is permitted.
     * <p>
     * By default, {@link #visit(Object)}ing is always permitted.
     *
     * @param object the {@link Object}
     * @return {@code true} if visiting {@link Object} is permitted,
     *         {@code false} otherwise
     */
    default boolean onEntering(final T object) {
        return true;
    }

    /**
     * Invoked to visit a {@link Object}, if and only if permitted by {@link #onEntering(Object)}.
     *
     * @param object the {@link Object}
     */
    void visit(T object);

    /**
     * Invoked after {@link #onEntering(Object)} and possibly {@link #visit(Object)}ing the {@link Object}.
     * <p>
     * By default, this method performs no operations.
     *
     * @param object the {@link Object}
     */
    default void onLeaving(final T object) {
    }
}
