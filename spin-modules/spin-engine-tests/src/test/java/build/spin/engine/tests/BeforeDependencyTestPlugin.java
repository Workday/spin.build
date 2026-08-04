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
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test plugin with a {@link BeforeTask} that declares itself {@link Before} {@link TargetTask}, where
 * nothing else references {@link BeforeTask} at all (no {@link build.spin.annotation.From} parameter,
 * no {@link Task.Pattern} match, no codependency). Used to verify that a Task's own {@link Before}
 * annotation does not, by itself, cause it to be pulled into a {@link build.spin.Program} merely because
 * the Task it names happens to run - the mirror image of {@link AfterDependencyTestPlugin}, which checks
 * the same for {@link build.spin.annotation.After}.
 */
public class BeforeDependencyTestPlugin implements Plugin {

    /**
     * Set to {@code true} by {@link TargetTask} when it runs.
     */
    public static final AtomicBoolean TARGET_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link BeforeTask} when it runs.
     */
    public static final AtomicBoolean BEFORE_TASK_RAN = new AtomicBoolean(false);

    @Named("beforedep-target")
    public static class TargetTask implements Task<String> {
        public String compute() {
            TARGET_TASK_RAN.set(true);
            return "target";
        }
    }

    @Named("beforedep-before")
    @Before(TargetTask.class)
    public static class BeforeTask implements Task<String> {
        public String compute() {
            BEFORE_TASK_RAN.set(true);
            return "before";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("beforedep.marker"));
        }
    }
}
