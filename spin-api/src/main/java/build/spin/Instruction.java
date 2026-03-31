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

import java.util.stream.Stream;

/**
 * {@link Program} specific information concerning a {@link Task} to execute.
 *
 * @author brian.oliver
 * @since Aug-2019
 */
public interface Instruction<T> {

    /**
     * Obtains the unique identity of the {@link Instruction} in the {@link Program} to which it belongs.
     *
     * @return the unique identity
     */
    int getIdentity();

    /**
     * Obtains the {@link Reference} for the {@link Instruction}.
     *
     * @return the {@link Reference}
     */
    Reference getReference();

    /**
     * Obtains the {@link Project} in which the {@link Task} is defined.
     *
     * @return the {@link Project}
     */
    Project getProject();

    /**
     * Obtains the {@link Invocable} for the {@link Task} to be executed.
     *
     * @return the {@link Invocable}
     */
    Invocable<T> getInvocable();

    /**
     * Obtains the {@link Task} to be executed.
     *
     * @return the {@link Task}
     */
    Task<?> getTask();

    /**
     * Determines the {@link Task} dependencies defined by the {@link Task} for the {@link Instruction}.
     *
     * @return a {@link Stream} of {@link Reference}s to {@link Task}s
     */
    Stream<Reference> dependencies();

    /**
     * Determine if the {@link Task} has any dependencies.
     *
     * @return {@code true} if there's one or more dependencies, {@code false} otherwise
     */
    boolean hasDependencies();

    /**
     * Obtains a {@link Stream} of the {@link Task}s that can be executed after the execution of the {@link Task}.
     *
     * @return a {@link Stream} of {@link Reference}s
     */
    Stream<Reference> dependents();

    /**
     * Determine if the {@link Task} has any dependents.
     *
     * @return {@code true} if there's one or more dependents, {@code false} otherwise
     */
    boolean hasDependents();

    /**
     * Determines the {@link Task} codependencies for the {@link Instruction}.
     *
     * @return a {@link Stream} of {@link Reference}s to {@link Task}s
     */
    Stream<Invocable<?>> codependencies();

    /**
     * Determine if the {@link Task} has any codependencies.
     *
     * @return {@code true} if there's one or more codependencies, {@code false} otherwise
     */
    boolean hasCodependencies();
}
