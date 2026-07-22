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

import build.base.configuration.AbstractValueOption;
import build.codemodel.dependency.injection.Context;
import build.codemodel.dependency.injection.Dependency;
import build.codemodel.dependency.injection.IndependentDependency;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.UnsatisfiedDependencyException;
import build.codemodel.dependency.injection.ValueBinding;
import build.codemodel.foundation.descriptor.FormalParameterDescriptor;
import build.codemodel.jdk.descriptor.MethodType;
import build.codemodel.jdk.descriptor.Static;
import build.codemodel.objectoriented.descriptor.AccessModifier;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static build.base.foundation.Introspection.describe;


/**
 * Defines a {@link Task} that may be executed in a {@link Project}.
 * <p>
 * {@link Task}s <strong>must</strong> be defined as {@code public} {@code static} inner {@link Class}es of
 * {@link Plugin}s.
 *
 * @param <T> the type of result produced by the {@link Task}
 * @author brian.oliver
 * @since Jun-2019
 */
public interface Task<T> {

    /**
     * Obtains a {@link Stream} of {@link Reference}s to {@link Task}s that are dependencies of
     * this {@link Task}.  These {@link Task}s will be executed prior to this {@link Task} being executed.
     *
     * @return a {@link Stream} of {@link Reference}
     */
    default Stream<Reference> dependencies() {
        return Stream.empty();
    }

    /**
     * Executes the {@link Task} using the provided {@link Context}.
     * <p>
     * By default, this method locates the first declared method on the {@link Task} that returns the
     * generic type specified by the {@link Task}, resolves the required formal parameters specified by the
     * method, then invokes it.
     *
     * @param invocable the {@link Invocable} for the {@link Task}
     * @param context   the context for the {@link Task}
     * @param framework the {@link InjectionFramework}
     * @return the result of executing the {@link Task}
     */
    @SuppressWarnings("unchecked")
    default T execute(final Invocable<?> invocable, final Context context, final InjectionFramework framework) {

        final Class<?> taskResultClass = invocable.getTaskResultClass().orElse(void.class);

        final var codemodel = framework.codeModel();

        // locate the public non-static method returning the result class via the codemodel
        final Optional<MethodDescriptor> optionalMethod = codemodel
            .getJDKTypeDescriptor(getClass())
            .flatMap(typeDescriptor ->
                codemodel.getTraitsInHierarchy(typeDescriptor, MethodDescriptor.class)
                    .filter(md -> md.getTrait(Static.class).isEmpty())
                    .filter(md -> md.getTrait(AccessModifier.class)
                        .map(am -> am == AccessModifier.PUBLIC)
                        .orElse(false))
                    .filter(md -> md.getTrait(MethodType.class)
                        .map(MethodType::method)
                        .map(m -> (m.getReturnType().equals(void.class) && taskResultClass.equals(Void.class))
                            || taskResultClass.isAssignableFrom(m.getReturnType()))
                        .orElse(false))
                    .findFirst());

        if (optionalMethod.isPresent()) {

            final MethodDescriptor taskMethod = optionalMethod.get();
            final FormalParameterDescriptor[] formalParams = taskMethod.formalParameters()
                .toArray(FormalParameterDescriptor[]::new);

            // resolve the parameters for the method from the context
            final Object[] parameters = new Object[formalParams.length];

            for (int i = 0; i < formalParams.length; i++) {
                final Dependency dependency = IndependentDependency.of(
                    formalParams[i].type(),
                    framework::getQualifierAnnotationTypes);

                @SuppressWarnings("unchecked") final Optional<Object> value = (Optional<Object>) context.resolver().resolve(dependency)
                    .map(b -> b instanceof ValueBinding<?> vb ? vb.value() : null)
                    .filter(Objects::nonNull);

                parameters[i] = value.orElseThrow(() -> new UnsatisfiedDependencyException(dependency));
            }

            // snapshot any Stream parameters as lists so we can show their contents in error messages
            // and reconstruct them for invocation
            final Object[] snapshots = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i] instanceof Stream<?> s) {
                    final List<?> list = s.toList();
                    parameters[i] = list.stream();
                    snapshots[i] = list;
                } else {
                    snapshots[i] = describeTaskParam(parameters[i]);
                }
            }

            // attempt to perform the task!
            final var method = taskMethod.getTrait(MethodType.class)
                .map(MethodType::method)
                .orElseThrow();

            try {
                return (T) method.invoke(this, parameters);
            } catch (final IllegalAccessException | InvocationTargetException e) {
                final StringBuilder params = new StringBuilder("[");
                for (int i = 0; i < snapshots.length; i++) {
                    if (i > 0) {
                        params.append(", ");
                    }
                    params.append(snapshots[i]);
                }
                params.append("]");
                throw new RuntimeException(
                    "Failed to invoke " + describe(method) +
                        " with parameters " + params, e);
            }
        } else {
            throw new UnsupportedOperationException("Could not determine a public non-static method of " +
                describe(getClass()) + " that returns " + describe(taskResultClass) + " to execute");
        }
    }

    /**
     * Returns a readable description of a task parameter for error messages.
     * <p>
     * {@link Iterable} values (e.g. {@code ModulePath}, {@code ClassPath}) are expanded to a
     * list of their elements; all other values use their own {@code toString()}.
     *
     * @param param the parameter value, possibly {@code null}
     * @return a {@link String} describing the parameter
     */
    private static String describeTaskParam(final Object param) {
        return switch (param) {
            case null -> "null";
            case java.nio.file.Path p -> p.toString();
            case Iterable<?> it -> StreamSupport.stream(it.spliterator(), false).toList().toString();
            default -> param.toString();
        };
    }

    /**
     * A pattern to use for matching {@link Task}s to include in a {@link Program}.
     */
    class Pattern
        extends AbstractValueOption<String> {

        /**
         * Constructs a {@link Task.Pattern}.
         *
         * @param pattern the {@link Task.Pattern}
         */
        private Pattern(final String pattern) {
            super(pattern);
        }

        /**
         * Creates a new {@link Task.Pattern}
         *
         * @param pattern the pattern
         * @return a new {@link Task.Pattern}
         */
        public static Pattern of(final String pattern) {
            return new Pattern(pattern);
        }
    }
}
