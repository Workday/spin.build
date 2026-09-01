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
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.PreProcess;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Combines the recursive-codependency case ({@link NestedCodependencyTestPlugin}) with the
 * forcing-dependency case ({@link PreProcessCodependencyForcingDependencyTestPlugin}): {@link NestedPreprocessor}
 * is a {@link PreProcess} codependency of {@link Preprocessor}, which is itself a {@link PreProcess}
 * codependency of {@link MainTask}, and it is {@link NestedPreprocessor} - the deeper of the two -
 * that declares the forcing dependency on {@link ForcedTask} via {@link Task#dependencies()}. Verifies
 * the forcing-dependency folding in {@link build.spin.common.DefaultInstruction} reaches
 * transitively-discovered codependencies, not just the owning task's immediate ones.
 */
public class NestedCodependencyForcingDependencyTestPlugin implements Plugin {

    /**
     * Set to {@code true} by {@link MainTask} when it runs.
     */
    public static final AtomicBoolean MAIN_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link Preprocessor} when it runs.
     */
    public static final AtomicBoolean PREPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link NestedPreprocessor} when it runs.
     */
    public static final AtomicBoolean NESTED_PREPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link ForcedTask} when it runs.
     */
    public static final AtomicBoolean FORCED_TASK_RAN = new AtomicBoolean(false);

    @Named("nestforce-main")
    public static class MainTask implements Task<String> {
        public String compute() {
            MAIN_TASK_RAN.set(true);
            return "main";
        }
    }

    /**
     * Reachable only through {@link NestedPreprocessor#dependencies()}.
     */
    public static class ForcedTask implements Task<String> {
        public String compute() {
            FORCED_TASK_RAN.set(true);
            return "forced";
        }
    }

    @PreProcess(MainTask.class)
    public static class Preprocessor implements Task<String> {
        public String compute() {
            PREPROCESSOR_RAN.set(true);
            return "preprocessed";
        }
    }

    @PreProcess(Preprocessor.class)
    public static class NestedPreprocessor implements Task<String> {

        @Inject
        private Project project;

        @Override
        public Stream<Reference> dependencies() {
            return Stream.of(Reference.of(this.project, ForcedTask.class));
        }

        public String compute() {
            NESTED_PREPROCESSOR_RAN.set(true);
            return "nested-preprocessed";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("nestforce.marker"));
        }
    }
}
