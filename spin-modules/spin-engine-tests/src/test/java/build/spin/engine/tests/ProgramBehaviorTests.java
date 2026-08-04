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
        AfterDependencyTestPlugin.ROOT_TASK_RAN.set(false);
        AfterDependencyTestPlugin.MIDDLE_TASK_RAN.set(false);
        AfterDependencyTestPlugin.SIDE_TASK_RAN.set(false);
        BeforeDependencyTestPlugin.TARGET_TASK_RAN.set(false);
        BeforeDependencyTestPlugin.BEFORE_TASK_RAN.set(false);
        CodependencyRaceTestPlugin.SLOW_DEPENDENCY_RAN.set(false);
        CodependencyRaceTestPlugin.PREPROCESSOR_RAN.set(false);
        CodependencyRaceTestPlugin.PREPROCESSOR_OBSERVED_VALUE = null;
        CodependencyRaceTestPlugin.MAIN_TASK_RAN.set(false);
        CodependencyOrderTestPlugin.MAIN_TASK_RAN.set(false);
        CodependencyOrderTestPlugin.PREPROCESSOR_RAN.set(false);
        CodependencyOrderTestPlugin.PREPROCESSOR_COMPLETED_AT.set(-1L);
        CodependencyOrderTestPlugin.SECONDARY_TASK_RAN.set(false);
        CodependencyOrderTestPlugin.SECONDARY_TASK_COMPLETED_AT.set(-1L);
        NestedCodependencyTestPlugin.SEQUENCE.set(0);
        NestedCodependencyTestPlugin.NESTED_PREPROCESSOR_RAN.set(false);
        NestedCodependencyTestPlugin.NESTED_PREPROCESSOR_ORDER.set(-1);
        NestedCodependencyTestPlugin.PREPROCESSOR_RAN.set(false);
        NestedCodependencyTestPlugin.PREPROCESSOR_ORDER.set(-1);
        NestedCodependencyTestPlugin.MAIN_TASK_RAN.set(false);
        NestedCodependencyTestPlugin.MAIN_TASK_ORDER.set(-1);
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

    // ── Question: does declaring @After on a Task that IS independently required pull the
    //    referenced Task into the Program too? (MiddleTask is required via RootTask's @From;
    //    MiddleTask is @After(SideTask.class); nothing else references SideTask at all) ──────

    @Test
    @WorkspacePath("afterdep-test")
    void shouldNotRunSideTaskThatIsOnlyReachableThroughAnotherTasksAfterAnnotation(
        final Engine engine, final Workspace workspace)
        throws Exception {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("afterdep-root"));
        program.execute(cache);

        assertThat(AfterDependencyTestPlugin.ROOT_TASK_RAN.get()).withFailMessage("root task must have run").isTrue();
        assertThat(AfterDependencyTestPlugin.MIDDLE_TASK_RAN.get())
            .withFailMessage("middle task must have run (it's a @From dependency of root)")
            .isTrue();
        assertThat(AfterDependencyTestPlugin.SIDE_TASK_RAN.get())
            .withFailMessage("side task is only reachable via middle's @After annotation - "
                + "per the @After contract it must not be pulled into the Program merely because "
                + "the annotated (middle) task happens to run")
            .isFalse();
    }

    // ── Mirror of the above for @Before: does a Task's own @Before(X) declaration pull it into
    //    the Program merely because X is independently required, with nothing else referencing it? ──

    @Test
    @WorkspacePath("beforedep-test")
    void shouldNotRunTaskThatIsOnlyBeforeAnotherTask(final Engine engine, final Workspace workspace)
        throws Exception {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("beforedep-target"));
        program.execute(cache);

        assertThat(BeforeDependencyTestPlugin.TARGET_TASK_RAN.get()).withFailMessage("target task must have run").isTrue();
        assertThat(BeforeDependencyTestPlugin.BEFORE_TASK_RAN.get())
            .withFailMessage("a task that is only @Before another task must not be executed unless required")
            .isFalse();
    }

    // ── Codependency scheduling gap, Issue 1: a codependency's own @From dependency must be a
    //    real scheduling constraint, not just a Task that happens to be included in the Program. ──

    @Test
    @WorkspacePath("codeprace-test")
    void shouldWaitForACodependencysOwnDependencyBeforeRunningIt(final Engine engine, final Workspace workspace)
        throws Exception {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("codeprace-main"));
        program.execute(cache);

        assertThat(CodependencyRaceTestPlugin.MAIN_TASK_RAN.get()).withFailMessage("main task must have run").isTrue();
        assertThat(CodependencyRaceTestPlugin.SLOW_DEPENDENCY_RAN.get())
            .withFailMessage("the preprocessor's @From dependency must have run").isTrue();
        assertThat(CodependencyRaceTestPlugin.PREPROCESSOR_RAN.get())
            .withFailMessage("the preprocessor must have run").isTrue();
        assertThat(CodependencyRaceTestPlugin.PREPROCESSOR_OBSERVED_VALUE)
            .withFailMessage("the preprocessor's own @From dependency must be a real scheduling "
                + "constraint - it must have completed and landed in the cache before the "
                + "preprocessor (and its owning task) are dispatched, not merely be included "
                + "somewhere in the Program with no graph edge tying the two together")
            .isEqualTo("slow-value");
    }

    // ── Codependency scheduling gap, Issue 2: @Before/@After declared on a codependency's own
    //    Task class must be honored as an ordering constraint, not silently ignored because
    //    codependencies never go through DefaultInstruction's own @Before/@After resolution. ───

    @Test
    @WorkspacePath("codeporder-test")
    void shouldHonorBeforeDeclaredOnACodependencysOwnTaskClass(final Engine engine, final Workspace workspace)
        throws Exception {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("codep-order"));
        program.execute(cache);

        assertThat(CodependencyOrderTestPlugin.MAIN_TASK_RAN.get()).withFailMessage("main task must have run").isTrue();
        assertThat(CodependencyOrderTestPlugin.PREPROCESSOR_RAN.get())
            .withFailMessage("the preprocessor must have run").isTrue();
        assertThat(CodependencyOrderTestPlugin.SECONDARY_TASK_RAN.get())
            .withFailMessage("the secondary task must have run").isTrue();
        assertThat(CodependencyOrderTestPlugin.PREPROCESSOR_COMPLETED_AT.get())
            .withFailMessage("the preprocessor's own @Before(SecondaryTask) declaration must be "
                + "honored as an ordering constraint, so the preprocessor completes strictly "
                + "before the secondary task it named")
            .isLessThan(CodependencyOrderTestPlugin.SECONDARY_TASK_COMPLETED_AT.get());
    }

    // ── Codependency scheduling gap, Issue 3: a codependency that itself declares @PreProcess/
    //    @PostProcess must have that nested codependency discovered and executed, not silently
    //    dropped because codependency resolution only ever looks one level deep. ──────────────

    @Test
    @WorkspacePath("codepnested-test")
    void shouldRunACodependencysOwnNestedCodependency(final Engine engine, final Workspace workspace)
        throws Exception {

        final AssetCache cache = DefaultAssetCache.create();
        final Program program = engine.createProgram(workspace, Task.Pattern.of("codepnested-main"));
        program.execute(cache);

        assertThat(NestedCodependencyTestPlugin.MAIN_TASK_RAN.get()).withFailMessage("main task must have run").isTrue();
        assertThat(NestedCodependencyTestPlugin.PREPROCESSOR_RAN.get())
            .withFailMessage("the preprocessor must have run").isTrue();
        assertThat(NestedCodependencyTestPlugin.NESTED_PREPROCESSOR_RAN.get())
            .withFailMessage("a codependency that itself declares @PreProcess must have that "
                + "nested codependency discovered and executed too, rather than only the owning "
                + "task's own (one level of) codependencies being resolved")
            .isTrue();
        assertThat(NestedCodependencyTestPlugin.NESTED_PREPROCESSOR_ORDER.get())
            .withFailMessage("the nested preprocessor must run before the preprocessor it pre-processes")
            .isLessThan(NestedCodependencyTestPlugin.PREPROCESSOR_ORDER.get());
        assertThat(NestedCodependencyTestPlugin.PREPROCESSOR_ORDER.get())
            .withFailMessage("the preprocessor must run before the main task it pre-processes")
            .isLessThan(NestedCodependencyTestPlugin.MAIN_TASK_ORDER.get());
    }
}
