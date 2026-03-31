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

import build.base.telemetry.TelemetryRecorder;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * A {@link TaskExecutor} which schedules cancellable {@link CompletableFuture}s.
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class CancellableTaskExecutor<T>
    implements TaskExecutor<T> {


    private final ConcurrentHashMap<T, Future<?>> taskMap;
    private final ExecutorService taskExecutorService;
    private final TelemetryRecorder recorder;

    public CancellableTaskExecutor(final ExecutorService executorService, final TelemetryRecorder recorder) {
        this.taskMap = new ConcurrentHashMap<>();
        this.taskExecutorService = executorService;
        this.recorder = recorder;
    }

    @Override
    public void scheduleTask(final T id, final Runnable task) {
        this.recorder.warn("Scheduling request %s", id);

        // Create an "internal" future which is cancelable.
        final var future = new CancellableFuture<Void>(() -> {
            task.run();
            return null;
        }, this.taskExecutorService);

        // Chain a final step on completion of the future (though do not capture this Future for cancellation,
        // as it is not the originating CompletionStage in the chain!)
        future.whenComplete((r, e) -> {
            this.taskMap.remove(id);
            this.recorder.warn("Completed request %s", id);
        });

        this.taskMap.put(id, future);
    }

    @Override
    public void scheduleTask(final T id, final boolean abortIfAlreadyScheduled, final Runnable task) {
        if (abortIfAlreadyScheduled && this.taskMap.containsKey(id)) {
            this.recorder.warn("A running request already exists for ID %s!", id);
            return;
        }
        scheduleTask(id, task);
    }

    @Override
    public void cancelTask(final T id) {
        final var task = this.taskMap.get(id);

        // No task found? Nothing more to do.
        if (task == null) {
            this.recorder.warn("No running task found for ID %s", id);
            return;
        }

        // Proceed with cancellation.
        final var success = task.cancel(true);
        this.taskMap.remove(id);

        if (success) {
            this.recorder.warn("Cancelled running task %s", id);
        }
        else {
            this.recorder.warn("Failed to cancel running task %s", id);
        }
    }

    @Override
    public void shutdown() {
        this.taskExecutorService.shutdown();
    }

    /**
     * A cancellable version of a {@link CompletableFuture} which delegates to an internal (cancellable!) {@link Future}.
     *
     * @param <T> the return type of the future
     */
    private static class CancellableFuture<T>
        extends CompletableFuture<T> {

        private final Future<?> future;

        private CancellableFuture(final Callable<T> runnable, final ExecutorService executorService) {
            this.future = executorService.submit(() -> complete(runnable));
        }

        private void complete(final Callable<T> runnable) {
            try {
                final var result = runnable.call();
                complete(result);
            }
            catch (final Exception e) {
                completeExceptionally(e);
            }
        }

        @Override
        public boolean cancel(final boolean mayInterrupt) {
            return this.future.cancel(mayInterrupt) && super.cancel(true);
        }
    }
}
