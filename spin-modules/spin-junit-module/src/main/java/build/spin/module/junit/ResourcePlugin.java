package build.spin.module.junit;

/*-
 * #%L
 * Spin Junit Module
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
import build.spin.Plugin;
import build.spin.annotation.Before;
import build.spin.annotation.From;
import build.spin.common.task.SourcePathKind;
import build.spin.module.clean.CleanPlugin;
import build.spin.module.java.AbstractResourcePlugin;
import jakarta.inject.Named;

import java.nio.file.Path;

/**
 * A {@link Plugin} for processing resources for JUnit, like those placed in META-INF/ source directories.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
public class ResourcePlugin
    implements Plugin {

    /**
     * A {@link build.spin.Task} to detect the {@link Path}s of resources for a JUnit-based Module.
     */
    @Named("detect.junit.resource.paths")
    public static class DetectModuleResourcePaths
        extends AbstractResourcePlugin.DetectResourcePaths {

        @Override
        protected String sourcePath() {
            return "src/test/resources";
        }
    }

    /**
     * A {@link build.spin.Task} to copy the JUnit resources into the build.
     */
    @Named("copy.junit.resources")
    @Before(JUnitPlugin.Compile.class)
    public static class CopyJUnitResources
        extends AbstractResourcePlugin.CopyResources {

        @Override
        protected String destinationPrefix() {
            return SourcePathKind.TEST.outputPrefix().orElseThrow();
        }

        public PathSet copy(final @From(DetectModuleResourcePaths.class) PathSet paths,
                            final @From(CleanPlugin.CreateBuildPath.class) Path buildPath) {
            return super.doCopy(paths, buildPath);
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link ResourcePlugin}.
     */
    public static class MetaClass
        extends AbstractResourcePlugin.MetaClass {

        @Override
        protected Class<? extends Plugin> pluginClass() {
            return JUnitPlugin.class;
        }

        @Override
        protected String resourceDirectory() {
            return "src/test/resources";
        }
    }
}
