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
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test plugin with a {@link After} task that is not a dependency of anything. Used to verify that
 * {@link After} does not, by itself, cause a task to be included in a {@link build.spin.Program}.
 */
public class AfterTestPlugin implements Plugin {

    /**
     * Set to {@code true} by {@link MainTask} when it runs.
     */
    public static final AtomicBoolean MAIN_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link AfterTask} when it runs.
     */
    public static final AtomicBoolean AFTER_TASK_RAN = new AtomicBoolean(false);

    @Named("after-main")
    public static class MainTask implements Task<String> {
        public String compute() {
            MAIN_TASK_RAN.set(true);
            return "main";
        }
    }

    @Named("after-task")
    @After(MainTask.class)
    public static class AfterTask implements Task<String> {
        public String compute() {
            AFTER_TASK_RAN.set(true);
            return "after";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("after.marker"));
        }
    }
}
