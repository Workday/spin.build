package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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

import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.common.task.AbstractCopy;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract base for resource-copying {@link Plugin}s.
 *
 * <p>Provides shared implementations for the detect, copy, and meta-class inner tasks.
 * Concrete plugins supply the source directory, destination prefix, and detection predicate
 * by implementing a small number of abstract methods.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public abstract class AbstractResourcePlugin {

    /**
     * An abstract base for a {@link Task} that detects a single resource directory.
     */
    public abstract static class DetectResourcePaths
        implements Task<PathSet> {

        @Inject
        private Path projectPath;

        /**
         * Returns the resource source path, relative to the project root (e.g. {@code "src/main/resources"}).
         */
        protected abstract String sourcePath();

        public PathSet detect() {
            return PathSetBuilder.create()
                .add(this.projectPath.resolve(sourcePath()))
                .build();
        }
    }

    /**
     * An abstract base for a resource-copying {@link AbstractCopy} task.
     *
     * <p>Concrete subclasses supply the {@code @Named}, {@code @Before}, and {@code @From}
     * annotations (which must be compile-time constants) and delegate their method body to
     * {@link #doCopy}.
     */
    public abstract static class CopyResources
        extends AbstractCopy {

        @Inject
        private TargetDirectoryName target;

        /**
         * Returns the destination sub-path prefix within the build directory
         * (e.g. {@code "main/"} or {@code "test/"}).
         */
        protected abstract String destinationPrefix();

        /**
         * Copies resources from the detected source paths into the build directory.
         *
         * @param paths     the source resource paths
         * @param buildPath the root build {@link Path}
         * @return the {@link PathSet} of copied resources
         */
        protected PathSet doCopy(final PathSet paths, final Path buildPath) {
            final PathSetBuilder builder = PathSetBuilder.create();
            final Path destination = buildPath.resolve(destinationPrefix() + this.target.get());

            paths.stream()
                .map(source -> super.copy(source, destination))
                .flatMap(PathSet::stream)
                .forEach(builder::add);

            return builder.build();
        }
    }

    /**
     * An abstract {@link Plugin.MetaClass} for resource plugins.
     *
     * <p>Activates when a matching compiler plugin is present and the resource directory exists.
     */
    public abstract static class MetaClass
        implements Plugin.MetaClass {

        /**
         * Returns the {@link Plugin} class whose presence is required for this resource plugin to activate.
         */
        protected abstract Class<? extends Plugin> pluginClass();

        /**
         * Returns the resource directory path, relative to the project root (e.g. {@code "src/main/resources"}).
         */
        protected abstract String resourceDirectory();

        @Override
        public boolean isDetectedIn(final Path path) {
            return false;
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            return project.plugins(pluginClass()).findFirst().isPresent()
                && Files.exists(project.path().resolve(resourceDirectory()));
        }
    }
}
