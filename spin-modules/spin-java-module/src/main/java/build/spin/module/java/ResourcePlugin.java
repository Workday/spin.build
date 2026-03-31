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
import build.spin.annotation.Before;
import build.spin.annotation.From;
import build.spin.common.task.AbstractCopy;
import build.spin.common.task.DetectSourcePaths;
import build.spin.module.clean.CleanPlugin;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link Plugin} for processing resource for Java-based Modules, like those placed in META-INF/ source directories.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
public class ResourcePlugin
    implements Plugin {

    /**
     * A {@link Task} to detect the {@link Path}s of resources for a Java-based Module.
     */
    @Named("detect.module.resource.paths")
    public static class DetectModuleResourcePaths
        implements DetectSourcePaths {

        @Inject
        private Path projectPath;

        @Override
        public PathSet detect() {

            final PathSetBuilder builder = PathSetBuilder.create();

            final Path defaultPath = this.projectPath.resolve("src/main/resources");
            builder.add(defaultPath);

            return builder.build();
        }
    }

    /**
     * A {@link Task} to copy the source module resources into the build.
     */
    @Named("copy.module.resources")
    @Before(JavaCompilerPlugin.Compile.class)
    public static class CopyModuleResources
        extends AbstractCopy {

        @Inject
        private Path path;

        @Inject
        private TargetDirectoryName target;

        public PathSet copy(final @From(DetectModuleResourcePaths.class) PathSet paths,
                            final @From(CleanPlugin.CreateBuildPath.class) Path buildPath) {

            final PathSetBuilder builder = PathSetBuilder.create();
            final Path destination = buildPath.resolve("main/" + this.target.get());

            paths.stream()
                .map(source -> super.copy(source, destination))
                .flatMap(PathSet::stream)
                .forEach(builder::add);

            return builder.build();
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link ResourcePlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Override
        public boolean isDetectedIn(final Path path) {
            // we only detect Resources for Java-based Projects, not based on the presence in a Path
            return false;
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            return project.plugins(JavaCompilerPlugin.class).findFirst().isPresent()
                && Files.exists(project.path().resolve("src/main/resources"));
        }
    }
}
