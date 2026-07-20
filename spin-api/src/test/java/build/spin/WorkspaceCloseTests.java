package build.spin;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        final var workspace = mock(Workspace.class, CALLS_REAL_METHODS);

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

        final var workspace = mockWorkspace();
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

        final var workspace = mockWorkspace();
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
        final var workspace = mockWorkspace();
        assertDoesNotThrow(workspace::close);
    }

    /**
     * Verifies that {@link Workspace#close()} continues closing the remaining {@link AutoCloseable} extensions
     * after an earlier one fails to close, rather than aborting.
     */
    @Test
    void shouldContinueClosingRemainingExtensionsAfterAFailure() {
        final boolean[] secondClosed = {false};

        final class FailingPlugin implements Plugin, AutoCloseable {
            @Override
            public void close() throws Exception {
                throw new IllegalStateException("first plugin failed to close");
            }
        }

        final class SecondPlugin implements Plugin, AutoCloseable {
            @Override
            public void close() {
                secondClosed[0] = true;
            }
        }

        final var workspace = mockWorkspace();
        when(workspace.plugins()).thenReturn(Stream.of(new FailingPlugin(), new SecondPlugin()));

        assertThrows(RuntimeException.class, workspace::close);

        assertThat(secondClosed[0])
            .withFailMessage("The second AutoCloseable plugin must still be closed after the first fails")
            .isTrue();
    }

    /**
     * Verifies that {@link Workspace#close()} reports every failure to close an {@link AutoCloseable} extension:
     * the first as the thrown exception's cause, and any subsequent failures as
     * {@linkplain Throwable#getSuppressed() suppressed} exceptions.
     */
    @Test
    void shouldReportAllFailuresWhenMultipleExtensionsFailToClose() {

        final class FailingPlugin implements Plugin, AutoCloseable {

            private final String message;

            FailingPlugin(final String message) {
                this.message = message;
            }

            @Override
            public void close() throws Exception {
                throw new IllegalStateException(message);
            }
        }

        final var workspace = mockWorkspace();
        when(workspace.plugins()).thenReturn(Stream.of(new FailingPlugin("first"), new FailingPlugin("second")));

        final var thrown = assertThrows(RuntimeException.class, workspace::close);

        assertThat(thrown.getCause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("first");

        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0].getCause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("second");
    }
}
