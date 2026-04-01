package build.spin;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for the default {@link Workspace#close()} implementation.
 */
class WorkspaceCloseTests {

    /**
     * Verifies that {@link Workspace#close()} actually calls {@code close()} on each
     * {@link AutoCloseable} plugin rather than recursing into itself.
     */
    @Test
    void shouldCloseAutoCloseablePlugins() throws Exception {
        final boolean[] closed = {false};
        final AutoCloseable plugin = () -> closed[0] = true;

        final Workspace workspace = new StubWorkspace(plugin);
        workspace.close();

        assertThat("AutoCloseable plugin must be closed", closed[0], is(true));
    }

    /**
     * Verifies that {@link Workspace#close()} does not throw a {@link StackOverflowError}
     * when there are no plugins.
     */
    @Test
    void shouldNotStackOverflowWithNoPlugins() {
        final Workspace workspace = new StubWorkspace(null);
        assertDoesNotThrow(workspace::close);
    }

    // ── Minimal stub ──────────────────────────────────────────────────────────

    /**
     * Minimal {@link Workspace} stub: only {@link #walk} and {@link #plugins(Class)}
     * are meaningful; everything else throws.
     */
    private static final class StubWorkspace implements Workspace {

        private final AutoCloseable plugin;

        StubWorkspace(final AutoCloseable plugin) {
            this.plugin = plugin;
        }

        @Override
        public void walk(final Visitor<? super Project> visitor) {
            // single-node tree: just this workspace
            visitor.onLeaving(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Stream<T> plugins(final Class<T> assignableTo) {
            if (plugin != null && assignableTo.isInstance(plugin)) {
                return (Stream<T>) Stream.of(plugin);
            }
            return Stream.empty();
        }

        // ── Unused stubs ───────────────────────────────────────────────────

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Engine engine() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Project> parent() {
            return Optional.empty();
        }

        @Override
        public int depth() {
            return 0;
        }

        @Override
        public Path path() {
            throw new UnsupportedOperationException();
        }

        @Override
        public build.base.configuration.Configuration options() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<Plugin> plugins() {
            return Stream.empty();
        }

        @Override
        public boolean contains(final Class<?> cls) {
            return false;
        }

        @Override
        public Stream<Resource> resources() {
            return Stream.empty();
        }

        @Override
        public Workspace workspace() {
            return this;
        }

        @Override
        public Stream<Project> stream(final Predicate<? super Project> p) {
            return Stream.of(this);
        }

        @Override
        public Stream<Project> stream() {
            return Stream.of(this);
        }

        @Override
        public Stream<Project> children() {
            return Stream.empty();
        }

        @Override
        public Stream<Invocable<?>> invocables() {
            return Stream.empty();
        }

        @Override
        public <T extends Plugin> Optional<T> getPlugin(final Class<T> c) {
            return Optional.empty();
        }

        @Override
        public <T extends Resource> Optional<T> getResource(final Class<T> c) {
            return Optional.empty();
        }

        @Override
        public <T extends Resource> Optional<T> findResource(final Class<T> c) {
            return Optional.empty();
        }

        @Override
        public boolean contains(final Path path) {
            return false;
        }

        @Override
        public boolean isIgnored(final Path path) {
            return false;
        }

        @Override
        public Stream<Invocable<?>> codependencies(final Reference ref) {
            return Stream.empty();
        }

        @Override
        public Optional<Project> getProject(final Path path) {
            return Optional.empty();
        }

        @Override
        public void treeify(final StringBuilder b, final String p, final String c, final Function<Project, String> f) {
        }

        @Override
        public int compareTo(final Project other) {
            return 0;
        }

        @Override
        public String toString() {
            return "StubWorkspace";
        }
    }
}
