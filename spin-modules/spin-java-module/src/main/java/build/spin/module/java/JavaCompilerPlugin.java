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
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.Category;
import build.spin.common.annotation.Ordered;
import build.spin.module.modulesystem.CompilationResolution;

import java.nio.file.Path;

/**
 * A {@link JavaPlugin} for a compiling Java-based {@link Project}s using the Java Platform {@code javac} command.
 * <p>
 * While may types of {@link Plugin}s may use {@code javac} internally, this {@link Plugin} is
 * <strong>specifically</strong> intended for compiling source code that becomes part of a Java Archive.
 * For example, {@code Junit} and other test frameworks, while compiling Java code and using {@code javac}, are
 * <strong>not</strong> considered {@link JavaCompilerPlugin}s.
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public interface JavaCompilerPlugin
    extends JavaPlugin, Plugin.Comparable<JavaCompilerPlugin> {

    @Override
    default int compareTo(final JavaCompilerPlugin other) {
        return getJavaVersion().compareTo(other.getJavaVersion());
    }

    /**
     * A {@link Ordered} {@link Task} that compiles Java Source Code.
     */
    @Ordered(plugin = JavaCompilerPlugin.class, task = JavaCompilerPlugin.Compile.class)
    @Category("compile")
    @Category("build")
    interface Compile
        extends Task<PathSet> {

    }

    /**
     * A {@link Task} to detect the location of Java source files.
     */
    interface DetectSourceFiles
        extends build.spin.common.task.DetectSourceFiles {

    }

    /**
     * A {@link Task} to detect all Java source files — both declared and annotation-processor
     * generated — for use by analysis consumers such as javadoc.
     */
    interface DetectAllSourceFiles
        extends build.spin.common.task.DetectAllSourceFiles {

    }

    /**
     * A {@link Task} to detect the location of Java source paths.
     */
    interface DetectSourcePaths
        extends build.spin.common.task.DetectSourcePaths {

    }

    /**
     * A {@link Task} to detect annotation-processor generated source paths from a prior build.
     * Only surfaces directories that already exist on disk — never participates in compilation.
     */
    interface DetectGeneratedSourcePaths
        extends build.spin.common.task.DetectGeneratedSourcePaths {

    }

    /**
     * A {@link Task} to detect annotation-processor generated source files from a prior build.
     */
    interface DetectGeneratedSourceFiles
        extends build.spin.common.task.DetectGeneratedSourceFiles {

    }

    /**
     * A {@link Task} that merges {@link DetectSourcePaths} and {@link DetectGeneratedSourcePaths}
     * into a single {@link PathSet} of source root directories.
     *
     * <p>Intended for analysis-only consumers (e.g. language servers, documentation indexers) that
     * need all source roots in one shot without triggering compilation.
     */
    interface DetectAllSourcePaths
        extends build.spin.common.task.DetectAllSourcePaths {

    }

    /**
     * A {@link Task} to detect the {@link CompilationResolution} for the {@link Project}.
     */
    interface DetectCompilationResolution
        extends Task<CompilationResolution> {

    }

    /**
     * A {@link Task} to generate and compile Java Documentation from source paths into a destination {@link Path}.
     */
    interface JavaDoc
        extends Task<Path> {

    }
}
