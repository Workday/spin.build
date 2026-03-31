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
import build.base.option.JDKVersion;
import build.spin.annotation.System;
import build.spin.common.task.DetectSourcePaths;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects the {@link Path}s for multi-version Java source code, typically for compilation using a
 * {@link JavaCompilerPlugin}.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
public abstract class AbstractDetectSourcePaths
    implements DetectSourcePaths {

    @Inject
    private Path projectPath;

    @Inject
    @System
    private JDKVersion defaultJavaVersion;

    @Inject
    private JDKVersion javaVersion;

    /**
     * Obtains the default location for source code, relative to the {@link #projectPath}.
     *
     * @return the default location for source code
     */
    protected String getRelativeSourcePath() {
        return "src/main/java";
    }

    @Override
    public PathSet detect() {

        final PathSetBuilder builder = PathSetBuilder.create();

        // include the default source path iff this the plugin is for the default version
        final Path defaultPath = this.projectPath.resolve(getRelativeSourcePath());

        if (Files.exists(defaultPath)
            && this.defaultJavaVersion.major() == this.javaVersion.major()) {
            builder.add(defaultPath);
        }

        // include the specific source path iff it exists
        final Path specificPath = this.projectPath.resolve(getRelativeSourcePath() + this.javaVersion.major());
        if (Files.exists(specificPath)) {
            builder.add(specificPath);
        }

        return builder.build();
    }
}
