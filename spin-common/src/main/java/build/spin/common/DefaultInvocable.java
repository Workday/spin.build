package build.spin.common;

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

import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.common.util.Invocables;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.stream.Stream;

import static build.base.foundation.Introspection.describe;

/**
 * The default implementation of an {@link Invocable}.
 *
 * @param <T> the type of return value from the {@link Task}
 *
 * @author brian.oliver
 * @since Jan-2021
 */
public class DefaultInvocable<T>
    implements Invocable<T> {

    /**
     * The {@link Project} in which the {@link Task} is defined.
     */
    private final Project project;

    /**
     * The {@link Plugin} defining the {@link Task}
     */
    private final Plugin plugin;

    /**
     * The concrete {@link Class} of the {@link Task}
     */
    private final Class<? extends Task<T>> taskClass;

    /**
     * The {@link URI} of the {@link Task}
     */
    private final URI uri;

    /**
     * Constructs a {@link DefaultInvocable} {@link Task} {@link Invocable}.
     *
     * @param project the {@link Project}
     * @param plugin the {@link Plugin}
     * @param taskClass the {@link Task} {@link Class}
     */
    @Inject
    public DefaultInvocable(final Project project,
                            final Plugin plugin,
                            final Class<? extends Task<T>> taskClass) {
        this.project = project;
        this.plugin = plugin;
        this.taskClass = taskClass;
        this.uri = Invocables.createURI(this);
    }

    @Override
    public Project getProject() {
        return this.project;
    }

    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public Class<? extends Task<T>> getTaskClass() {
        return this.taskClass;
    }

    @Override
    public URI getURI() {
        return this.uri;
    }

    @Override
    public Stream<Reference> dependencies() {
        return Stream.concat(Invocable.super.dependencies(), this.plugin.projectDependencies(this.taskClass));
    }

    @Override
    public String toString() {
        return "Task for Project [" + getProject().name() + "] " +
            "Plugin [" + getPlugin().name() + "] " +
            "Task [" + getTaskName() + "] " +
            "Class [" + describe(this.taskClass) + "]";
    }

    @Override
    public int hashCode() {
        return this.project.hashCode() + this.plugin.hashCode() + this.taskClass.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof Invocable &&
            getProject().name().equals(((Invocable<?>) object).getProject().name()) &&
            getPlugin().name().equals(((Invocable<?>) object).getPlugin().name()) &&
            getTaskClass().equals(((Invocable<?>) object).getTaskClass());
    }
}
