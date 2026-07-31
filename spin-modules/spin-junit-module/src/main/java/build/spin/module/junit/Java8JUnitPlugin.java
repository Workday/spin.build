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
import build.base.option.JDKVersion;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.annotation.From;
import build.spin.annotation.System;
import build.spin.common.task.AbstractDetectSourcePaths;
import build.spin.common.task.SourcePathKind;
import build.spin.module.clean.CleanPlugin;
import build.spin.module.java.AbstractCompile;
import build.spin.module.java.Java8CompilerPlugin;
import build.spin.module.modulesystem.CompilationResolution;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A {@link JUnitPlugin} for Java 8-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Oct-2019
 */
public class Java8JUnitPlugin
    extends AbstractJUnitPlugin {

    /**
     * Constructs a {@link Java8JUnitPlugin}.
     */
    public Java8JUnitPlugin() {
        super(JDKVersion.of(8));
    }

    /**
     * A {@link build.spin.Task} to determine the source paths for compilation.
     */
    @Named("detect.test.source.paths")
    public static class DetectSourcePaths
        extends AbstractDetectSourcePaths {

        @Override
        protected Set<SourcePathKind> kinds() {
            return EnumSet.of(SourcePathKind.TEST);
        }
    }

    /**
     * A {@link build.spin.Task} that resolves the full source-graph dependency closure for test
     * compilation and classifies candidates into module-path vs classpath.
     *
     * <p>Adds the project's own main compiled classes directory as an additional sibling candidate
     * so they appear on the module-path or classpath alongside the external dependencies.
     */
    @Named("detect.test.compilation.resolution")
    public static class DetectTestResolution
        extends AbstractDetectTestResolution {
    }

    /**
     * A {@link build.spin.Task} to compile the source code in the {@link Project}.
     */
    @Named("test-compile")
    public static class Compile
        extends AbstractCompile
        implements JUnitPlugin.Compile {

        @Inject
        private TargetDirectoryName target;

        @Inject
        private Project project;

        @Override
        public Stream<Reference> dependencies() {
            // when this is a Java project, we include Java.Compile as a prerequisite
            return this.project.getPlugin(Java8CompilerPlugin.class)
                .map(java ->
                    Stream.concat(
                        Stream.of(Reference.of(this.project, Java8CompilerPlugin.Compile.class)),
                        super.dependencies()))
                .orElse(super.dependencies());
        }

        /**
         * Compiles the test source code detected under the provided {@link SourcePathKind}s into the
         * specified build {@link Path}.
         *
         * @param sourcePaths the detected source root directories, by {@link SourcePathKind}
         * @param resolution the {@link CompilationResolution} (module-path and classpath)
         * @param buildPath  the build {@link Path}
         *
         * @return the {@link PathSet} containing the compiled classes
         * @throws Exception should compilation fail
         */
        public PathSet compile(final @From(DetectSourcePaths.class) Map<SourcePathKind, PathSet> sourcePaths,
                               final @From(DetectTestResolution.class) CompilationResolution resolution,
                               final @From(CleanPlugin.CreateBuildPath.class) Path buildPath)
            throws Exception {

            // the path in which to place the compiled classes
            final Path targetPath = buildPath.resolve("test/" + this.target.get());

            final PathSet sourceCode = build.spin.common.task.DetectSourcePaths.filesOf(sourcePaths, SourcePathKind.TEST);

            return super.compile(sourceCode, resolution, buildPath, targetPath);
        }
    }

    /**
     * A {@link build.spin.Task} to execute compiled tests in the {@link Project}.
     */
    @Named("test")
    public static class Test
        extends AbstractTest {

        @Inject
        private Project project;

        @Override
        public Stream<Reference> dependencies() {
            return Stream.concat(
                Stream.of(Reference.of(this.project, Compile.class)),
                super.dependencies());
        }

        public PathSet test(final @From(DetectTestResolution.class) CompilationResolution resolution,
                            final @From(CleanPlugin.CreateBuildPath.class) Path buildPath) {

            return super.test(resolution, buildPath);
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link Java8JUnitPlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Inject
        @System
        private JDKVersion defaultJavaVersion;

        @Override
        public boolean isDetectedIn(final Path path) {
            return (Files.exists(path.resolve("src/test/java")) && this.defaultJavaVersion.major() == 8)
                || Files.exists(path.resolve("src/test/java8"));
        }
    }
}
