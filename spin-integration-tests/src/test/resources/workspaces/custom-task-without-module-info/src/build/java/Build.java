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
import build.spin.Task;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A build customization with no {@code module-info.java} alongside it. When spin runs from its own
 * jlink runtime image, CustomizationPlugin must still compile this against the Spin API
 * ({@code build.spin.Task}, {@code Project}) and {@code jakarta.inject}, resolving those from spin's
 * own image via {@code javac --system} rather than a hand-declared set of {@code requires} clauses.
 *
 * <p>NOTE: this fixture is duplicated in {@code spin-java-module-tests} (same tree, under
 * {@code workspaces/custom-task-without-module-info/}), where an in-process {@code Engine} test
 * exercises the module-path branch of the same code. Keep the two in sync.
 */
public class Build {

    @Named("greet")
    public static class Greet
        implements Task<Void> {

        public void greet(final Project project) throws Exception {
            final Path marker = project.path().resolve(".build").resolve("greeting.txt");
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "hello custom task without a module-info");
        }
    }
}
