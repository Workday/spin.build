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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces a {@code DefaultEngine#createProject} bug where a non-root {@link Project}'s own
 * {@code .spinignore} (backed by {@code build.spin.module.configuration.ConfigurationResource}, a
 * {@link build.spin.Resource} detected in every {@link Project}) was silently never read: the
 * {@code ProjectResourceResolver} added to a {@link Project}'s injection {@code Context} was
 * registered <strong>before</strong> that {@link Project}'s own {@link build.spin.Resource}s were
 * created, so it resolved the request to construct this {@link Project}'s own
 * {@code ConfigurationResource} by handing back the nearest ancestor's already-attached instance
 * instead - meaning every non-root {@link Project} in a {@link Workspace} actually shared the root's
 * {@code ConfigurationResource} (and, therefore, its {@code .spinignore} rules, or the lack of any).
 * <p>
 * This test does not reference {@code ConfigurationResource} directly; {@code spin-configuration-module}
 * is only present on the module path (via an explicit {@code requires build.spin.module.configuration},
 * matching the real {@code build.spin.application} module's own {@code requires transitive} of every
 * plugin module) so {@code ServiceLoader} discovers it, exactly as production spin does.
 * {@code spin-clean-module} is required alongside it for the same reason - it too contributes a
 * {@link build.spin.Resource} detected in every {@link Project}, and both build paths (Maven and
 * spin's own self-hosted graph) must agree on the discovered plugin set. The bug is instead observed
 * the way it actually manifested: a directory that would otherwise become its own child
 * {@link Project} is excluded by a {@code .spinignore} rule that lives only in its parent
 * {@link Project} - not the {@link Workspace} root.
 */
class DefaultEngineResourceScopingTests {

    private void write(final Path path, final String content) throws IOException {
        Files.writeString(path, content);
    }

    @Test
    void shouldHonorASpinIgnoreDefinedInANonRootProjectNotJustTheWorkspaceRoot(@TempDir final Path root)
        throws IOException {

        // establish the Workspace boundary at "root" itself, so discovery never climbs into real
        // filesystem ancestors above the @TempDir
        write(root.resolve(".spinignore"), "");

        // "child" becomes its own Project (detected via WorkspaceDetectionTestPlugin's marker) and
        // defines its own .spinignore excluding "grandchild" - a rule that must be honored by
        // "child"'s own ConfigurationResource, not silently ignored because it's not the root's
        final Path child = Files.createDirectory(root.resolve("child"));
        Files.createFile(child.resolve(WorkspaceDetectionTestPlugin.MARKER));
        write(child.resolve(".spinignore"), "**/grandchild");

        // "grandchild" would become its own child Project of "child" if the exclusion above were
        // (as the bug caused) silently inert
        final Path grandchild = Files.createDirectory(child.resolve("grandchild"));
        Files.createFile(grandchild.resolve(WorkspaceDetectionTestPlugin.MARKER));

        final Engine engine = new DefaultEngine(
            Thread.currentThread().getContextClassLoader(),
            FileSystems.getDefault(),
            Configuration.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        final Workspace workspace = engine.createWorkspace(root).orElseThrow();

        assertThat(workspace.children().map(Project::path))
            .as("the child Project must be discovered")
            .containsExactly(child);

        final Project childProject = workspace.children().findFirst().orElseThrow();

        assertThat(childProject.children())
            .as("child's own .spinignore must exclude \"grandchild\" - if child's "
                + "ConfigurationResource were (as the bug caused) actually the root's shared "
                + "instance instead of its own, this rule would be silently never read and "
                + "\"grandchild\" would wrongly appear here as a discovered Project")
            .isEmpty();
    }
}
