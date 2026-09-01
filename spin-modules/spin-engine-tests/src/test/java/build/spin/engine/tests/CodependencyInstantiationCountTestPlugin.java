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
import build.spin.annotation.PostProcess;
import build.spin.annotation.PreProcess;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test plugin whose codependencies count their own construction. A codependency's {@link Task}
 * instance is created once, when the owning {@link build.spin.common.DefaultInstruction} is built,
 * and reused for the inline execution in {@code DefaultProgram#runTask} - exactly as the primary
 * {@link Task} is. Used to guard against a regression to creating a throwaway instance for the
 * {@link Task#dependencies()} read and then a second instance to execute: each counter must read
 * exactly {@code 1} after a single {@code Program} execution, not {@code 2}.
 */
public class CodependencyInstantiationCountTestPlugin implements Plugin {

    /**
     * Incremented by {@link Preprocessor}'s constructor every time it is instantiated.
     */
    public static final AtomicInteger PREPROCESSOR_CONSTRUCTIONS = new AtomicInteger(0);

    /**
     * Incremented by {@link Postprocessor}'s constructor every time it is instantiated.
     */
    public static final AtomicInteger POSTPROCESSOR_CONSTRUCTIONS = new AtomicInteger(0);

    /**
     * Set to {@code true} by {@link MainTask} when it runs.
     */
    public static final AtomicBoolean MAIN_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link Preprocessor} when it runs.
     */
    public static final AtomicBoolean PREPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link Postprocessor} when it runs.
     */
    public static final AtomicBoolean POSTPROCESSOR_RAN = new AtomicBoolean(false);

    @Named("codepcount-main")
    public static class MainTask implements Task<String> {
        public String compute() {
            MAIN_TASK_RAN.set(true);
            return "main";
        }
    }

    @PreProcess(MainTask.class)
    public static class Preprocessor implements Task<String> {

        public Preprocessor() {
            PREPROCESSOR_CONSTRUCTIONS.incrementAndGet();
        }

        public String compute() {
            PREPROCESSOR_RAN.set(true);
            return "preprocessed";
        }
    }

    @PostProcess(MainTask.class)
    public static class Postprocessor implements Task<String> {

        public Postprocessor() {
            POSTPROCESSOR_CONSTRUCTIONS.incrementAndGet();
        }

        public String compute() {
            POSTPROCESSOR_RAN.set(true);
            return "postprocessed";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("codepcount.marker"));
        }
    }
}
