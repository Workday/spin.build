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
import build.spin.annotation.After;
import build.spin.annotation.Category;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test plugin with two independent root tasks — one that fails immediately and one that
 * runs slowly — plus a task dependent on the slow one. Used to verify that once a task
 * fails, {@link build.spin.common.DefaultProgram} stops dispatching new tasks but still
 * lets already in-flight tasks (and their non-failed continuations) finish.
 */
public class FailFastTestPlugin implements Plugin {

    /**
     * Set to {@code true} by {@link SlowRoot} when it completes.
     */
    public static final AtomicBoolean SLOW_ROOT_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link NeverTask} if it ever runs; must remain {@code false} once
     * {@link TriggerTask} has failed.
     */
    public static final AtomicBoolean NEVER_TASK_RAN = new AtomicBoolean(false);

    /**
     * How long {@link SlowRoot} sleeps before completing; gives {@link TriggerTask} time to fail
     * and have its failure recorded before {@link SlowRoot} fires its dependents.
     */
    static final long SLOW_ROOT_DELAY_MILLIS = 200L;

    @Category("fail-fast")
    @Named("fail-fast-trigger")
    public static class TriggerTask implements Task<String> {
        public String compute() {
            throw new RuntimeException("fail-fast-trigger always fails");
        }
    }

    @Category("fail-fast")
    @Named("fail-fast-slow-root")
    public static class SlowRoot implements Task<String> {
        public String compute() throws InterruptedException {
            Thread.sleep(SLOW_ROOT_DELAY_MILLIS);
            SLOW_ROOT_RAN.set(true);
            return "slow-root";
        }
    }

    @After(SlowRoot.class)
    @Named("fail-fast-never")
    public static class NeverTask implements Task<String> {
        public String compute() {
            NEVER_TASK_RAN.set(true);
            return "never";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("fail-fast.marker"));
        }
    }
}
