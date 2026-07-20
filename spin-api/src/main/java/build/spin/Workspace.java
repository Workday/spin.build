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

/**
 * A {@link Workspace} is a collection of one or more {@link Project}s discovered by an {@link Engine}.
 * <p>
 * A {@link Workspace} represents the root {@link Project}, which is the ultimate ancestor for all child and
 * descendant {@link Project}s.
 * <p>
 * The concept of a {@link Workspace} enables an {@link Engine} to:
 * <ol>
 *     <li>Capture and configure settings that apply to all {@link Project}s in a {@link Workspace}.</li>
 *     <li>Persist information concerning configurations used by a {@link Workspace} and its {@link Project}s.</li>
 * </ol>
 *
 * @see Project
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public interface Workspace
    extends Project, AutoCloseable {

    /**
     * Depth-first closes the {@link AutoCloseable} {@link Plugin}s and {@link Resource}s in the {@link Workspace}.
     */
    @Override
    default void close() {

        walk(new Visitor<>() {

            @Override
            public void visit(final Project project) {
                // there's nothing to do upon visiting a Project
                // (only upon leaving a Project)
            }

            @Override
            public void onLeaving(final Project project) {
                // close the AutoCloseable Extensions (Plugins, then Resources - see Project#extensions())
                project.extensions(AutoCloseable.class)
                    .forEach(closeable -> {
                        try {
                            closeable.close();
                        }
                        catch (final Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            }
        });
    }
}
