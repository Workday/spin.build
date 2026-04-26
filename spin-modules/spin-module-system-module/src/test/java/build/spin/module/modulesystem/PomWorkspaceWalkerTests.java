package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
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

import build.base.telemetry.TelemetryRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PomWorkspaceWalker}. Uses a simple collecting visitor to verify which
 * (module-name list, coordinate) tuples the walker emits for a given workspace layout.
 * <p>
 * These tests exercise the shared walking algorithm that backs both {@link PomBasedModuleCatalog}
 * and {@link PomBasedModuleVersioning} — the Catalog/Versioning-specific test files only verify
 * their visitor wiring, not the walker behavior.
 */
class PomWorkspaceWalkerTests {

    private static final TelemetryRecorder RECORDER = mock(TelemetryRecorder.class);

    // -------------------------------------------------------------------------
    // basic walking
    // -------------------------------------------------------------------------

    @Test
    void walk_emitsNothingWhenNoPomExists(@TempDir final Path workspace) {
        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, workspace.resolve("missing-repo"), RECORDER, visitor);
        assertThat(visitor.visits).isEmpty();
    }

    @Test
    void walk_visitsDirectDependencyUnderGroupIdAndDerivedName(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-api</artifactId>
                  <version>5.10.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        final Visit v = visitor.forCoordinate("org.junit.jupiter", "junit-jupiter-api");
        assertThat(v.version).isEqualTo("5.10.0");
        assertThat(v.names).contains("org.junit.jupiter", "junit.jupiter.api");
    }

    @Test
    void walk_resolvesPropertyReferencedVersion(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <properties>
                <assertj.version>3.25.0</assertj.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.assertj</groupId>
                  <artifactId>assertj-core</artifactId>
                  <version>${assertj.version}</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        assertThat(visitor.forCoordinate("org.assertj", "assertj-core").version).isEqualTo("3.25.0");
    }

    @Test
    void walk_resolvesDependencyManagementVersionForSubmodule(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <properties>
                <base.version>0.22.1</base.version>
              </properties>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>build.base</groupId>
                    <artifactId>base-marshalling</artifactId>
                    <version>${base.version}</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
            """);

        final Path submodule = Files.createDirectory(workspace.resolve("sub"));
        writePom(submodule.resolve("pom.xml"), """
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>root</artifactId>
                <version>1.0.0</version>
              </parent>
              <artifactId>sub</artifactId>
              <dependencies>
                <dependency>
                  <groupId>build.base</groupId>
                  <artifactId>base-marshalling</artifactId>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        assertThat(visitor.forCoordinate("build.base", "base-marshalling").version).isEqualTo("0.22.1");
    }

    // -------------------------------------------------------------------------
    // derivation heuristics — positive cases
    // -------------------------------------------------------------------------

    @Test
    void walk_derivesGroupIdPlusLastHyphenSegmentForDependency(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-api</artifactId>
                  <version>5.10.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        // org.junit.jupiter + last segment "api" -> org.junit.jupiter.api
        assertThat(visitor.forCoordinate("org.junit.jupiter", "junit-jupiter-api").names)
            .contains("org.junit.jupiter.api");
    }

    @Test
    void walk_derivesGroupPrefixedModuleNameForDependency(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>build.base</groupId>
                  <artifactId>base-telemetry-foundation</artifactId>
                  <version>0.21.5</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        // build.base + artifactId-minus-groupId-prefix -> build.base.telemetry.foundation
        assertThat(visitor.forCoordinate("build.base", "base-telemetry-foundation").names)
            .contains("build.base.telemetry.foundation");
    }

    @Test
    void walk_derivesGroupSuffixedModuleNameForDependency(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>build.codemodel</groupId>
                  <artifactId>jdk-codemodel</artifactId>
                  <version>0.19.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        // build.codemodel + artifactId-minus-groupId-suffix -> build.codemodel.jdk
        assertThat(visitor.forCoordinate("build.codemodel", "jdk-codemodel").names)
            .contains("build.codemodel.jdk");
    }

    @Test
    void walk_derivesGroupParentWithLastArtifactSegmentForDependency(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>com.fasterxml.jackson.core</groupId>
                  <artifactId>jackson-databind</artifactId>
                  <version>2.18.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        // parent groupId + last artifact segment -> com.fasterxml.jackson.databind
        assertThat(visitor.forCoordinate("com.fasterxml.jackson.core", "jackson-databind").names)
            .contains("com.fasterxml.jackson.databind");
    }

    // -------------------------------------------------------------------------
    // self-artifact handling
    // -------------------------------------------------------------------------

    @Test
    void walk_skipsRootAggregatorPomAsSelf(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>aggregator</artifactId>
              <version>1.0.0</version>
              <packaging>pom</packaging>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        // root pom has no deps and its own artifact is deliberately NOT visited
        assertThat(visitor.visits).isEmpty();
    }

    @Test
    void walk_visitsChildPomSelfArtifactWithParentInheritance(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>parent</artifactId>
              <version>2.0.0</version>
            </project>
            """);

        final Path childDir = workspace.resolve("child-module");
        Files.createDirectories(childDir);
        writePom(childDir.resolve("pom.xml"), """
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>parent</artifactId>
                <version>2.0.0</version>
              </parent>
              <artifactId>child-module</artifactId>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        // child's groupId/version inherited from <parent>; names come from derivation
        final Visit v = visitor.forCoordinate("com.example", "child-module");
        assertThat(v.version).isEqualTo("2.0.0");
        assertThat(v.names).contains("com.example", "child.module");
    }

    @Test
    void walk_prefersModuleInfoNameOverDerivationForSelfArtifact(@TempDir final Path workspace) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>parent</artifactId>
              <version>2.0.0</version>
            </project>
            """);

        final Path childDir = workspace.resolve("child-module");
        Files.createDirectories(childDir.resolve("src/main/java"));
        writePom(childDir.resolve("pom.xml"), """
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>parent</artifactId>
                <version>2.0.0</version>
              </parent>
              <artifactId>child-module</artifactId>
            </project>
            """);
        Files.writeString(childDir.resolve("src/main/java/module-info.java"),
            "module com.example.totally.custom {\n}\n");

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, missingRepo(workspace), RECORDER, visitor);

        // when module-info.java is present, its declared name is the ONLY name the walker emits
        final Visit v = visitor.forCoordinate("com.example", "child-module");
        assertThat(v.names).containsExactly("com.example.totally.custom");
    }

    // -------------------------------------------------------------------------
    // BFS transitive resolution & scope filtering
    // -------------------------------------------------------------------------

    @Test
    void walk_followsTransitiveDepsFromLocalRepo(@TempDir final Path workspace,
                                                 @TempDir final Path localRepo) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>build.codemodel</groupId>
                  <artifactId>jdk-codemodel</artifactId>
                  <version>0.19.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final Path pomDir = localRepo.resolve("build/codemodel/jdk-codemodel/0.19.0");
        Files.createDirectories(pomDir);
        writePom(pomDir.resolve("jdk-codemodel-0.19.0.pom"), """
            <project>
              <groupId>build.codemodel</groupId>
              <artifactId>jdk-codemodel</artifactId>
              <version>0.19.0</version>
              <dependencies>
                <dependency>
                  <groupId>build.codemodel</groupId>
                  <artifactId>codemodel-expression</artifactId>
                  <version>0.19.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, localRepo, RECORDER, visitor);

        assertThat(visitor.forCoordinate("build.codemodel", "codemodel-expression").version)
            .isEqualTo("0.19.0");
    }

    @Test
    void walk_excludesTestScopedTransitiveDepsOfExternalArtifacts(@TempDir final Path workspace,
                                                                  @TempDir final Path localRepo) throws Exception {
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>build.codemodel</groupId>
                  <artifactId>jdk-codemodel</artifactId>
                  <version>0.19.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final Path jdkDir = localRepo.resolve("build/codemodel/jdk-codemodel/0.19.0");
        Files.createDirectories(jdkDir);
        writePom(jdkDir.resolve("jdk-codemodel-0.19.0.pom"), """
            <project>
              <groupId>build.codemodel</groupId>
              <artifactId>jdk-codemodel</artifactId>
              <version>0.19.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.only.in.external.test</groupId>
                  <artifactId>external-test-only</artifactId>
                  <version>9.9.9</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, localRepo, RECORDER, visitor);

        assertThat(visitor.coordinateVisited("org.only.in.external.test", "external-test-only")).isFalse();
    }

    @Test
    void walk_includesTestScopedWorkspaceDepsInBfs(@TempDir final Path workspace,
                                                   @TempDir final Path localRepo) throws Exception {
        // workspace has a test-scoped dep on base-assertion; base-assertion's pom lists base-retryable
        writePom(workspace.resolve("pom.xml"), """
            <project>
              <groupId>com.example</groupId>
              <artifactId>root</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>build.base</groupId>
                  <artifactId>base-assertion</artifactId>
                  <version>1.0.0</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """);

        final Path assertionDir = localRepo.resolve("build/base/base-assertion/1.0.0");
        Files.createDirectories(assertionDir);
        writePom(assertionDir.resolve("base-assertion-1.0.0.pom"), """
            <project>
              <groupId>build.base</groupId>
              <artifactId>base-assertion</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>build.base</groupId>
                  <artifactId>base-retryable</artifactId>
                  <version>1.0.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        final CollectingVisitor visitor = new CollectingVisitor();
        PomWorkspaceWalker.walk(workspace, localRepo, RECORDER, visitor);

        // base-retryable is only reachable through the test-scoped workspace dep chain
        assertThat(visitor.forCoordinate("build.base", "base-retryable").names)
            .contains("build.base.retryable");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a repo path that doesn't exist — used by tests that don't need Phase 2 BFS.
     */
    private static Path missingRepo(final Path workspace) {
        return workspace.resolve("__no_repo__");
    }

    private static void writePom(final Path path, final String content) throws Exception {
        Files.writeString(path, content);
    }

    /**
     * Simple visitor that captures every visit made by the walker for later inspection.
     */
    private static final class CollectingVisitor implements PomWorkspaceWalker.CoordinateVisitor {

        final List<Visit> visits = new ArrayList<>();

        @Override
        public void accept(final List<String> moduleNames,
                           final String groupId,
                           final String artifactId,
                           final String resolvedVersion) {
            this.visits.add(new Visit(List.copyOf(moduleNames), groupId, artifactId, resolvedVersion));
        }

        Visit forCoordinate(final String groupId, final String artifactId) {
            return this.visits.stream()
                .filter(v -> v.groupId.equals(groupId) && v.artifactId.equals(artifactId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "Expected visit for [" + groupId + ":" + artifactId + "] not found among " + this.visits));
        }

        boolean coordinateVisited(final String groupId, final String artifactId) {
            return this.visits.stream()
                .anyMatch(v -> v.groupId.equals(groupId) && v.artifactId.equals(artifactId));
        }
    }

    private record Visit(List<String> names, String groupId, String artifactId, String version) {
    }
}
