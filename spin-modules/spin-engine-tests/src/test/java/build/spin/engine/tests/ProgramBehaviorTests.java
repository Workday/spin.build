package build.spin.engine.tests;

import build.spin.AssetCache;
import build.spin.Engine;
import build.spin.Program;
import build.spin.ProgramExecutionException;
import build.spin.Task;
import build.spin.Workspace;
import build.spin.common.DefaultAssetCache;
import build.spin.testing.WorkspaceDiscovery;
import build.spin.testing.WorkspacePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link build.spin.common.DefaultProgram} behaviour:
 * pre-processor execution and cycle detection.
 */
@ExtendWith(WorkspaceDiscovery.class)
class ProgramBehaviorTests {

    // ── Bug 1: @PreProcess tasks must execute before the main task ────────────

    @BeforeEach
    void resetFlags() {
        PreProcessTestPlugin.PRE_PROCESSOR_RAN.set(false);
        PreProcessTestPlugin.MAIN_TASK_RAN.set(false);
        FailFastTestPlugin.SLOW_ROOT_RAN.set(false);
        FailFastTestPlugin.NEVER_TASK_RAN.set(false);
        AfterTestPlugin.MAIN_TASK_RAN.set(false);
        AfterTestPlugin.AFTER_TASK_RAN.set(false);
    }

    @Test
    @WorkspacePath("preprocess-test")
    void shouldRunPreProcessorBeforeMainTask(final Engine engine, final Workspace workspace)
        throws Exception {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("preprocess-main"));
        program.execute(cache);

        assertThat(PreProcessTestPlugin.MAIN_TASK_RAN.get()).withFailMessage("main task must have run").isTrue();
        assertThat(PreProcessTestPlugin.PRE_PROCESSOR_RAN.get()).withFailMessage("@PreProcess task must have run").isTrue();
    }

    // ── Bug 2: cyclic task dependencies must throw, not silently produce nothing

    @Test
    @WorkspacePath("cyclic-test")
    void shouldThrowWhenTaskDependenciesAreCyclic(final Engine engine, final Workspace workspace) {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("cyclic-a"));

        assertThrows(ProgramExecutionException.class, () -> program.execute(cache),
            "execute() must throw ProgramExecutionException when tasks have cyclic dependencies");
    }

    // ── Bug 3: once a task fails, new tasks must not be dispatched ────────────

    @Test
    @WorkspacePath("fail-fast-test")
    void shouldNotDispatchNewTasksAfterAFailure(final Engine engine, final Workspace workspace) {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("fail-fast"));

        assertThrows(ProgramExecutionException.class, () -> program.execute(cache),
            "execute() must throw ProgramExecutionException when a task fails");

        assertThat(FailFastTestPlugin.SLOW_ROOT_RAN.get())
            .withFailMessage("an already in-flight task must be left to finish")
            .isTrue();
        assertThat(FailFastTestPlugin.NEVER_TASK_RAN.get())
            .withFailMessage("no new task may be dispatched once a failure has been recorded")
            .isFalse();
    }

    // ── Bug 4: @After alone must not pull a task into the Program ─────────────

    @Test
    @WorkspacePath("after-test")
    void shouldNotRunTaskThatIsOnlyAfterAnotherTask(final Engine engine, final Workspace workspace)
        throws Exception {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("after-main"));
        program.execute(cache);

        assertThat(AfterTestPlugin.MAIN_TASK_RAN.get()).withFailMessage("main task must have run").isTrue();
        assertThat(AfterTestPlugin.AFTER_TASK_RAN.get())
            .withFailMessage("a task that is only @After another task must not be executed unless required")
            .isFalse();
    }
}
