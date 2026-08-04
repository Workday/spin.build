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
import build.spin.annotation.Before;
import build.spin.annotation.Category;
import build.spin.annotation.PreProcess;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test plugin whose {@link Preprocessor} codependency (of {@link MainTask}) declares its own
 * {@link Before} annotation, targeting {@link SecondaryTask} - an independently-required root task
 * with no other relationship to {@link MainTask} or {@link Preprocessor}. Used to verify that
 * {@link Before}/{@link build.spin.annotation.After} declared on a codependency's own {@link Task}
 * class is honored as an ordering constraint, rather than being silently ignored because
 * codependencies never go through {@link build.spin.common.DefaultInstruction}'s own
 * {@link Before}/{@link build.spin.annotation.After} resolution pass.
 * <p>
 * {@link Preprocessor} sleeps before recording its completion timestamp, so that - absent a real
 * ordering constraint - {@link SecondaryTask} (which has no dependencies of its own) would race
 * ahead and record an earlier timestamp than {@link Preprocessor}.
 */
public class CodependencyOrderTestPlugin implements Plugin {

    /**
     * How long {@link Preprocessor} sleeps before completing - long enough that, absent a real
     * ordering constraint, {@link SecondaryTask} would race ahead of it.
     */
    static final long PREPROCESSOR_DELAY_MILLIS = 300L;

    /**
     * Set to {@code true} by {@link MainTask} when it runs.
     */
    public static final AtomicBoolean MAIN_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link Preprocessor} when it runs.
     */
    public static final AtomicBoolean PREPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * {@link System#nanoTime()} recorded by {@link Preprocessor} when it completes.
     */
    public static final AtomicLong PREPROCESSOR_COMPLETED_AT = new AtomicLong(-1L);

    /**
     * Set to {@code true} by {@link SecondaryTask} when it runs.
     */
    public static final AtomicBoolean SECONDARY_TASK_RAN = new AtomicBoolean(false);

    /**
     * {@link System#nanoTime()} recorded by {@link SecondaryTask} when it completes.
     */
    public static final AtomicLong SECONDARY_TASK_COMPLETED_AT = new AtomicLong(-1L);

    @Category("codep-order")
    @Named("codeporder-main")
    public static class MainTask implements Task<String> {
        public String compute() {
            MAIN_TASK_RAN.set(true);
            return "main";
        }
    }

    @PreProcess(MainTask.class)
    @Before(SecondaryTask.class)
    public static class Preprocessor implements Task<String> {
        public String compute() throws InterruptedException {
            Thread.sleep(PREPROCESSOR_DELAY_MILLIS);
            PREPROCESSOR_RAN.set(true);
            PREPROCESSOR_COMPLETED_AT.set(System.nanoTime());
            return "preprocessed";
        }
    }

    @Category("codep-order")
    @Named("codeporder-secondary")
    public static class SecondaryTask implements Task<String> {
        public String compute() {
            SECONDARY_TASK_RAN.set(true);
            SECONDARY_TASK_COMPLETED_AT.set(System.nanoTime());
            return "secondary";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("codeporder.marker"));
        }
    }
}
