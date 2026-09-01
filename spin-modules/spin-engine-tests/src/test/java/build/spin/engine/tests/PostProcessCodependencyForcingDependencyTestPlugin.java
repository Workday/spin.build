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
import build.spin.annotation.PostProcess;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * The {@link PostProcess} mirror of {@link PreProcessCodependencyForcingDependencyTestPlugin}: a
 * {@link Postprocessor} codependency of {@link MainTask} declares a forcing dependency on
 * {@link ForcedTask} by overriding {@link Task#dependencies()}. {@link ForcedTask} is referenced by
 * nothing else. Used to verify the forcing-dependency folding in
 * {@link build.spin.common.DefaultInstruction} applies to {@link PostProcess} codependencies just as
 * it does to {@link build.spin.annotation.PreProcess} ones - both run inline as part of the owner and
 * never get an Instruction of their own.
 */
public class PostProcessCodependencyForcingDependencyTestPlugin implements Plugin {

    /**
     * Set to {@code true} by {@link MainTask} when it runs.
     */
    public static final AtomicBoolean MAIN_TASK_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link Postprocessor} when it runs.
     */
    public static final AtomicBoolean POSTPROCESSOR_RAN = new AtomicBoolean(false);

    /**
     * Set to {@code true} by {@link ForcedTask} when it runs.
     */
    public static final AtomicBoolean FORCED_TASK_RAN = new AtomicBoolean(false);

    @Named("postpforce-main")
    public static class MainTask implements Task<String> {
        public String compute() {
            MAIN_TASK_RAN.set(true);
            return "main";
        }
    }

    /**
     * Reachable only through {@link Postprocessor#dependencies()}.
     */
    public static class ForcedTask implements Task<String> {
        public String compute() {
            FORCED_TASK_RAN.set(true);
            return "forced";
        }
    }

    @PostProcess(MainTask.class)
    public static class Postprocessor implements Task<String> {

        @Inject
        private Project project;

        @Override
        public Stream<Reference> dependencies() {
            return Stream.of(Reference.of(this.project, ForcedTask.class));
        }

        public String compute() {
            POSTPROCESSOR_RAN.set(true);
            return "postprocessed";
        }
    }

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve("postpforce.marker"));
        }
    }
}
