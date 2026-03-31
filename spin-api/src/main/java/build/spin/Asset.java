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

import build.spin.annotation.From;
import build.spin.annotation.NoCache;

/**
 * A <strong>non-null</strong> immutable value produced by executing a {@link Plugin} {@link Task} in a
 * specific {@link Project}.
 * <p>
 * {@link Asset}s are typically consumed by other {@link Plugin}s {@link Task}s using the {@link From} annotation,
 * either in the same or different {@link Project}s, as an {@code Asset<T>} type or simply as the result type {@code T}.
 * <p>
 * Once produced by a {@link Plugin} {@link Task}, {@link Asset}s are <strong>immutable</strong>.  This is to allow
 * them to be cached by {@link AssetCache}s, and/or later reused during {@link Program} execution.  Should an
 * {@link Asset} not be cacheable, the {@link Task} should be annotated with {@link NoCache}.
 * <p>
 * {@link Asset}s are not named, but instead <strong>strongly-typed</strong> and scoped by the {@link Project},
 * {@link Plugin} and {@link Class} of {@link Task} (ie: {@link Invocable}), that was responsible for producing them.
 * Thus, they can not be looked up by name, but only by {@link Invocable}.
 *
 * @see Project
 * @see Plugin
 * @see Task
 * @see Invocable
 * @see AssetCache
 * @see From
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public interface Asset<T> {

    /**
     * The {@link Project} in which the {@link Asset} was produced.
     *
     * @return the {@link Project}
     */
    default Project project() {
        return invocable().getProject();
    }

    /**
     * The {@link Plugin} that produced the {@link Asset}.
     *
     * @return the {@link Asset}
     */
    default Plugin plugin() {
        return invocable().getPlugin();
    }

    /**
     * The {@link Invocable} that produced the {@link Asset}.
     *
     * @return {@link Invocable}
     */
    Invocable<T> invocable();

    /**
     * Obtains the {@link Asset} value.
     *
     * @return the {@link Asset} value
     */
    T get();
}
