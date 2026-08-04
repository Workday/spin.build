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
import build.spin.annotation.From;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test plugin with a chain: {@link RootTask} is {@link From} {@link MiddleTask} (so {@link MiddleTask}
 * is independently required), and {@link MiddleTask} is {@link After} {@link SideTask} (so
 * {@link SideTask} is only reachable through {@link MiddleTask}'s {@link After} annotation, never
 * through a {@link From} parameter or a {@link Task.Pattern} match).
 * <p>
 * Used to verify whether declaring {@link After} on a Task that IS independently required causes the
 * referenced Task to be pulled into and executed as part of a {@link build.spin.Program}, as opposed
 * to {@link AfterTestPlugin}, which verifies the (already-fixed) case where nothing requires the
 * {@link After}-annotated Task at all.
 */
public class AfterDependencyTestPlugin implements Plugin {

    /**
     * Set to {@code true} by {@link RootTask} when it runs.
     */
    public static final AtomicBoolean ROOT_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link MiddleTask} when it runs.
     */
    public static final AtomicBoolean MIDDLE_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link SideTask} when it runs.
     */
    public static final AtomicBoolean SIDE_TASK_RAN = new AtomicBoolean(false);

    @Named("afterdep-side")
    public static class SideTask implements Task<String> {
        public String compute() {
            SIDE_TASK_RAN.set(true);
            return "side";
        }
    }

    @Named("afterdep-middle")
    @After(SideTask.class)
    public static class MiddleTask implements Task<String> {
        public String compute() {
            MIDDLE_TASK_RAN.set(true);
            return "middle";
        }
    }

    @Named("afterdep-root")
    public static class RootTask implements Task<String> {
        public String compute(@From(MiddleTask.class) final String middle) {
            ROOT_TASK_RAN.set(true);
            return "root:" + middle;
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("afterdep.marker"));
        }
    }
}
