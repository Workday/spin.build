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

import java.util.Objects;

import static build.base.foundation.Introspection.describe;

/**
 * Defines a reference to a {@link Class} of {@link Task} in a specific {@link Project}.
 *
 * @author brian.oliver
 * @since Jun-2019
 */
public interface Reference {

    /**
     * Creates a {@link Reference} for a {@link Class} of {@link Task} in a specific {@link Project}.
     *
     * @param project the {@link Project}
     * @param taskClass the {@link Class} of {@link Task}
     *
     * @return a new {@link Reference}
     */
    static Reference of(final Project project, final Class<? extends Task<?>> taskClass) {
        return new Reference() {
            @Override
            public Project project() {
                return project;
            }

            @Override
            public Class<? extends Task<?>> getTaskClass() {
                return taskClass;
            }

            @Override
            public String toString() {
                return "Project [" + project.name() + "] " +
                    "Plugin [" + describe(getPluginClass()) + "] " +
                    "Task [" + describe(taskClass) + "] ";
            }

            @Override
            public int hashCode() {
                return project.hashCode() + taskClass.hashCode();
            }

            @Override
            public boolean equals(final Object object) {
                return object instanceof Reference
                    && project().equals(((Reference) object).project())
                    && Objects.equals(getPluginClass(), ((Reference) object).getPluginClass())
                    && getTaskClass().equals(((Reference) object).getTaskClass());
            }
        };
    }

    /**
     * Obtains the {@link Project} in which the {@link Task} is defined.
     *
     * @return the {@link Project}
     */
    Project project();

    /**
     * Obtains the {@link Class} of the {@link Task}.
     *
     * @return the {@link Class} of the {@link Task}
     */
    Class<? extends Task<?>> getTaskClass();

    /**
     * Obtains the {@link Class} of {@link Plugin} that defines the {@link Task}.
     *
     * @return the {@link Class} of {@link Plugin}
     */
    @SuppressWarnings("unchecked")
    default Class<? extends Plugin> getPluginClass() {
        return (Class<? extends Plugin>) getTaskClass().getDeclaringClass();
    }
}
