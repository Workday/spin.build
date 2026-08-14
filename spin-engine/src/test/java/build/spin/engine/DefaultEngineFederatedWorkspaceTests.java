package build.spin.engine;

import build.base.configuration.Configuration;
import build.spin.Engine;
import build.spin.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultEngine#createWorkspace(List)} and its {@link DefaultEngine#commonAncestor(List)} helper.
 * <p>
 * This module's tests are patched into the {@code build.spin.engine} module and so cannot register a test-only
 * {@code Plugin.MetaClass} via {@code ServiceLoader} (JPMS ignores {@code META-INF/services} for named modules);
 * the equivalent coverage for actually-detected federated roots lives in {@code spin-modules/spin-engine-tests},
 * which is its own module with a {@code provides} clause.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
class DefaultEngineFederatedWorkspaceTests {

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
    void commonAncestorOfASinglePathIsThatPath() {
        final Path path = Path.of("/home/reed/code/workday/spin.build").toAbsolutePath().normalize();
        assertThat(DefaultEngine.commonAncestor(List.of(path))).isEqualTo(path);
    }

    @Test
    void commonAncestorOfSiblingPathsIsTheirSharedParent() {
        final Path a = Path.of("/home/reed/code/workday/spin.build").toAbsolutePath().normalize();
        final Path b = Path.of("/home/reed/code/workday/base.build").toAbsolutePath().normalize();
        assertThat(DefaultEngine.commonAncestor(List.of(a, b))).isEqualTo(a.getParent());
    }

    @Test
    void commonAncestorAcrossMultiplePathsIsTheDeepestSharedAncestor() {
        final Path a = Path.of("/home/reed/code/workday/spin.build").toAbsolutePath().normalize();
        final Path b = Path.of("/home/reed/code/workday/base.build").toAbsolutePath().normalize();
        final Path c = Path.of("/home/reed/code/deer/lang.build").toAbsolutePath().normalize();
        assertThat(DefaultEngine.commonAncestor(List.of(a, b, c))).isEqualTo(a.getParent().getParent());
    }

    @Test
    void commonAncestorWhenOnePathIsAnAncestorOfAnotherIsTheShallowerPath() {
        final Path parent = Path.of("/home/reed/code/workday").toAbsolutePath().normalize();
        final Path child = Path.of("/home/reed/code/workday/spin.build").toAbsolutePath().normalize();
        assertThat(DefaultEngine.commonAncestor(List.of(parent, child))).isEqualTo(parent);
        assertThat(DefaultEngine.commonAncestor(List.of(child, parent))).isEqualTo(parent);
    }

    @Test
    void createWorkspaceWithNoRootsIsEmpty() {
        assertThat(engine().createWorkspace(List.of())).isEmpty();
    }

    @Test
    void createWorkspaceWithASingleRootDelegatesToTheSingleRootOverload(@TempDir final Path tempDir) {
        final Engine engine = engine();

        // neither overload can detect a Project without a Plugin/Resource module on the classpath,
        // but both must agree (single-root createWorkspace(List.of(path)) delegates to createWorkspace(path))
        assertThat(engine.createWorkspace(List.of(tempDir)))
            .isEqualTo(engine.createWorkspace(tempDir));
    }

    @Test
    void createWorkspaceWithMultipleUndetectableRootsIsEmpty(@TempDir final Path tempDirA,
                                                             @TempDir final Path tempDirB) {

        final Optional<Workspace> workspace = engine().createWorkspace(List.of(tempDirA, tempDirB));

        // no Extension.MetaClass is registered on this module's test classpath, so neither root is
        // ever detected as containing a Project — the federated Workspace is therefore never created
        assertThat(workspace).isEmpty();
    }

    @Test
    void createWorkspaceRejectsANestedRoot(@TempDir final Path tempDir) throws IOException {
        final Path nested = Files.createDirectory(tempDir.resolve("nested"));

        assertThatThrownBy(() -> engine().createWorkspace(List.of(tempDir, nested)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlap");
    }

    @Test
    void createWorkspaceRejectsADuplicateRoot(@TempDir final Path tempDir) {
        assertThatThrownBy(() -> engine().createWorkspace(List.of(tempDir, tempDir)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlap");
    }
}
