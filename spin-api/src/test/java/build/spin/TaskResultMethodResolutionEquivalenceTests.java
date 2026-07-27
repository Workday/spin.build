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

import build.codemodel.dependency.injection.Context;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.Resolver;
import build.codemodel.dependency.injection.ValueBinding;
import build.codemodel.jdk.TypeUsages;
import build.spin.annotation.From;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link Task#execute} locates a {@link Task}'s method via {@code codemodel}'s {@code MethodDescriptor}
 * API; {@link Invocable#dependencies()} locates the same method via {@code java.lang.reflect} +
 * {@link build.base.foundation.Introspection}. These are two independent implementations of "find the
 * method that implements this Task's declared result" — they happen to agree today (both now delegate
 * their match test to {@link Invocable#isTaskResultMethod}), but nothing stops the two traversal
 * mechanisms themselves from silently diverging for some declared-result shape in the future.
 * <p>
 * These tests exercise both entry points against the same {@link Task} classes, for every legitimate
 * way a {@link Task} declares its result (concrete, raw/void, explicit {@code Task<Void>}, and generic),
 * and assert both resolve to — and successfully use — the same method.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
class TaskResultMethodResolutionEquivalenceTests {

    private static final Path MARKER = Path.of("marker");
    private static final Project PROJECT = mock(Project.class);

    private static class SourceTask
        implements Task<Path> {
    }

    /** {@code Task<Path>}: a plain, concrete declared result type. */
    private static class ConcreteResultTask
        implements Task<Path> {

        public Path run(final @From(SourceTask.class) Path input) {
            return input;
        }
    }

    /** raw {@code Task} (a void-returning method) — the shape used by {@code CheckstylePlugin.Checkstyle}. */
    private static class VoidResultTask
        implements Task {

        private boolean invoked;

        public void run(final @From(SourceTask.class) Path input) {
            this.invoked = input != null;
        }
    }

    /** {@code Task<Void>} — the boxed-result special case a raw {@code Task} is distinct from. */
    private static class ExplicitVoidResultTask
        implements Task<Void> {

        private boolean invoked;

        public void run(final @From(SourceTask.class) Path input) {
            this.invoked = input != null;
        }
    }

    /** {@code Task<Set<Path>>}: a generic (parameterized) declared result type. */
    private static class GenericResultTask
        implements Task<Set<Path>> {

        public Set<Path> run(final @From(SourceTask.class) Path input) {
            return Set.of(input);
        }
    }

    private static Invocable<?> invocableFor(final Class<?> taskClass) {
        return new Invocable<>() {
            @Override
            public URI getURI() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Project getProject() {
                return PROJECT;
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
     * A minimal {@link Resolver} standing in for {@code FromResolver} — satisfies any {@link Path}-typed
     * dependency with {@link #MARKER}, regardless of its {@link From} qualifier, so {@link Task#execute}
     * can actually invoke the resolved method.
     */
    private static Resolver<Object> pathResolver() {
        return dependency -> {
            final var type = TypeUsages.getThreadContextClass(dependency.typeUsage()).orElse(null);
            return Path.class.equals(type)
                ? Optional.of(ValueBinding.of(dependency, (Object) MARKER))
                : Optional.empty();
        };
    }

    private static Context newContext() {
        final Context context = InjectionFramework.create().newContext();
        context.addResolver(pathResolver());
        return context;
    }

    private static List<Class<?>> dependencies(final Invocable<?> invocable) {
        return invocable.dependencies()
            .<Class<?>>map(Reference::getTaskClass)
            .toList();
    }

    @Test
    void agreeForAConcreteDeclaredResultType() {
        final ConcreteResultTask task = new ConcreteResultTask();
        final Invocable<?> invocable = invocableFor(ConcreteResultTask.class);

        final Path result = task.execute(invocable, newContext(), InjectionFramework.create());
        assertThat(result).isEqualTo(MARKER);

        assertThat(dependencies(invocable))
            .containsExactly(SourceTask.class);
    }

    @Test
    void agreeForARawVoidTask() {
        final VoidResultTask task = new VoidResultTask();
        final Invocable<?> invocable = invocableFor(VoidResultTask.class);

        task.execute(invocable, newContext(), InjectionFramework.create());
        assertThat(task.invoked).isTrue();

        assertThat(dependencies(invocable))
            .as("a raw Task (void method) must resolve @From dependencies the same as Task#execute "
                + "resolves the method to invoke")
            .containsExactly(SourceTask.class);
    }

    @Test
    void agreeForAnExplicitTaskOfVoid() {
        final ExplicitVoidResultTask task = new ExplicitVoidResultTask();
        final Invocable<?> invocable = invocableFor(ExplicitVoidResultTask.class);

        task.execute(invocable, newContext(), InjectionFramework.create());
        assertThat(task.invoked).isTrue();

        assertThat(dependencies(invocable))
            .as("Task<Void> (declared boxed result, void method) must resolve @From dependencies the "
                + "same as Task#execute resolves the method to invoke")
            .containsExactly(SourceTask.class);
    }

    @Test
    void agreeForAGenericDeclaredResultType() {
        final GenericResultTask task = new GenericResultTask();
        final Invocable<?> invocable = invocableFor(GenericResultTask.class);

        final Set<Path> result = task.execute(invocable, newContext(), InjectionFramework.create());
        assertThat(result).containsExactly(MARKER);

        assertThat(dependencies(invocable))
            .containsExactly(SourceTask.class);
    }
}
