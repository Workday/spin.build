package build.spin.engine;

import build.base.configuration.Configuration;
import build.spin.Engine;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.PreProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the indexed {@link Project} lookups ({@code getInvocable}, {@code getInvocables},
 * {@code codependencies}) added to {@link AbstractProject}.
 */
class AbstractProjectTests {

    /**
     * A {@link Task} with no codependency relationships.
     */
    public static class BaseTask implements Task<String> { }

    /**
     * A {@link Task} unrelated to {@link BaseTask}.
     */
    public static class OtherTask implements Task<String> { }

    /**
     * A {@link Task} that pre-processes {@link BaseTask}.
     */
    @PreProcess(BaseTask.class)
    public static class PreProcessorTask implements Task<String> { }

    /**
     * A minimal {@link Invocable} test double, avoiding a mocking dependency this module doesn't have.
     */
    private static final class StubInvocable<T> implements Invocable<T> {

        private final Project project;
        private final Class<? extends Task<T>> taskClass;

        private StubInvocable(final Project project, final Class<? extends Task<T>> taskClass) {
            this.project = project;
            this.taskClass = taskClass;
        }

        @Override
        public URI getURI() {
            return URI.create("task:" + taskClass.getSimpleName());
        }

        @Override
        public Project getProject() {
            return project;
        }

        @Override
        public Plugin getPlugin() {
            return null;
        }

        @Override
        public Class<? extends Task<T>> getTaskClass() {
            return taskClass;
        }
    }

    private Engine engine() {
        return new DefaultEngine(
            Thread.currentThread().getContextClassLoader(),
            FileSystems.getDefault(),
            Configuration.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    }

    private DefaultProject project(final Path path) {
        return new DefaultProject(engine(), Optional.empty(), path, Configuration.empty());
    }

    private <T> Invocable<T> invocable(final Project project, final Class<? extends Task<T>> taskClass) {
        return new StubInvocable<>(project, taskClass);
    }

    @Test
    void getInvocableFindsTheInvocableMatchingItsReference(@TempDir final Path tempDir) {
        final DefaultProject project = project(tempDir);
        final Invocable<String> baseInvocable = invocable(project, BaseTask.class);
        project.invocables.put(baseInvocable.getReference(), baseInvocable);

        assertThat(project.getInvocable(Reference.of(project, BaseTask.class))).contains(baseInvocable);
    }

    @Test
    void getInvocableIsEmptyWhenNoInvocableMatchesTheReference(@TempDir final Path tempDir) {
        final DefaultProject project = project(tempDir);

        assertThat(project.getInvocable(Reference.of(project, BaseTask.class))).isEmpty();
    }

    @Test
    void getInvocablesReturnsOnlyThoseAssignableToTheRequestedTaskClass(@TempDir final Path tempDir) {
        final DefaultProject project = project(tempDir);
        final Invocable<String> baseInvocable = invocable(project, BaseTask.class);
        final Invocable<String> otherInvocable = invocable(project, OtherTask.class);
        project.invocables.put(baseInvocable.getReference(), baseInvocable);
        project.invocables.put(otherInvocable.getReference(), otherInvocable);

        assertThat(project.getInvocables(BaseTask.class)).containsExactly(baseInvocable);
        assertThat(project.getInvocables(OtherTask.class)).containsExactly(otherInvocable);
    }

    @Test
    void codependenciesResolvesPreProcessDeclarationsTargetingTheGivenReference(@TempDir final Path tempDir) {
        final DefaultProject project = project(tempDir);
        final Invocable<String> baseInvocable = invocable(project, BaseTask.class);
        final Invocable<String> otherInvocable = invocable(project, OtherTask.class);
        final Invocable<String> preProcessorInvocable = invocable(project, PreProcessorTask.class);
        project.invocables.put(baseInvocable.getReference(), baseInvocable);
        project.invocables.put(otherInvocable.getReference(), otherInvocable);
        project.invocables.put(preProcessorInvocable.getReference(), preProcessorInvocable);

        assertThat(project.codependencies(Reference.of(project, BaseTask.class)))
            .containsExactly(preProcessorInvocable);
        assertThat(project.codependencies(Reference.of(project, OtherTask.class))).isEmpty();
    }

    @Test
    void getInvocablesIndexIsConsistentAcrossRepeatedQueries(@TempDir final Path tempDir) {
        // exercises the Memoizer-backed cache in AbstractProject: querying the same Class twice must
        // not observe a different (eg: stale-then-fresh, or reordered) result the second time
        final DefaultProject project = project(tempDir);
        final Invocable<String> baseInvocable = invocable(project, BaseTask.class);
        project.invocables.put(baseInvocable.getReference(), baseInvocable);

        assertThat(project.getInvocables(BaseTask.class)).containsExactly(baseInvocable);
        assertThat(project.getInvocables(BaseTask.class)).containsExactly(baseInvocable);
    }
}
