package build.spin.common.task;

/*-
 * #%L
 * Spin Common Library
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

import build.base.io.PathSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SourcePathKindGeneratedTest {

    @TempDir
    Path projectRoot;

    @Test
    void detect_noGeneratedSources_returnsEmpty() {
        assertThat(detect()).isEmpty();
    }

    @Test
    void detect_spinOutputExists_returnsSpinPath() throws IOException {
        final Path spinGenerated = projectRoot.resolve(".build/main/generated-sources");
        Files.createDirectories(spinGenerated);

        assertThat(detect()).containsExactly(spinGenerated);
    }

    @Test
    void detect_mavenOutputExists_returnsEachSubdirectory() throws IOException {
        final Path annotations = projectRoot.resolve("target/generated-sources/annotations");
        final Path jt = projectRoot.resolve("target/generated-sources/jt");
        Files.createDirectories(annotations);
        Files.createDirectories(jt);

        assertThat(detect()).containsExactlyInAnyOrder(annotations, jt);
    }

    @Test
    void detect_mavenOutputExistsButEmpty_returnsEmpty() throws IOException {
        Files.createDirectories(projectRoot.resolve("target/generated-sources"));

        assertThat(detect()).isEmpty();
    }

    @Test
    void detect_mavenOutputContainsFilesNotDirs_returnsEmpty() throws IOException {
        final Path mavenGenerated = projectRoot.resolve("target/generated-sources");
        Files.createDirectories(mavenGenerated);
        Files.createFile(mavenGenerated.resolve("Foo.java"));

        assertThat(detect()).isEmpty();
    }

    @Test
    void detect_spinAndMavenBothExist_prefersSpinOutput() throws IOException {
        final Path spinGenerated = projectRoot.resolve(".build/main/generated-sources");
        Files.createDirectories(spinGenerated);
        Files.createDirectories(projectRoot.resolve("target/generated-sources/annotations"));

        assertThat(detect()).containsExactly(spinGenerated);
    }

    private PathSet detect() {
        return SourcePathKind.detectGenerated(projectRoot, ".build");
    }
}
