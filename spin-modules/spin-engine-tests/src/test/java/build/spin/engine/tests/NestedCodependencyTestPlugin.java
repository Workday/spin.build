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
import build.spin.annotation.PreProcess;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test plugin with a two-level codependency chain: {@link Preprocessor} is a {@link PreProcess}
 * codependency of {@link MainTask}, and {@link NestedPreprocessor} is itself a {@link PreProcess}
 * codependency of {@link Preprocessor}. Used to verify that codependency resolution is recursive -
 * a codependency that itself declares {@link PreProcess}/{@link build.spin.annotation.PostProcess}
 * must have that nested codependency discovered and executed too, rather than
 * {@link build.spin.common.DefaultInstruction} only ever querying the owning task's own
 * codependencies (one level).
 */
public class NestedCodependencyTestPlugin implements Plugin {

    /**
     * Assigns each task an execution-order index as it runs, so relative ordering can be verified.
     */
    public static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    /**
     * Set to {@code true} by {@link NestedPreprocessor} when it runs.
     */
    public static final AtomicBoolean NESTED_PREPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * The order index recorded by {@link NestedPreprocessor}, or {@code -1} if it never ran.
     */
    public static final AtomicInteger NESTED_PREPROCESSOR_ORDER = new AtomicInteger(-1);

    /**
     * Set to {@code true} by {@link Preprocessor} when it runs.
     */
    public static final AtomicBoolean PREPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * The order index recorded by {@link Preprocessor}, or {@code -1} if it never ran.
     */
    public static final AtomicInteger PREPROCESSOR_ORDER = new AtomicInteger(-1);

    /**
     * Set to {@code true} by {@link MainTask} when it runs.
     */
    public static final AtomicBoolean MAIN_TASK_RAN = new AtomicBoolean(false);

    /**
     * The order index recorded by {@link MainTask}, or {@code -1} if it never ran.
     */
    public static final AtomicInteger MAIN_TASK_ORDER = new AtomicInteger(-1);

    @Named("codepnested-main")
    public static class MainTask implements Task<String> {
        public String compute() {
            MAIN_TASK_RAN.set(true);
            MAIN_TASK_ORDER.set(SEQUENCE.getAndIncrement());
            return "main";
        }
    }

    @PreProcess(MainTask.class)
    public static class Preprocessor implements Task<String> {
        public String compute() {
            PREPROCESSOR_RAN.set(true);
            PREPROCESSOR_ORDER.set(SEQUENCE.getAndIncrement());
            return "preprocessed";
        }
    }

    @PreProcess(Preprocessor.class)
    public static class NestedPreprocessor implements Task<String> {
        public String compute() {
            NESTED_PREPROCESSOR_RAN.set(true);
            NESTED_PREPROCESSOR_ORDER.set(SEQUENCE.getAndIncrement());
            return "nested-preprocessed";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("codepnested.marker"));
        }
    }
}
