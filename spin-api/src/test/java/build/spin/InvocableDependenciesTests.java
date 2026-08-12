package build.spin;

/*-
 * #%L
 * Spin API
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import build.spin.annotation.From;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Invocable#dependencies()}, in particular that a {@link Task} declared without a
 * result type — i.e. {@code implements Task} rather than {@code Task<T>}, the shape used by any
 * {@link Task} whose method returns {@code void} — still has its {@link From}-annotated parameters
 * wired up as dependencies. {@link Invocable#getTaskResultClass()} is empty for such a {@link Task},
 * the same as it is for {@link Task#execute} — which already falls back to {@code void.class} to
 * locate the task method. {@link Invocable#dependencies()} must apply the same fallback, or a
 * {@code void} {@link Task}'s {@code @From} parameters (e.g. checkstyle's source-file input) are
 * silently dropped from the {@link Task} graph and resolve empty at runtime.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
class InvocableDependenciesTests {

    private static class SourceTask
        implements Task<Path> {
    }

    private interface AbstractSourceTask
        extends Task<Path> {
    }

    private static class ConcreteAbstractSourceTask
        implements AbstractSourceTask {
    }

    private static class NonVoidTaskWithFromDependency
        implements Task<Path> {

        public Path run(final @From(SourceTask.class) Path input) {
            return input;
        }
    }

    private static class VoidTaskWithFromDependency
        implements Task {

        public void run(final @From(SourceTask.class) Path input) {
        }
    }

    private static class VoidTaskWithStreamFromDependency
        implements Task {

        public void run(final @From(AbstractSourceTask.class) Stream<Path> inputs) {
        }
    }

    private static class VoidTaskWithConcreteStreamFromDependency
        implements Task {

        public void run(final @From(SourceTask.class) Stream<Path> inputs) {
        }
    }

    private static Invocable<?> invocableFor(final Class<?> taskClass, final Project project) {
        return new Invocable<>() {
            @Override
            public URI getURI() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public Plugin getPlugin() {
                throw new UnsupportedOperationException();
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends Task<Object>> getTaskClass() {
                return (Class<? extends Task<Object>>) taskClass;
            }
        };
    }

    /**
     * A minimal {@link Project} stub — every {@link Invocable#dependencies()} code path under test
     * only ever calls {@link Project#invocables()}, so every other method is left unimplemented.
     */
    private static Project projectWithInvocables(final Stream<Invocable<?>> invocables) {
        return new Project() {
            @Override
            public Stream<Invocable<?>> invocables() {
                return invocables;
            }

            @Override
            public String name() {
                return "InvocableDependenciesTests Project";
            }

            @Override
            public Engine engine() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Project> parent() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int depth() {
                throw new UnsupportedOperationException();
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
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> Stream<T> plugins(final Class<T> assignableTo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean contains(final Class<?> assignableTo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<Resource> resources() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Workspace workspace() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<Project> stream(final Predicate<? super Project> predicate) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<Project> stream() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<Project> children() {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Plugin> Optional<T> getPlugin(final Class<T> pluginClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Resource> Optional<T> getResource(final Class<T> resourceClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Resource> Optional<T> findResource(final Class<T> resourceClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void walk(final Visitor<? super Project> visitor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean contains(final Path path) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isIgnored(final Path path) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<Invocable<?>> codependencies(final Reference reference) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Project> getProject(final Path path) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int compareTo(final Project other) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void treeify(final StringBuilder builder, final String prefix, final String childPrefix,
                                final java.util.function.Function<Project, String> describe) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static List<Class<?>> dependencies(final Invocable<?> invocable) {
        return invocable.dependencies()
            .<Class<?>>map(Reference::getTaskClass)
            .toList();
    }

    @Test
    void includesFromDependencyForATaskDeclaringAResultType() {
        final Project project = projectWithInvocables(Stream.empty());
        final Invocable<?> invocable = invocableFor(NonVoidTaskWithFromDependency.class, project);

        assertThat(dependencies(invocable))
            .containsExactly(SourceTask.class);
    }

    @Test
    void includesFromDependencyForAVoidTaskWithAConcreteFromTask() {
        final Project project = projectWithInvocables(Stream.empty());
        final Invocable<?> invocable = invocableFor(VoidTaskWithFromDependency.class, project);

        assertThat(dependencies(invocable))
            .as("a Task declared as raw `Task` (a void-returning method) must still resolve its "
                + "@From-annotated parameters, the same as Task#execute already falls back to "
                + "void.class to locate that method")
            .containsExactly(SourceTask.class);
    }

    @Test
    void includesFromDependenciesForAVoidTaskWithAnAbstractStreamFromTask() {
        final Invocable<?> sourceInvocable = invocableFor(ConcreteAbstractSourceTask.class,
            projectWithInvocables(Stream.empty()));
        final Project project = projectWithInvocables(Stream.of(sourceInvocable));
        final Invocable<?> invocable = invocableFor(VoidTaskWithStreamFromDependency.class, project);

        assertThat(dependencies(invocable))
            .as("mirrors CheckstylePlugin.Checkstyle: a void Task with a Stream<T> @From an "
                + "abstract/interface Task must resolve to every matching concrete Task in the "
                + "Project, not an empty Stream")
            .containsExactly(ConcreteAbstractSourceTask.class);
    }

    @Test
    void toleratesAConcreteStreamFromTaskThatDoesNotExistInTheProject() {
        final Project project = projectWithInvocables(Stream.empty());
        final Invocable<?> invocable = invocableFor(VoidTaskWithConcreteStreamFromDependency.class, project);

        assertThat(dependencies(invocable))
            .as("mirrors ResourcePlugin.DetectModuleResourcePaths: a concrete Task declared as a "
                + "Stream<T> @From dependency only exists in some Projects (e.g. ones with a resources "
                + "directory), so it must resolve against the Project's actual Invocables and tolerate "
                + "zero matches, rather than always producing a Reference to a Task that may not exist "
                + "in this Project")
            .isEmpty();
    }

    @Test
    void resolvesAConcreteStreamFromTaskAgainstTheProjectWhenItIsPresent() {
        final Invocable<?> sourceInvocable = invocableFor(SourceTask.class, projectWithInvocables(Stream.empty()));
        final Project project = projectWithInvocables(Stream.of(sourceInvocable));
        final Invocable<?> invocable = invocableFor(VoidTaskWithConcreteStreamFromDependency.class, project);

        assertThat(dependencies(invocable))
            .containsExactly(SourceTask.class);
    }
}
