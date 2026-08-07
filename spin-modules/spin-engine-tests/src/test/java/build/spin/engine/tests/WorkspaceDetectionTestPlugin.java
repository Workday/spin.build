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
import build.spin.engine.DefaultEngine;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal {@link Plugin} whose {@link MetaClass#isDetectedIn(Path)} matches only a directory that
 * directly contains {@value #MARKER}. Used by {@link DefaultEngineWorkspacePathTests} to prove
 * {@link DefaultEngine#getWorkspacePath} resolves to the directory that actually contains the
 * marker, not some higher ancestor.
 */
public class WorkspaceDetectionTestPlugin implements Plugin {

    public static final String MARKER = "workspace-detection-test.marker";

    public static class MetaClass implements Plugin.MetaClass {
        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.isDirectory(path) && Files.exists(path.resolve(MARKER));
        }
    }
}
