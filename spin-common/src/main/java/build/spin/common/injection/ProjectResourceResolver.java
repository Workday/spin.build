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
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public class ProjectResourceResolver
    implements Resolver<Resource> {

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
    public Optional<? extends Binding<Resource>> resolve(final Dependency dependency) {
        final Class<?> mainClass = TypeUsages.getThreadContextClass(dependency.typeUsage()).orElse(null);
        if (mainClass == null) {
            return Optional.empty();
        }

        // if Optional<X>, resolve the X type from the annotated element's generic type
        final Class<?> resourceClass = mainClass.equals(Optional.class)
            ? TypeUsages.getFirstTypeParameterClass(dependency.typeUsage()).orElse(null)
            : mainClass;

        if (resourceClass == null) {
            return Optional.empty();
        }

        return this.project.hierarchy()
            .flatMap(Project::resources)
            .filter(resource -> resourceClass.isAssignableFrom(resource.getClass()))
            .findFirst()
            .map(resource -> ValueBinding.of(dependency, resource));
    }
}
