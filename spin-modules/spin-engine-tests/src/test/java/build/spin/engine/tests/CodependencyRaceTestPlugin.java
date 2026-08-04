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
import build.spin.annotation.From;
import build.spin.annotation.PreProcess;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test plugin whose {@link Preprocessor} codependency has its own {@link From} dependency
 * ({@link SlowDependency}). Used to verify that a codependency's own data dependencies are wired
 * into the {@link build.spin.Program} dependency graph as real scheduling constraints, rather than
 * only being pulled into {@code this.instructions} without a graph edge - which would let
 * {@link MainTask} (and therefore {@link Preprocessor}) be dispatched concurrently with, rather than
 * after, {@link SlowDependency}.
 */
public class CodependencyRaceTestPlugin implements Plugin {

    /**
     * How long {@link SlowDependency} sleeps before completing - long enough that, absent a real
     * scheduling constraint, {@link Preprocessor} would race ahead of it.
     */
    static final long SLOW_DEPENDENCY_DELAY_MILLIS = 300L;

    /**
     * Set to {@code true} by {@link SlowDependency} when it runs.
     */
    public static final AtomicBoolean SLOW_DEPENDENCY_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link Preprocessor} when it runs.
     */
    public static final AtomicBoolean PREPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * The value {@link Preprocessor} observed for {@link SlowDependency}'s result, or {@code null}
     * if it never ran (eg: it threw before reaching that point).
     */
    public static volatile String PREPROCESSOR_OBSERVED_VALUE;

    /**
     * Set to {@code true} by {@link MainTask} when it runs.
     */
    public static final AtomicBoolean MAIN_TASK_RAN = new AtomicBoolean(false);

    @Named("codeprace-slow-dependency")
    public static class SlowDependency implements Task<String> {
        public String compute() throws InterruptedException {
            Thread.sleep(SLOW_DEPENDENCY_DELAY_MILLIS);
            SLOW_DEPENDENCY_RAN.set(true);
            return "slow-value";
        }
    }

    @Named("codeprace-main")
    public static class MainTask implements Task<String> {
        public String compute() {
            MAIN_TASK_RAN.set(true);
            return "main";
        }
    }

    @PreProcess(MainTask.class)
    public static class Preprocessor implements Task<String> {
        public String compute(final @From(SlowDependency.class) String slowValue) {
            PREPROCESSOR_RAN.set(true);
            PREPROCESSOR_OBSERVED_VALUE = slowValue;
            return "preprocessed:" + slowValue;
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("codeprace.marker"));
        }
    }
}
