package build.spin;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link Reference}.
 */
class ReferenceTests {

    /**
     * Verifies that {@link Reference#equals(Object)} does not throw a {@link NullPointerException}
     * when {@link Reference#getPluginClass()} returns {@code null} (i.e. the task is not a static
     * inner class of a {@link Plugin}).
     */
    @Test
    @SuppressWarnings("unchecked")
    void equalsDoesNotThrowWhenPluginClassIsNull() {
        // A local class has getDeclaringClass() == null, so getPluginClass() will return null.
        class LocalTask implements Task<String> {
            public String compute() {
                return "";
            }
        }

        final Project project = mock(Project.class);
        when(project.name()).thenReturn("test-project");

        final Reference ref = Reference.of(project, (Class<? extends Task<?>>) (Class<?>) LocalTask.class);

        assertThat(ref.getPluginClass()).isNull();
        assertDoesNotThrow(() -> ref.equals(ref));
        assertThat(ref.equals(ref)).isTrue();
    }

    /**
     * Verifies that two {@link Reference}s with null {@link Reference#getPluginClass()} and the
     * same task class are equal.
     */
    @Test
    @SuppressWarnings("unchecked")
    void twoReferencesWithNullPluginClassAreEqualWhenTaskClassMatches() {
        class LocalTask implements Task<String> {
            public String compute() {
                return "";
            }
        }

        final Project project = mock(Project.class);
        when(project.name()).thenReturn("test-project");

        final Class<? extends Task<?>> taskClass = (Class<? extends Task<?>>) (Class<?>) LocalTask.class;
        final Reference ref1 = Reference.of(project, taskClass);
        final Reference ref2 = Reference.of(project, taskClass);

        assertDoesNotThrow(() -> ref1.equals(ref2));
        assertThat(ref1.equals(ref2)).isTrue();
    }

    /**
     * Verifies that a {@link Reference} with a non-null plugin class does not equal one with a
     * null plugin class.
     */
    @Test
    @SuppressWarnings("unchecked")
    void referenceWithPluginClassDoesNotEqualReferenceWithNullPluginClass() {
        class LocalTask implements Task<String> {
            public String compute() {
                return "";
            }
        }

        final Project project = mock(Project.class);
        when(project.name()).thenReturn("test-project");

        // ref1: task class whose declaring class is null (local class)
        final Reference refWithNullPlugin = Reference.of(project,
            (Class<? extends Task<?>>) (Class<?>) LocalTask.class);

        // ref2: a custom Reference that explicitly declares a plugin class
        final Reference refWithPlugin = new Reference() {
            @Override
            public Project project() {
                return project;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends Task<?>> getTaskClass() {
                return (Class<? extends Task<?>>) (Class<?>) LocalTask.class;
            }

            @Override
            public Class<? extends Plugin> getPluginClass() {
                return Plugin.class;
            }
        };

        assertDoesNotThrow(() -> refWithNullPlugin.equals(refWithPlugin));
        assertThat(refWithNullPlugin.equals(refWithPlugin)).isFalse();
    }
}
