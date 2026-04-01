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
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test plugin with two tasks that form a cyclic dependency. Used to verify that
 * the engine detects and reports cycles rather than silently producing no output.
 */
public class CyclicTestPlugin implements Plugin {

    /** TaskA depends on TaskB. TaskB depends on TaskA — a cycle. */
    @Named("cyclic-a")
    public static class TaskA implements Task<String> {
        public String compute(@From(TaskB.class) final String b) {
            return "a:" + b;
        }
    }

    @Named("cyclic-b")
    public static class TaskB implements Task<String> {
        public String compute(@From(TaskA.class) final String a) {
            return "b:" + a;
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("cyclic.marker"));
        }
    }
}
