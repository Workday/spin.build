package build.spin.module.languageserver.tasks;

/*-
 * #%L
 * Spin Language Server Module
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
 * A background executor of tasks.
 *
 * @author drew.malin
 * @since Jan-2023
 */
public interface TaskExecutor<T> {

    /**
     * Schedules the provided {@link Runnable} task for completion. The provided {@link T} ID may be used for task
     * cancellation.
     *
     * @param id   the {@link T} task ID
     * @param task the {@link Runnable} to schedule
     */
    void scheduleTask(T id, Runnable task);

    /**
     * Schedules the provided {@link Runnable} task for completion. The provided {@link T} ID may be used for task
     * cancellation. If the provided id is discovered to already be scheduled (meaning it is either running or is
     * scheduled to be run in the future) and if {@code abortIfAlreadyScheduled} is true, the task will not be
     * scheduled.
     * <p>
     * The use of {@code abortIfAlreadyScheduled} is useful when it is necessary to ensure that tasks are not
     * duplicated.
     *
     * @param id                      the {@link T} task ID
     * @param abortIfAlreadyScheduled if true, aborts the scheduling procedure if a task is found matching the id
     *                                parameter
     * @param task                    the {@link Runnable} to schedule
     */
    void scheduleTask(T id, boolean abortIfAlreadyScheduled, Runnable task);

    /**
     * Attempts to cancel the task corresponding to the provided {@link T} ID.
     *
     * @param id the {@link T} task ID
     */
    void cancelTask(T id);

    /**
     * Shuts down this {@link TaskExecutor}.
     */
    void shutdown();
}
