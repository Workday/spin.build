package build.spin.engine.tests;

/*-
 * #%L
 * Spin Engine Tests
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

import build.spin.Plugin;
import build.spin.Task;
import build.spin.annotation.Category;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test plugin with four mutually independent root tasks, all in the {@code slotbound} category, that
 * each sleep briefly while tracking how many task bodies are running concurrently. Used to verify that
 * {@link build.spin.common.DefaultProgram} caps concurrent task-body execution at its execution-slot
 * count (set low for the test via an explicit {@link build.spin.option.ExecutionSlots} option) rather
 * than running every DAG-ready task at once.
 */
public class ExecutionSlotBoundTestPlugin implements Plugin {

    /**
     * Number of task bodies currently executing.
     */
    public static final AtomicInteger LIVE = new AtomicInteger(0);

    /**
     * High-water mark of {@link #LIVE} observed across a Program execution.
     */
    public static final AtomicInteger MAX_LIVE = new AtomicInteger(0);

    /**
     * Number of task bodies that ran to completion.
     */
    public static final AtomicInteger COMPLETED = new AtomicInteger(0);

    /**
     * How long each task holds its execution slot; long enough that all ready tasks overlap.
     */
    public static final long TASK_DURATION_MILLIS = 500L;

    private static String runSlotBoundTask() throws InterruptedException {
        final int live = LIVE.incrementAndGet();
        MAX_LIVE.accumulateAndGet(live, Math::max);
        try {
            Thread.sleep(TASK_DURATION_MILLIS);
        } finally {
            LIVE.decrementAndGet();
        }
        COMPLETED.incrementAndGet();
        return "done";
    }

    @Category("slotbound")
    @Named("slotbound-1")
    public static class Task1 implements Task<String> {
        public String compute() throws InterruptedException {
            return runSlotBoundTask();
        }
    }

    @Category("slotbound")
    @Named("slotbound-2")
    public static class Task2 implements Task<String> {
        public String compute() throws InterruptedException {
            return runSlotBoundTask();
        }
    }

    @Category("slotbound")
    @Named("slotbound-3")
    public static class Task3 implements Task<String> {
        public String compute() throws InterruptedException {
            return runSlotBoundTask();
        }
    }

    @Category("slotbound")
    @Named("slotbound-4")
    public static class Task4 implements Task<String> {
        public String compute() throws InterruptedException {
            return runSlotBoundTask();
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("slotbound.marker"));
        }
    }
}
