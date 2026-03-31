package build.spin.common.util;

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

import build.base.foundation.Introspection;
import build.base.foundation.UniformResource;
import build.codemodel.injection.Context;
import build.codemodel.injection.InjectionFramework;
import build.codemodel.injection.ProvidesResolver;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.SpecifiedBy;
import build.spin.common.DefaultInvocable;
import build.spin.common.injection.ProjectResourceResolver;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Helper methods for working with {@link Invocable}s.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public class Invocables {

    private static final InjectionFramework FRAMEWORK = InjectionFramework.create();

    /**
     * Private constructor to prevent instantiation.
     */
    private Invocables() {
        // empty constructor
    }

    /**
     * Creates an {@link Invocable} for a specified {@link Class} of {@link Task} from a {@link Plugin} in a
     * {@link Project}.
     *
     * @param project   the {@link Project}
     * @param plugin    the {@link Plugin}
     * @param taskClass the {@link Class} of {@link Task}
     * @param <T>       the type
     * @return a new {@link Invocable}
     */
    @SuppressWarnings("unchecked")
    public static <T> Invocable<T> create(final Project project,
                                          final Plugin plugin,
                                          final Class<? extends Task<T>> taskClass) {

        Objects.requireNonNull(project, "The project must not be null");
        Objects.requireNonNull(plugin, "The plugin must not be null");
        Objects.requireNonNull(taskClass, "The Task Class must not be null");

        // determine the type of Invocable to be created for the Task
        // (the Task class may override the default using @SpecifiedBy)
        SpecifiedBy specifiedBy = Introspection.getAllDeclaredAnnotationsByType(taskClass, SpecifiedBy.class)
            .findFirst()
            .orElse(null);

        // when not found on the class directly, look for meta annotations
        // (annotations that themselves define SpecifiedBy)
        // (we need to make this super efficient so that we're not looking up class annotations for every task?)
        if (specifiedBy == null) {
            specifiedBy = Introspection.getAll(taskClass, Class::getAnnotations)
                .map(annotation -> annotation.annotationType().getAnnotation(SpecifiedBy.class))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }

        final Class<? extends Invocable<T>> invocableClass = (Class<? extends Invocable<T>>)
            (specifiedBy == null ? DefaultInvocable.class : specifiedBy.value());

        // establish a Context to create the Invocable
        final Context context = FRAMEWORK.newContext();

        // allow the Project to resolve Dependencies
        context.addResolver(new ProjectResourceResolver(project));

        // allow the Plugin to provide values for injection
        context.addResolver(ProvidesResolver.of(plugin, FRAMEWORK));

        // allow the Project, Plugin and Task class to be injected
        context.bind(Project.class).to(project);
        context.bind(Plugin.class).to(plugin);
        context.bind(Class.class).to(taskClass);

        // create the Invocable
        return context.create(invocableClass);
    }

    /**
     * Creates the {@link URI} for the given {@link Task} {@link Invocable}.
     *
     * @param invocable the {@link Invocable}
     * @return the {@link URI}
     */
    public static URI createURI(final Invocable<?> invocable) {
        // create the path for the Task based on the path of the Project
        String path = "";
        Project project = invocable.getProject();
        while (project != null) {
            path = project.name() + "/" + path;
            project = project.parent().orElse(null);
        }

        path = path + invocable.getTaskName();

        return UniformResource.createURI("task", UniformResource.sanitize(path));
    }

    /**
     * Obtains a {@link Stream} of {@link Invocable}s for {@link Task}s defined for a {@link Project} by
     * a {@link Plugin} for the said {@link Project}.
     * <p>
     * By default, this method returns a {@link Stream} containing the non-{@code abstract},
     * {@code public} {@code static} inner {@link Class}es defined within the {@link Plugin} implementation that
     * implement the {@link Task} interface.
     *
     * @param project the {@link Project} for the {@link Plugin}
     * @param plugin  the {@link Plugin} {@link Invocable}s
     * @return A stream of task definitions
     */
    @SuppressWarnings("unchecked")
    public static Stream<Invocable<?>> stream(final Project project, final Plugin plugin) {
        Objects.requireNonNull(project, "The Project must not be null");
        Objects.requireNonNull(plugin, "The Plugin must not be null");

        return Introspection.getAll(plugin.getClass(), Class::getDeclaredClasses)
            .filter(c -> Modifier.isPublic(c.getModifiers()))
            .filter(c -> !Modifier.isAbstract(c.getModifiers()))
            .filter(c -> Modifier.isStatic(c.getModifiers()))
            .filter(Task.class::isAssignableFrom)
            .map(c -> (Class<? extends Task<Object>>) c)
            .map(c -> Invocables.create(project, plugin, c));
    }
}
