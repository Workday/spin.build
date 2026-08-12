package build.spin.common;

import build.base.telemetry.TelemetryRecorder;
import build.codemodel.dependency.injection.Context;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultInstruction}.
 */
class DefaultInstructionTests {

    /**
     * A minimal task for use in tests.
     */
    static class SimpleTask implements Task<String> {
        public String compute() {
            return "simple";
        }
    }

    /**
     * A minimal plugin for use in tests.
     */
    interface StubPlugin extends Plugin {}

    /**
     * Builds a {@link DefaultInstruction} wired with mocks that produce no dependencies.
     */
    @SuppressWarnings("unchecked")
    private DefaultInstruction<String> buildInstruction(final Project project,
                                                        final TelemetryRecorder recorder) {
        final Context context = mock(Context.class);
        final SimpleTask task = new SimpleTask();
        when(context.create(any(Class.class))).thenReturn(task);

        final Invocable<String> invocable = mock(Invocable.class);
        when(invocable.createTask(context)).thenReturn(task);
        when(invocable.dependencies()).thenAnswer(__ -> Stream.empty());
        when(invocable.getProject()).thenReturn(project);
        when(invocable.getReference()).thenReturn(Reference.of(project, SimpleTask.class));

        when(project.invocables()).thenAnswer(__ -> Stream.empty());
        when(project.codependencies(any())).thenAnswer(__ -> Stream.empty());
        when(project.name()).thenReturn("test-project");

        return new DefaultInstruction<>(0, invocable, recorder, context);
    }

    /**
     * Verifies that a dependency is added when its plugin is present in the project.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldAddDependencyWhenPluginIsPresent() {
        final Project project = mock(Project.class);
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);
        final DefaultInstruction<String> instruction = buildInstruction(project, recorder);

        final Plugin plugin = mock(StubPlugin.class);
        when(project.getPlugin(any())).thenReturn(Optional.of((StubPlugin) plugin));

        final Reference dep = Reference.of(project, SimpleTask.class);
        instruction.addDependency(dep);

        assertThat(instruction.dependencies()).contains(dep);
        verify(recorder, never()).warn(anyString(), any(), any());
    }

    /**
     * Verifies that a dependency is skipped and a warning is emitted when its plugin is absent.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldWarnAndSkipDependencyWhenPluginIsAbsent() {
        final Project project = mock(Project.class);
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);
        final DefaultInstruction<String> instruction = buildInstruction(project, recorder);

        when(project.getPlugin(any())).thenReturn(Optional.empty());
        when(project.name()).thenReturn("test-project");

        final Reference dep = Reference.of(project, SimpleTask.class);
        instruction.addDependency(dep);

        assertThat(instruction.dependencies()).doesNotContain(dep);
        verify(recorder, atLeastOnce()).warn(anyString(), any());
    }

    /**
     * Verifies that a dependent is added when its plugin is present in the project.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldAddDependentWhenPluginIsPresent() {
        final Project project = mock(Project.class);
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);
        final DefaultInstruction<String> instruction = buildInstruction(project, recorder);

        final Plugin plugin = mock(StubPlugin.class);
        when(project.getPlugin(any())).thenReturn(Optional.of((StubPlugin) plugin));

        final Reference dep = Reference.of(project, SimpleTask.class);
        instruction.addDependent(dep);

        assertThat(instruction.dependents()).contains(dep);
        verify(recorder, never()).warn(anyString(), any(), any());
    }

    /**
     * Verifies that a dependent is skipped and a warning is emitted when its plugin is absent.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldWarnAndSkipDependentWhenPluginIsAbsent() {
        final Project project = mock(Project.class);
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);
        final DefaultInstruction<String> instruction = buildInstruction(project, recorder);

        when(project.getPlugin(any())).thenReturn(Optional.empty());
        when(project.name()).thenReturn("test-project");

        final Reference dep = Reference.of(project, SimpleTask.class);
        instruction.addDependent(dep);

        assertThat(instruction.dependents()).doesNotContain(dep);
        verify(recorder, atLeastOnce()).warn(anyString(), any());
    }
}
