package build.spin.module.clean;

/*-
 * #%L
 * Spin Clean Module
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

import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.option.BuildDirectoryName;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * A {@link Plugin} to establish the directory in which {@link Project} {@link Task}s should output files and resources.
 *
 * @see BuildDirectoryName
 *
 * @author brian.oliver
 * @since Aug-2020
 */
@SuppressWarnings("HideUtilityClassConstructor")
public class CleanPlugin
    implements Plugin {

    /**
     * Attempts to delete file system contents at the specified {@link Path}.
     * <p>
     * Should the {@link Path} be a directory, an attempt is made to recursively delete the contents of {@link Path}.
     *
     * @param path the {@link Path}
     * @return {@code true} if the contents no longer exist, {@code false} otherwise
     */
    public static boolean delete(final Path path) {
        try {
            // remove the specified Path
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    java.nio.file.Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .filter(File::exists)
                        .forEach(File::delete);

                    return true;
                }
                else {
                    Files.delete(path);

                    return true;
                }
            }

            // when the Path doesn't exist, we return that true because it doesn't exist
            return true;
        }
        catch (final IOException e) {
            return false;
        }
    }

    /**
     * A {@link Task} to remove a previously created build {@link Path} for a {@link Project}.
     */
    @Named("clean")
    public static class RemoveBuildPath
        implements Task<Path> {

        @Inject
        private Path path;

        @Inject
        private BuildDirectoryName buildDirectoryName;

        /**
         * Removes all files in the {@link Path} with the {@link BuildDirectoryName}.
         *
         * @return the build {@link Path}.
         */
        public Path clean() {

            final Path buildPath = this.path.resolve(this.buildDirectoryName.get());

            if (delete(buildPath)) {
                return buildPath;
            }

            throw new RuntimeException("Failed to remove build path [" + buildPath + "]");
        }
    }

    /**
     * A {@link Task} to establish the build directory for a {@link Project}.
     */
    @Named("create.build.path")
    public static class CreateBuildPath
        implements Task<Path> {

        @Inject
        private Path path;

        @Inject
        private BuildDirectoryName buildDirectoryName;

        /**
         * Creates the directory with the {@link BuildDirectoryName} in which {@link Task}s for the {@link Project}
         * may create files and resources.
         *
         * @return the build {@link Path}.
         */
        public Path create() {

            final Path path = this.path.resolve(this.buildDirectoryName.get());

            try {
                Files.createDirectories(path);
            }
            catch (final IOException e) {
                throw new RuntimeException("Failed to create [" + path + "]", e);
            }

            return path;
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link CleanPlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Inject
        private BuildDirectoryName buildDirectoryName;

        @Override
        public boolean isDetectedIn(final Path path) {
            return Files.exists(path.resolve(this.buildDirectoryName.get()));
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            // automatically allow the CleanPlugin Tasks to operate when a Project has been detected.
            return true;
        }
    }
}
