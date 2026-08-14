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
import build.spin.Project;
import build.spin.Workspace;
import build.spin.engine.DefaultEngine;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link Engine#createWorkspace(List)}, using two real, {@link FederatedTestPlugin}-detected
 * physical roots ({@code workspaces/federated-a} and {@code workspaces/federated-b}) to verify that federation
 * actually produces sibling {@link Project}s sharing one {@link Workspace}, not just the empty/undetected paths
 * covered by the unit tests in {@code spin-engine}.
 */
class FederatedWorkspaceTests {

    private Path workspacesRoot() {
        final URL location = FederatedWorkspaceTests.class.getProtectionDomain().getCodeSource().getLocation();
        return Paths.get(location.getPath()).resolve("workspaces");
    }

    private Engine engine() {
        return new DefaultEngine(
            Thread.currentThread().getContextClassLoader(),
            FileSystems.getDefault(),
            Configuration.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    }

    @Test
    void createWorkspaceFederatesTwoDetectedRootsAsSiblingProjectsOfOneWorkspace() {

        final Path rootA = workspacesRoot().resolve("federated-a");
        final Path rootB = workspacesRoot().resolve("federated-b");

        final Optional<Workspace> workspace = engine().createWorkspace(List.of(rootA, rootB));

        assertThat(workspace).isPresent();

        final Workspace federatedWorkspace = workspace.get();

        // both roots must be discovered as sibling children of the one federated Workspace ...
        assertThat(federatedWorkspace.children().map(Project::path))
            .containsExactlyInAnyOrder(rootA.toAbsolutePath().normalize(), rootB.toAbsolutePath().normalize());

        // ... sharing it as their common workspace() ...
        federatedWorkspace.children().forEach(
            project -> assertThat(project.workspace()).isSameAs(federatedWorkspace));

        // ... and stream() from the federated root yields the union of both roots' trees
        assertThat(federatedWorkspace.stream()).contains(federatedWorkspace).hasSize(3);
    }
}
