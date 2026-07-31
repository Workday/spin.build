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

class SourcePathKindExternalGeneratedTest {

    @TempDir
    Path projectRoot;

    @Test
    void detectExternal_noGeneratedSources_returnsEmpty() {
        assertThat(detectExternal()).isEmpty();
    }

    @Test
    void detectExternal_spinOutputExists_ignoresSpinPath() throws IOException {
        // spin's own generated-sources dir is annotation-processor output (the javac "-s" target
        // of a prior spin compile) -- never external, so it must never be surfaced here.
        Files.createDirectories(projectRoot.resolve(".build/main/generated-sources"));

        assertThat(detectExternal()).isEmpty();
    }

    @Test
    void detectExternal_mavenOutputExists_returnsEachSubdirectory() throws IOException {
        final Path annotations = projectRoot.resolve("target/generated-sources/annotations");
        final Path protobuf = projectRoot.resolve("target/generated-sources/protobuf");
        Files.createDirectories(annotations);
        Files.createDirectories(protobuf);

        assertThat(detectExternal()).containsExactlyInAnyOrder(annotations, protobuf);
    }

    @Test
    void detectExternal_spinAndMavenBothExist_stillReturnsMavenSubdirectories() throws IOException {
        // unlike SourcePathKind.GENERATED, the presence of spin's own output does not suppress
        // the Maven scan -- Maven-generated content (e.g. protobuf, ANTLR) is unrelated to spin's
        // annotation-processor output and must always be surfaced.
        Files.createDirectories(projectRoot.resolve(".build/main/generated-sources"));
        final Path protobuf = projectRoot.resolve("target/generated-sources/protobuf");
        Files.createDirectories(protobuf);

        assertThat(detectExternal()).containsExactly(protobuf);
    }

    private PathSet detectExternal() {
        return SourcePathKind.detectExternalGenerated(projectRoot);
    }
}
