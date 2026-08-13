package build.spin.engine;

/*-
 * #%L
 * Spin Engine
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link DefaultEngine}'s project-discovery helpers.
 */
class DefaultEngineTests {

    private DefaultEngine engine() {
        return new DefaultEngine(
            Thread.currentThread().getContextClassLoader(),
            FileSystems.getDefault(),
            Configuration.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    }

    /**
     * Exercises the actual recursion in {@code createProject}, not just the {@code
     * isBuildOutputDirectory} predicate in isolation: a symlink inside {@code target/} pointing
     * back to the workspace root turns any recursion into {@code target/} into an infinite cycle
     * (eventually a {@link StackOverflowError}), so a passing test proves {@code target/} itself was
     * never listed.
     */
    @Test
    void createProjectDoesNotRecurseIntoADiscoveredMavenTargetDirectory(@TempDir final Path workspace)
        throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        final Path target = Files.createDirectory(workspace.resolve("target"));
        Files.createSymbolicLink(target.resolve("loop"), workspace);

        final DefaultEngine engine = engine();

        assertThatCode(() -> engine.createProject(Optional.empty(), workspace)).doesNotThrowAnyException();
    }

    /**
     * A build tool copying or writing into its own output directory — e.g. Maven's resource plugin
     * copying {@code src/test/resources} verbatim into {@code target/test-classes}, including any
     * nested {@code module-info.java} files — must never have that copy independently rediscovered
     * by {@code DefaultEngine#createProject} as a second, distinct candidate project.
     */
    @Test
    void isBuildOutputDirectory_recognizesMavenTargetDirectoryBesideAPom(@TempDir final Path workspace)
        throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        final Path target = Files.createDirectory(workspace.resolve("target"));

        assertThat(DefaultEngine.isBuildOutputDirectory(target)).isTrue();
    }

    @Test
    void isBuildOutputDirectory_recognizesGradleBuildDirectoryBesideABuildScript(@TempDir final Path workspace)
        throws Exception {
        Files.writeString(workspace.resolve("build.gradle"), "");
        final Path build = Files.createDirectory(workspace.resolve("build"));

        assertThat(DefaultEngine.isBuildOutputDirectory(build)).isTrue();
    }

    @Test
    void isBuildOutputDirectory_recognizesGradleBuildDirectoryBesideAKotlinBuildScript(
        @TempDir final Path workspace) throws Exception {
        Files.writeString(workspace.resolve("build.gradle.kts"), "");
        final Path build = Files.createDirectory(workspace.resolve("build"));

        assertThat(DefaultEngine.isBuildOutputDirectory(build)).isTrue();
    }

    /**
     * The critical case: a directory literally named {@code build} that is NOT a Gradle build
     * directory — e.g. a Java package segment, as in this very codebase's own
     * {@code src/main/java/build/...} — must not be pruned from discovery just because its name
     * matches. Without the adjacent-build-script check, this would silently break discovery of any
     * source tree under a {@code build.*} package.
     */
    @Test
    void isBuildOutputDirectory_doesNotMatchADirectoryNamedBuildWithNoAdjacentGradleScript(
        @TempDir final Path workspace) throws Exception {
        final Path packageSegment = Files.createDirectory(workspace.resolve("build"));

        assertThat(DefaultEngine.isBuildOutputDirectory(packageSegment)).isFalse();
    }

    @Test
    void isBuildOutputDirectory_doesNotMatchADirectoryNamedTargetWithNoAdjacentPom(
        @TempDir final Path workspace) throws Exception {
        final Path target = Files.createDirectory(workspace.resolve("target"));

        assertThat(DefaultEngine.isBuildOutputDirectory(target)).isFalse();
    }

    @Test
    void isBuildOutputDirectory_doesNotMatchUnrelatedDirectoryNames(@TempDir final Path workspace)
        throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        final Path targets = Files.createDirectory(workspace.resolve("targets"));

        assertThat(DefaultEngine.isBuildOutputDirectory(targets)).isFalse();
    }
}
