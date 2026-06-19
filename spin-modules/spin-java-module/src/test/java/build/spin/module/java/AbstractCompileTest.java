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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractCompileTest {

    @TempDir
    Path tempDir;

    @Test
    void emptySourceResult_createsTargetDirectory() throws Exception {
        final Path target = tempDir.resolve("classes");

        AbstractCompile.emptySourceResult(target);

        assertThat(target).isDirectory();
    }

    @Test
    void emptySourceResult_returnsPathSetContainingTarget() throws Exception {
        final Path target = tempDir.resolve("classes");

        final var result = AbstractCompile.emptySourceResult(target);

        assertThat(result).containsExactly(target);
    }

    @Test
    void emptySourceResult_targetAlreadyExists_doesNotThrow() throws Exception {
        final Path target = tempDir.resolve("classes");
        Files.createDirectories(target);

        assertThat(AbstractCompile.emptySourceResult(target)).containsExactly(target);
    }
}
