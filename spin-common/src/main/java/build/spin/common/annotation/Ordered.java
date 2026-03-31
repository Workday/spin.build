package build.spin.common.annotation;

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

import build.base.foundation.Capture;
import build.base.foundation.Introspection;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.SpecifiedBy;
import build.spin.common.DefaultInvocable;
import build.spin.common.util.Invocables;
import jakarta.inject.Inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.stream.Stream;

/**
 * Indicates that {@link Task} execution must be ordered such that {@link Task}s of the same or common {@link Class},
 * defined by the same or common {@link Class} of {@link Plugin}, in the same {@link Project}, must be executed in
 * order, the order of execution being defined by the comparison of the {@link Task} {@link Plugin}s.
 * <p>
 * {@code Java.Compiler}s are a good example of {@link Ordered} {@link Task}s.  {@code Java.Compiler} {@link Task}s
 * are uniquely defined for each version of Java, in their own {@code JavaPlugin}.   When working with a multi-version
 * Java {@link Project}, say for Java 8 and 11, the {@code Java.Compiler} for Java 8 must be executed
 * before the {@code Java.Compiler} defined for Java 11, and so on.
 *
 * @author brian.oliver
 * @since Jul-2020
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpecifiedBy(Ordered.Orderable.class)
public @interface Ordered {

    /**
     * Obtains the {@link Class} of {@link Comparable} {@link Plugin}.
     *
     * @return the {@link Class} of {@link Comparable} {@link Plugin}.
     */
    Class<? extends Plugin.Comparable<?>> plugin();

    /**
     * Obtains the {@link Class} of {@link Task}, usually an interface or abstract class that a {@link Task} proceeds.
     *
     * @return the {@link Class} of {@link Ordered} {@link Task}
     */
    Class<? extends Task<?>> task();

    /**
     * An {@link build.spin.Invocable} for {@link Ordered} {@link Task}s.
     *
     * @param <T> the return type of the {@link Task}
     */
    class Orderable<T>
        extends DefaultInvocable<T> {

        /**
         * The interface or abstract {@link Class} of the {@link Plugin}.
         */
        private final Class<? extends Plugin.Comparable<?>> pluginClass;

        /**
         * The interface or abstract {@link Class} of the successor {@link Task}.
         */
        private final Class<? extends Task<?>> taskClass;

        /**
         * The {@link URI} for the {@link Task} {@link Orderable}.
         */
        private final URI uri;

        /**
         * Constructs a {@link build.spin.Invocable}.
         *
         * @param project the {@link Project}
         * @param plugin the {@link Plugin}
         * @param taskClass the {@link Task} {@link Class}
         */
        @Inject
        Orderable(final Project project,
                  final Plugin plugin,
                  final Class<? extends Task<T>> taskClass) {

            super(project, plugin, taskClass);

            final Ordered ordered = Introspection.getAllDeclaredAnnotationsByType(taskClass, Ordered.class)
                .findFirst()
                .orElseThrow(() ->
                    new IllegalArgumentException("The Task [" + Introspection.describe(taskClass) +
                        "] is required to define the @Ordered annotation to be ordered"));

            if (!(plugin instanceof Plugin.Comparable)) {
                throw new IllegalArgumentException("The Plugin [" + Introspection.describe(plugin.getClass()) +
                    "] must implement Plugin.Comparable for the Task [" + Introspection.describe(taskClass) +
                    "] to be used as a @Ordered");
            }

            this.pluginClass = ordered.plugin();
            this.taskClass = ordered.task();
            this.uri = Invocables.createURI(this);
        }

        @Override
        public URI getURI() {
            return this.uri;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Stream<Reference> dependencies() {
            // capture the Plugin in the Project that is immediately before the Plug defining this Task
            // (so we can automatically create a dependency on the same class of Task)
            final Capture<Plugin.Comparable<?>> capture = Capture.empty();

            getProject().plugins()
                .filter(this.pluginClass::isInstance)
                .map(Plugin.Comparable.class::cast)
                .forEach(plugin -> {
                    if (plugin.compareTo(getPlugin()) < 0
                        && (!capture.isPresent() || plugin.compareTo(capture.get()) > 0)) {
                        capture.set(plugin);
                    }
                });

            return Stream.concat(
                super.dependencies(),
                capture.isPresent()
                    ? getProject()
                    .invocables()
                    .filter(definition -> definition.getPlugin().equals(capture.get()))
                    .filter(definition -> this.taskClass.isAssignableFrom(definition.getTaskClass()))
                    .findFirst()
                    .map(build.spin.Invocable::getReference)
                    .map(Stream::of)
                    .orElse(Stream.empty())
                    : Stream.empty());
        }
    }
}
