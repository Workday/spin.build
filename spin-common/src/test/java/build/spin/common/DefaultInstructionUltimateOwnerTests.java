package build.spin.common;

import build.base.telemetry.TelemetryRecorder;
import build.codemodel.dependency.injection.Context;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.Before;
import build.spin.annotation.PreProcess;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultInstruction}'s folding of {@link Before}/{@link build.spin.annotation.After}
 * declared on a nested {@link PreProcess}/{@link build.spin.annotation.PostProcess} codependency chain
 * up to its ultimate top-level owner - see {@code DefaultInstruction#ultimateOwner}, which now resolves
 * that chain via {@link Project#getInvocable(Reference)} rather than a manual {@code invocables()} scan.
 */
class DefaultInstructionUltimateOwnerTests {

    /**
     * The {@link Task} under test - the codependency chain below declares itself {@link Before} this.
     */
    static class TargetTask implements Task<String> { }

    /**
     * The top-level {@link Task} that ultimately owns the codependency chain below.
     */
    static class FinalOwnerTask implements Task<String> { }

    /**
     * A codependency, one level removed from the top-level owner.
     */
    @PreProcess(FinalOwnerTask.class)
    static class MidCodependencyTask implements Task<String> { }

    /**
     * A codependency nested two levels deep, that also declares an ordering constraint which must be
     * folded up to {@link FinalOwnerTask} rather than left pointing at this {@link Task} (which never
     * gets its own {@link build.spin.Instruction}).
     */
    @PreProcess(MidCodependencyTask.class)
    @Before(TargetTask.class)
    static class DeepCodependencyTask implements Task<String> { }

    @SuppressWarnings("unchecked")
    private <T> Invocable<T> invocable(final Project project, final Class<? extends Task<T>> taskClass) {
        // CALLS_REAL_METHODS so the default methods under test (isCodependency, isBefore) run their
        // real logic against the stubbed getTaskClass()/getReference(), rather than Mockito's smart nulls
        final Invocable<T> invocable = mock(Invocable.class, CALLS_REAL_METHODS);
        when(invocable.getProject()).thenReturn(project);
        when(invocable.getTaskClass()).thenReturn((Class) taskClass);
        when(invocable.getReference()).thenReturn(Reference.of(project, taskClass));
        return invocable;
    }

    @Test
    @SuppressWarnings("unchecked")
    void beforeDeclaredOnANestedCodependencyIsFoldedOntoItsUltimateTopLevelOwner() {
        final Project project = mock(Project.class);
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);
        final Context context = mock(Context.class);

        final TargetTask targetTask = new TargetTask();
        when(context.create(any(Class.class))).thenReturn(targetTask);

        final Invocable<String> targetInvocable = invocable(project, TargetTask.class);
        final Invocable<String> finalOwnerInvocable = invocable(project, FinalOwnerTask.class);
        final Invocable<String> midInvocable = invocable(project, MidCodependencyTask.class);
        final Invocable<String> deepInvocable = invocable(project, DeepCodependencyTask.class);

        when(targetInvocable.createTask(context)).thenReturn(targetTask);
        when(targetInvocable.dependencies()).thenAnswer(__ -> Stream.empty());

        // the Project's Invocables: used both to discover ordering-only codependency declarations,
        // and (via the default Project#getInvocable) to walk the PreProcess chain in ultimateOwner
        when(project.invocables()).thenAnswer(__ ->
            Stream.of(targetInvocable, finalOwnerInvocable, midInvocable, deepInvocable));
        when(project.getInvocable(any())).thenAnswer(invocation -> {
            final Reference reference = invocation.getArgument(0);
            return Stream.of(targetInvocable, finalOwnerInvocable, midInvocable, deepInvocable)
                .filter(candidate -> candidate.getReference().equals(reference))
                .findFirst();
        });
        when(project.codependencies(any())).thenAnswer(__ -> Stream.empty());
        when(project.getPlugin(any())).thenReturn(Optional.of(mock(Plugin.class)));
        when(project.name()).thenReturn("test-project");

        final DefaultInstruction<String> instruction =
            new DefaultInstruction<>(0, targetInvocable, recorder, context);

        assertThat(instruction.dependencies())
            .contains(Reference.of(project, FinalOwnerTask.class))
            .doesNotContain(Reference.of(project, DeepCodependencyTask.class))
            .doesNotContain(Reference.of(project, MidCodependencyTask.class));
    }
}
