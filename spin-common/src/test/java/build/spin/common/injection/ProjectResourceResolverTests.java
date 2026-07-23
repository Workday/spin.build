package build.spin.common.injection;

/*-
 * #%L
 * Spin Common Library
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

import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.Resolver;
import build.codemodel.dependency.injection.UnsatisfiedDependencyException;
import build.codemodel.dependency.injection.ValueBinding;
import build.codemodel.jdk.TypeUsages;
import build.spin.Project;
import build.spin.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ProjectResourceResolver}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class ProjectResourceResolverTests {

    private interface SomeResource
        extends Resource {
    }

    private static class SomeResourceImpl
        implements SomeResource {
    }

    private static class DirectProbe {
        @Inject
        private SomeResource resource;
    }

    private static class OptionalProbe {
        @Inject
        private Optional<SomeResource> resource;
    }

    private static class OptionalBooleanProbe {
        @Inject
        private Optional<Boolean> flag;
    }

    /**
     * Ensure a direct (non-{@link Optional}) injection of a {@link Resource} resolves when it's present for
     * the {@link Project}.
     */
    @Test
    void shouldResolveDirectInjectionWhenResourcePresent() {
        final var resource = new SomeResourceImpl();
        final var project = mockProjectWithResources(resource);

        final var context = InjectionFramework.create().newContext();
        context.addResolver(new ProjectResourceResolver(project));

        final var probe = context.create(DirectProbe.class);

        assertThat(probe.resource)
            .isSameAs(resource);
    }

    /**
     * Ensure a direct (non-{@link Optional}) injection of a {@link Resource} remains unsatisfied when it's
     * absent for the {@link Project}.
     */
    @Test
    void shouldFailDirectInjectionWhenResourceAbsent() {
        final var project = mockProjectWithResources();

        final var context = InjectionFramework.create().newContext();
        context.addResolver(new ProjectResourceResolver(project));

        assertThrows(UnsatisfiedDependencyException.class, () -> context.create(DirectProbe.class));
    }

    /**
     * Ensure an {@link Optional} injection of a {@link Resource} resolves to {@link Optional#of(Object)} when
     * it's present for the {@link Project}.
     */
    @Test
    void shouldResolveOptionalInjectionWhenResourcePresent() {
        final var resource = new SomeResourceImpl();
        final var project = mockProjectWithResources(resource);

        final var context = InjectionFramework.create().newContext();
        context.addResolver(new ProjectResourceResolver(project));

        final var probe = context.create(OptionalProbe.class);

        assertThat(probe.resource)
            .contains(resource);
    }

    /**
     * Ensure an {@link Optional} injection of a {@link Resource} resolves to {@link Optional#empty()},
     * rather than being left unsatisfied, when it's absent for the {@link Project}.
     */
    @Test
    void shouldResolveOptionalInjectionWhenResourceAbsent() {
        final var project = mockProjectWithResources();

        final var context = InjectionFramework.create().newContext();
        context.addResolver(new ProjectResourceResolver(project));

        final var probe = context.create(OptionalProbe.class);

        assertThat(probe.resource)
            .isEmpty();
    }

    /**
     * Ensure that, since {@link ProjectResourceResolver} always resolves an {@link Optional}-typed
     * dependency (to {@link Optional#of(Object)} or {@link Optional#empty()}), it must be registered
     * <strong>last</strong> — a {@link Resolver} for an unrelated type (eg: {@code Optional<Boolean>}, as
     * used by {@code @Configuration @Named(...)}-qualified fields) registered <em>before</em> it still gets
     * the chance to resolve the dependency, rather than being short-circuited.
     */
    @Test
    void shouldNotInterfereWhenRegisteredLastAfterAnotherResolverHandlesTheDependency() {
        final var project = mockProjectWithResources();

        final var context = InjectionFramework.create().newContext();
        context.addResolver(booleanFallbackResolver());
        context.addResolver(new ProjectResourceResolver(project));

        final var probe = context.create(OptionalBooleanProbe.class);

        assertThat(probe.flag)
            .contains(Boolean.TRUE);
    }

    private static Project mockProjectWithResources(final Resource... resources) {
        final var project = mock(Project.class);
        when(project.hierarchy()).thenAnswer(invocation -> Stream.of(project));
        when(project.resources()).thenAnswer(invocation -> Stream.of(resources));
        return project;
    }

    /**
     * A minimal {@link Resolver} that satisfies {@code Optional<Boolean>}-typed dependencies with
     * {@code Optional.of(true)}, standing in for a resolver like {@code ConfigurationResolver} that must get
     * a chance to run after {@link ProjectResourceResolver} declines a non-{@link Resource} dependency.
     */
    private static Resolver<Object> booleanFallbackResolver() {
        return dependency -> {
            final var mainClass = TypeUsages.getThreadContextClass(dependency.typeUsage()).orElse(null);
            if (mainClass != null && mainClass.equals(Optional.class)) {
                final var typeArg = TypeUsages.getFirstTypeParameterClass(dependency.typeUsage()).orElse(null);
                if (Boolean.class.equals(typeArg)) {
                    return Optional.of(ValueBinding.of(dependency, (Object) Optional.of(Boolean.TRUE)));
                }
            }
            return Optional.empty();
        };
    }
}
