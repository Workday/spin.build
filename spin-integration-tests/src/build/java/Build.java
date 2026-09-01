/*-
 * #%L
 * Spin Integration Tests
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
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.PreProcess;
import build.spin.module.java.Java25CompilerPlugin;
import build.spin.module.junit.Java25JUnitPlugin;
import jakarta.inject.Inject;

import java.util.stream.Stream;

/**
 * Build customizations local to {@code spin-integration-tests} (see {@code CustomizationPlugin}).
 */
public class Build {

    /**
     * Forces {@code spin}'s own jlink image to be (re)built from current source before this
     * project's tests run, so {@code spin/.build/spin-<os>-<arch>/bin/spin.sh} reflects the
     * current tree instead of relying on a prior {@code ./mvnw install} run's leftover image
     * (or nothing at all, when this workspace is built via spin's own self-hosted task graph
     * rather than Maven).
     *
     * <p>A {@code @PreProcess} codependency of {@code Test} (not merely {@code @Before}): it must
     * run <em>only</em> when {@code Test} runs in this project, and a codependency's
     * {@code dependencies()} override - the sole way to declare the cross-project forcing edge to
     * {@code spin}'s {@code JavaLinker} - is folded into {@code Test}'s own forcing dependencies.
     */
    @PreProcess(Java25JUnitPlugin.Test.class)
    public static class ForceSpinSelfHost
        implements Task<Void> {

        @Inject
        private Project project;

        @Override
        public Stream<Reference> dependencies() {
            final Project spin = this.project.workspace().stream()
                .filter(p -> p.name().equals("spin"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "Could not locate the [spin] Project in the workspace to force its self-hosted jlink"));

            return Stream.of(Reference.of(spin, Java25CompilerPlugin.JavaLinker.class));
        }

        public void run() {
            // no-op: this Task exists purely to attach the dependencies() edge above -- the actual
            // work happens in spin's own JavaLinker task, forced as a dependency of Test.
        }
    }
}
