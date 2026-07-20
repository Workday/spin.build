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

import java.util.ArrayDeque;

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
     * Depth-first closes the {@link AutoCloseable} {@link Plugin}s and {@link Resource}s in the {@link Workspace},
     * attempting to close every one of them, even if one or more fail to close.
     *
     * @throws RuntimeException wrapping the first failure to close an {@link AutoCloseable}, with any subsequent
     *         failures added as {@linkplain Throwable#addSuppressed(Throwable) suppressed} exceptions, when one or
     *         more {@link AutoCloseable}s failed to close
     */
    @Override
    default void close() {

        final var failures = new ArrayDeque<RuntimeException>();

        walk(new Visitor<>() {

            @Override
            public void visit(final Project project) {
                // there's nothing to do upon visiting a Project
                // (only upon leaving a Project)
            }

            @Override
            public void onLeaving(final Project project) {
                // attempt to close every AutoCloseable Extension (Plugins, then Resources - see
                // Project#extensions()), collecting rather than propagating failures, so that a failure to close
                // one doesn't prevent the rest from being closed
                project.extensions(AutoCloseable.class)
                    .forEach(closeable -> {
                        try {
                            closeable.close();
                        }
                        catch (final Exception e) {
                            failures.add(new RuntimeException(e));
                        }
                    });
            }
        });

        if (!failures.isEmpty()) {
            final var first = failures.poll();
            failures.forEach(first::addSuppressed);
            throw first;
        }
    }
}
