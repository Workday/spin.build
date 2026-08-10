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

import build.base.foundation.Introspection;
import build.codemodel.dependency.injection.Context;
import build.spin.annotation.After;
import build.spin.annotation.Automatic;
import build.spin.annotation.Before;
import build.spin.annotation.Categories;
import build.spin.annotation.Category;
import build.spin.annotation.From;
import build.spin.annotation.Merge;
import build.spin.annotation.NoCache;
import build.spin.annotation.PostProcess;
import build.spin.annotation.PreProcess;
import jakarta.inject.Named;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static build.base.foundation.Introspection.describe;

/**
 * Provides immutable {@link Project} specific information for a {@link Class} of {@link Task}, that may later
 * be used to establish {@link Instruction}s for {@link Program}s.
 *
 * @param <T> the type of result produced by the {@link Task}
 * @author brian.oliver
 * @since Jun-2019
 */
public interface Invocable<T> {

    /**
     * Obtains the {@link URI} for this {@link Invocable}.
     *
     * @return the {@link URI}
     */
    URI getURI();

    /**
     * Obtains the {@link Project} in which the {@link Plugin} {@link Task} has been defined.
     *
     * @return the {@link Project}
     */
    Project getProject();

    /**
     * Obtains the {@link Plugin} that defined the {@link Task}.
     *
     * @return the {@link Task} {@link Plugin}
     */
    Plugin getPlugin();

    /**
     * Obtains the {@link Class} of {@link Task}.
     *
     * @return the {@link Class} of {@link Task}.
     */
    Class<? extends Task<T>> getTaskClass();

    /**
     * Obtains the {@link Reference} for the defined {@link Task}.
     *
     * @return the {@link Reference}
     */
    default Reference getReference() {
        return Reference.of(getProject(), getTaskClass());
    }

    /**
     * Creates a new instance of the {@link Task} using the provided {@link Context}.
     *
     * @param context the {@link Context}
     * @return a new instance of the {@link Task}
     */
    default Task<T> createTask(final Context context) {
        return context.create(getTaskClass());
    }

    /**
     * Obtains the name of the {@link Task}.
     * <p>
     * By default, the name of the {@link Task} is the value defined by the {@code @Named} annotation on the
     * {@link Class} for the {@link Task}.  Should no {@link Named} annotation be defined, the name
     * is the simple name of the implementation {@link Class}.
     *
     * @return the name of the {@link Task}
     */
    default String getTaskName() {
        final Named named = getTaskClass().getAnnotation(Named.class);

        return named == null ? getTaskClass().getSimpleName() : named.value().trim();
    }

    /**
     * Obtains the names of the {@link Category}s defined for the {@link Task}.
     *
     * @return the {@link Stream} of {@link Category} names
     */
    default Stream<String> categories() {
        final List<Annotation> annotations = Introspection.getAll(getTaskClass(), Class::getDeclaredAnnotations).toList();

        return Stream.concat(
                annotations.stream()
                    .filter(Category.class::isInstance)
                    .map(Category.class::cast),
                annotations.stream()
                    .filter(Categories.class::isInstance)
                    .map(Categories.class::cast)
                    .flatMap(categories -> Arrays.stream(categories.value())))
            .map(Category::value);
    }

    /**
     * Determines if the {@link Task} matches the supplied {@link build.spin.Task.Pattern}, ultimately for
     * inclusion in a {@link Program}.
     *
     * @param pattern the {@link build.spin.Task.Pattern}
     * @return {@code true} if the {@link Task} matches the {@link build.spin.Task.Pattern},
     * {@code false} otherwise
     */
    default boolean matches(final Task.Pattern pattern) {
        return pattern != null
            && (getTaskName().equals(pattern.get())
            || categories().anyMatch(pattern.get()::equals));
    }

    /**
     * Obtains the {@link Class} of the result produced by executing the {@link Task}.
     * <p>
     * A {@link Task} declared without a generic result type — {@code implements Task} rather than
     * {@code Task<T>}, i.e. a {@link Task} whose method returns {@code void} — has no {@code Task<T>}
     * type argument to resolve; this is reported as {@code void.class} rather than
     * {@link Optional#empty()}, so every consumer of this method (locating the task method to invoke,
     * locating its {@link From}-annotated dependencies, etc.) treats "no declared result" uniformly
     * rather than each needing its own {@code void.class} fallback.
     *
     * @return {@link Optional} {@link Class}, never {@link Optional#empty()}
     */
    default Optional<Class<?>> getTaskResultClass() {
        final Optional<Class<?>> declared = Introspection.getAll(getTaskClass(), Class::getGenericInterfaces)
            .filter(ParameterizedType.class::isInstance)
            .map(ParameterizedType.class::cast)
            .filter(type -> type.getRawType().equals(Task.class))
            .filter(type -> type.getActualTypeArguments().length == 1)
            .map(type -> type.getActualTypeArguments()[0])
            // the type argument is a plain Class for a simple result type (e.g. Task<Path>), or a
            // ParameterizedType for a generic result type (e.g. Task<Set<Path>>) — generics are erased
            // at runtime, so either way the raw Class is what a task's declared return type resolves to
            .map(type -> switch (type) {
                case Class<?> c -> c;
                case ParameterizedType pt when pt.getRawType() instanceof Class<?> c -> c;
                default -> null;
            })
            .filter(java.util.Objects::nonNull)
            .findFirst();

        return declared.isPresent() ? declared : Optional.of(void.class);
    }

    /**
     * Determines if {@code methodReturnType} implements a {@link Task}'s declared result type,
     * {@code taskResultClass} — either directly assignable, or the special case of a method
     * returning primitive {@code void} for a {@code Task<Void>} (reflection has no way to declare
     * a method returning the boxed {@link Void}, so a real {@code void} method is how
     * {@code Task<Void>} is implemented).
     */
    static boolean isTaskResultMethod(final Class<?> taskResultClass, final Class<?> methodReturnType) {
        return (methodReturnType.equals(void.class) && taskResultClass.equals(Void.class))
            || taskResultClass.isAssignableFrom(methodReturnType);
    }

    /**
     * Obtains the {@link Stream} of {@link Reference}s for {@link Task}s that are
     * dependencies of the {@link Task}.
     * <p>
     * By default, this method returns {@link Reference}s for {@link Task}s specified
     * as {@link From} parameters of the {@link Task} execution method.
     *
     * @return a {@link Stream} of {@link Reference}s
     */
    default Stream<Reference> dependencies() {

        // determine the task result class (never empty — see getTaskResultClass())
        final Class<?> taskResultClass = getTaskResultClass().orElseThrow();

            // locate the first visible non-static, public method returning the a class assignable to the result class
            final Optional<Method> optionalMethod = Introspection.getVisibleMethods(getTaskClass(), method ->
                    isTaskResultMethod(taskResultClass, method.getReturnType())
                        && !Modifier.isStatic(method.getModifiers())
                        && Modifier.isPublic(method.getModifiers()))
                .findFirst();

            if (optionalMethod.isPresent()) {
                final Method taskMethod = optionalMethod.get();

                // locate the @From annotated parameters and return a stream of references for them
                return Arrays.stream(taskMethod.getParameters())
                    .filter(parameter -> parameter.getDeclaredAnnotation(From.class) != null)
                    .flatMap(parameter -> {
                        final From from = parameter.getDeclaredAnnotation(From.class);

                        // determine if the Task requires a result from a concrete class or a Stream of results
                        // from an abstract Task
                        final Class<? extends Task<?>> fromTaskClass = from.value();

                        if (fromTaskClass.isInterface() || Modifier.isAbstract(fromTaskClass.getModifiers())) {
                            // ensure the parameter type for injection is a Stream — unless the Task
                            // is annotated @Merge, in which case a plain result type is also allowed:
                            // every matching implementor still becomes a dependency edge here, but the
                            // framework combines their results via the Task's own merge(Stream<T>)
                            // method before injection, rather than requiring the caller to do so
                            if (Stream.class.isAssignableFrom(parameter.getType())
                                || fromTaskClass.isAnnotationPresent(Merge.class)) {

                                return getProject().getInvocables(fromTaskClass)
                                    .map(Invocable::getReference);
                            }

                            // as we're using an abstract class, the parameter to be injected must be a
                            // Stream, unless the Task is annotated @Merge
                            throw new IllegalArgumentException("The @From parameter [" + describe(parameter) + "] "
                                + "defined in [" + describe(taskMethod) + "] "
                                + "must be a Stream<T> as the declared @From Task is abstract, "
                                + "or the Task must be annotated with @Merge");
                        }

                        // use a reference to the concrete Task
                        return Stream.of(Reference.of(getProject(), fromTaskClass));
                    });
        }

        return Stream.empty();
    }

    /**
     * Determines if this {@link Task} is a codependency of another {@link Task}.
     *
     * @return {@code true} if the {@link Task} is a codependency, {@code false} otherwise
     */
    default boolean isCodependency() {
        final Class<? extends Task<T>> taskClass = getTaskClass();

        return Introspection.hasDeclaredAnnotation(taskClass, PreProcess.class)
            || Introspection.hasDeclaredAnnotation(taskClass, PostProcess.class);
    }

    /**
     * Determines if this {@link Task} must be executed {@link Before} the specified {@link Task}.
     *
     * @param reference the {@link Reference}
     * @return {@code true} if the {@link Task} must happen before this {@link Task}, {@code false} otherwise
     */
    default boolean isBefore(final Reference reference) {
        final Before before = getTaskClass().getDeclaredAnnotation(Before.class);

        return before != null && before.value().isAssignableFrom(reference.getTaskClass());
    }

    /**
     * Determines if this {@link Task} must be executed {@link After} the specified {@link Task}.
     *
     * @param reference the {@link Reference}
     * @return {@code true} if the {@link Task} must happen after this {@link Task}, {@code false} otherwise
     */
    default boolean isAfter(final Reference reference) {
        final After after = getTaskClass().getDeclaredAnnotation(After.class);

        return after != null && after.value().isAssignableFrom(reference.getTaskClass());
    }

    /**
     * Determines if this {@link Task} will automatically be included and executed by a {@link Program}.
     *
     * @return {@code true} if the {@link Task} must be executed, {@code false} otherwise
     */
    default boolean isAutomatic() {
        return getTaskClass().isAnnotationPresent(Automatic.class);
    }

    /**
     * Determines if the results from the {@link Task} are cacheable across {@link Program} executions.
     *
     * @return {@code true} if the results are cacheable, {@code false} otherwise
     * @see NoCache
     */
    default boolean isCacheable() {
        return !getTaskClass().isAnnotationPresent(NoCache.class);
    }
}
