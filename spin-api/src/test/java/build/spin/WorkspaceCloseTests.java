package build.spin;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the default {@link Workspace#close()} implementation.
 */
class WorkspaceCloseTests {

    /**
     * Creates a {@link Workspace} mock whose real default methods (including {@link Workspace#close()} and
     * {@link Project#extensions(Class)}) are called, and whose {@link Workspace#walk(Visitor)} invokes the
     * {@link Visitor} against the mock itself, as if it were a single-node tree with no plugins or resources.
     *
     * @return the {@link Workspace} mock
     */
    private static Workspace mockWorkspace() {
        final Workspace workspace = mock(Workspace.class, CALLS_REAL_METHODS);

        doAnswer(invocation -> {
            final Visitor<? super Project> visitor = invocation.getArgument(0);
            visitor.onLeaving(workspace);
            return null;
        }).when(workspace).walk(any());

        when(workspace.plugins()).thenReturn(Stream.empty());
        when(workspace.resources()).thenReturn(Stream.empty());

        return workspace;
    }

    /**
     * Verifies that {@link Workspace#close()} actually calls {@code close()} on each
     * {@link AutoCloseable} plugin rather than recursing into itself.
     */
    @Test
    void shouldCloseAutoCloseablePlugins() throws Exception {
        final boolean[] closed = {false};

        final class TestPlugin implements Plugin, AutoCloseable {
            @Override
            public void close() {
                closed[0] = true;
            }
        }

        final Workspace workspace = mockWorkspace();
        when(workspace.plugins()).thenReturn(Stream.of(new TestPlugin()));

        workspace.close();

        assertThat(closed[0]).withFailMessage("AutoCloseable plugin must be closed").isTrue();
    }

    /**
     * Verifies that {@link Workspace#close()} also calls {@code close()} on each {@link AutoCloseable}
     * {@link Resource}, not just {@link AutoCloseable} plugins.
     */
    @Test
    void shouldCloseAutoCloseableResources() throws Exception {
        final boolean[] closed = {false};

        final class TestResource implements Resource, AutoCloseable {
            @Override
            public void close() {
                closed[0] = true;
            }
        }

        final Workspace workspace = mockWorkspace();
        when(workspace.resources()).thenReturn(Stream.of(new TestResource()));

        workspace.close();

        assertThat(closed[0]).withFailMessage("AutoCloseable resource must be closed").isTrue();
    }

    /**
     * Verifies that {@link Workspace#close()} does not throw a {@link StackOverflowError}
     * when there are no plugins.
     */
    @Test
    void shouldNotStackOverflowWithNoPlugins() {
        final Workspace workspace = mockWorkspace();
        assertDoesNotThrow(workspace::close);
    }
}
