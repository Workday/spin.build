package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractDetectResolutionTest {

    @TempDir
    Path projectRoot;

    @Test
    void resolveCompiledOutput_noneExists_returnsEmpty() {
        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes")).isEmpty();
    }

    @Test
    void resolveCompiledOutput_spinOutputExists_returnsSpinPath() throws IOException {
        final Path spinOutput = projectRoot.resolve(".build/main/classes");
        Files.createDirectories(spinOutput);

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(spinOutput);
    }

    @Test
    void resolveCompiledOutput_mavenClassesExist_returnsMavenPath() throws IOException {
        final Path mavenClasses = projectRoot.resolve("target/classes");
        Files.createDirectories(mavenClasses);

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(mavenClasses);
    }

    @Test
    void resolveCompiledOutput_gradleClassesExist_returnsGradlePath() throws IOException {
        final Path gradleClasses = projectRoot.resolve("build/classes/java/main");
        Files.createDirectories(gradleClasses);

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(gradleClasses);
    }

    @Test
    void resolveCompiledOutput_spinAndMavenExist_prefersSpinOutput() throws IOException {
        final Path spinOutput = projectRoot.resolve(".build/main/classes");
        final Path mavenClasses = projectRoot.resolve("target/classes");
        Files.createDirectories(spinOutput);
        Files.createDirectories(mavenClasses);

        assertThat(AbstractDetectResolution.resolveCompiledOutput(projectRoot, ".build", "classes"))
            .contains(spinOutput);
    }
}
