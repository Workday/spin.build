package build.spin.common.task;

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

import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.base.telemetry.TelemetryRecorder;
import build.spin.Task;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * An abstract {@link Task} to copy the files one path to another.
 *
 * @author andrew.wilson
 * @author brian.oliver
 * @since Oct-2019
 */
public abstract class AbstractCopy
    implements Task<PathSet> {

    @Inject
    private TelemetryRecorder recorder;

    /**
     * Copy the resources into the target {@link Path}.
     *
     * @param sourcePath the resources {@link Path}
     * @param targetPath the target {@link Path}
     *
     * @return the {@link PathSet} of copied resources
     */
    protected PathSet copy(final Path sourcePath,
                           final Path targetPath) {

        final PathSetBuilder builder = PathSetBuilder.create();

        try {
            Files.createDirectories(targetPath);
        }
        catch (final IOException e) {
            this.recorder.error(e, "Failed to create target path [%s]", targetPath);

            throw new RuntimeException("Failed to create target path", e);
        }

        if (Files.exists(sourcePath)) {
            try {
                Files.walk(sourcePath).forEach(s -> {
                    // determine the destination
                    final Path d = targetPath.resolve(sourcePath.relativize(s));

                    try {
                        if (Files.isDirectory(s)) {
                            Files.createDirectories(d);
                            return;
                        }
                        Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                        builder.add(d);
                    }
                    catch (final Exception e) {
                        this.recorder.error(e, "Failed to copy the resource from [%s] to [%s]", s, d);

                        throw new RuntimeException("Resource Copy Failed", e);
                    }
                });
            }
            catch (final Exception ex) {
                this.recorder.error(ex, "Failed to read the resources at [%s]", sourcePath);

                throw new RuntimeException("Resource Access Failure", ex);
            }
        }
        return builder.build();
    }
}
