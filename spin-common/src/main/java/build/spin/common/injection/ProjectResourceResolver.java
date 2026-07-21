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

import build.codemodel.injection.Binding;
import build.codemodel.injection.ChainedResolver;
import build.codemodel.injection.Dependency;
import build.codemodel.injection.Resolver;
import build.codemodel.injection.ValueBinding;
import build.codemodel.jdk.TypeUsages;
import build.spin.Project;
import build.spin.Resource;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves {@link Resource}s defined by a {@link Project} and/or it's parents.
 * <p>
 * Supports both direct injection of a {@link Resource} (unsatisfied when not present) and injection as an
 * {@link Optional} (always satisfied: {@link Optional#of(Object)} when present, {@link Optional#empty()}
 * otherwise).
 * <p>
 * Because an {@link Optional}-typed dependency is always satisfied by this {@link Resolver} (even when no
 * matching {@link Resource} is present), this {@link Resolver} must be registered <strong>last</strong> in
 * any {@link ChainedResolver}, so other {@link Resolver}s (eg: a {@code ConfigurationResolver} resolving
 * {@code Optional<Boolean>}/{@code Optional<String>} values) are given the chance to resolve a dependency
 * first.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public class ProjectResourceResolver
    implements Resolver<Object> {

    /**
     * The {@link Project} in which to attempt to resolve a {@link Resource}.
     */
    private final Project project;

    /**
     * Constructs a {@link ProjectResourceResolver} for the specified {@link Project}.
     *
     * @param project the {@link Project}
     */
    public ProjectResourceResolver(final Project project) {
        this.project = Objects.requireNonNull(project, "The Project must not be null");
    }

    @Override
    public Optional<? extends Binding<Object>> resolve(final Dependency dependency) {
        final var mainClass = TypeUsages.getThreadContextClass(dependency.typeUsage()).orElse(null);
        if (mainClass == null) {
            return Optional.empty();
        }

        final var isOptional = mainClass.equals(Optional.class);

        // if Optional<X>, resolve the X type from the annotated element's generic type
        final var resourceClass = isOptional
            ? TypeUsages.getFirstTypeParameterClass(dependency.typeUsage()).orElse(null)
            : mainClass;

        if (resourceClass == null) {
            return Optional.empty();
        }

        final var resource = this.project.hierarchy()
            .flatMap(Project::resources)
            .filter(candidate -> resourceClass.isAssignableFrom(candidate.getClass()))
            .findFirst();

        if (isOptional) {
            // an Optional<X>-typed injection point must always resolve: to Optional.of(resource) when
            // found, or Optional.empty() when not — never left unsatisfied
            return Optional.of(ValueBinding.of(dependency, (Object) resource));
        }

        return resource.map(value -> ValueBinding.of(dependency, (Object) value));
    }
}
