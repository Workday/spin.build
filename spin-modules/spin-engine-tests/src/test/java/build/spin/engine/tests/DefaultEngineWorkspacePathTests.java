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

import build.base.configuration.Configuration;
import build.spin.Engine;
import build.spin.engine.DefaultEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultEngine#getWorkspacePath}.
 */
class DefaultEngineWorkspacePathTests {

    /**
     * {@link DefaultEngine#getWorkspacePath} walks up from the given path looking for the nearest
     * ancestor a {@link build.spin.Plugin.MetaClass} detects. The ascent loop re-checked the
     * original leaf path on every iteration instead of the ancestor currently being examined, so
     * once a marker was found once, every higher ancestor was (wrongly) treated as a match too —
     * ending at the top of whatever ancestor chain was walked, not the directory that actually
     * contains the marker.
     */
    @Test
    void shouldResolveWorkspacePathToTheDirectoryContainingTheMarker(@TempDir final Path tempDir)
        throws IOException {

        // the marker only exists in "project" — every ancestor above it must be irrelevant
        final Path project = Files.createDirectories(tempDir.resolve("workspace/project"));
        Files.createFile(project.resolve(WorkspaceDetectionTestPlugin.MARKER));

        final Engine engine = new DefaultEngine(
            Thread.currentThread().getContextClassLoader(),
            FileSystems.getDefault(),
            Configuration.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        assertThat(engine.getWorkspacePath(project))
            .as("getWorkspacePath must resolve to the directory that actually contains the "
                + "Plugin marker [%s], not climb past it to some higher ancestor", project)
            .contains(project);
    }
}
