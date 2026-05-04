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
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.ModulePath;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.annotation.Description;
import build.spin.annotation.From;
import build.spin.annotation.System;
import build.spin.module.clean.CleanPlugin;
import build.spin.module.java.AbstractCompile;
import build.spin.module.java.AbstractDetectClassPath;
import build.spin.module.java.AbstractDetectModulePath;
import build.spin.module.java.AbstractDetectSourceFiles;
import build.spin.module.java.AbstractDetectSourcePaths;
import build.spin.module.java.Java25CompilerPlugin;
import build.spin.module.modulesystem.CompilationResolution;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A {@link JUnitPlugin} for Java 25-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Oct-2019
 */
public class Java25JUnitPlugin
    extends AbstractJUnitPlugin {

    /**
     * Constructs a {@link Java25JUnitPlugin}.
     */
    public Java25JUnitPlugin() {
        super(JDKVersion.of(25));
    }

    /**
     * A {@link build.spin.Task} to determine the source paths for compilation.
     */
    @Named("detect.test.source.paths")
    public static class DetectSourcePaths
        extends AbstractDetectSourcePaths
        implements JUnitPlugin.DetectSourcePaths {

        @Override
        protected String getRelativeSourcePath() {
            return "src/test/java";
        }
    }

    /**
     * A {@link build.spin.Task} to determine the source files for compilation.
     */
    @Named("detect.test.source.files")
    public static class DetectSourceFiles
        extends AbstractDetectSourceFiles
        implements JUnitPlugin.DetectSourceFiles {

        @Override
        public PathSet detect(@From(DetectSourcePaths.class) final PathSet pathSet) {
            return super.detect(pathSet);
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
     * A {@link build.spin.Task} to detect the {@link ModulePath} suitable for <strong>compiling</strong>
     * and <strong>running</strong> tests for the {@link Project}.
     */
    @Named("detect.test.compilation.module.path")
    public static class DetectTestModulePath
        extends AbstractDetectModulePath {

        public ModulePath create(
            @From(DetectTestResolution.class) final CompilationResolution resolution) {

            return super.project(resolution);
        }
    }

    /**
     * A {@link build.spin.Task} to detect the {@link ClassPath} suitable for <strong>compiling</strong>
     * and <strong>running</strong> tests for the {@link Project}.
     */
    @Named("detect.test.compilation.classpath")
    public static class DetectTestClassPath
        extends AbstractDetectClassPath {

        public ClassPath create(
            @From(DetectTestResolution.class) final CompilationResolution resolution) {

            return super.project(resolution);
        }
    }

    /**
     * A {@link build.spin.Task} to compile the source code in the {@link Project}.
     */
    @Named("test-compile")
    @Description("Compile test sources")
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
            return this.project.getPlugin(Java25CompilerPlugin.class)
                .map(java ->
                    Stream.concat(
                        Stream.of(Reference.of(this.project, Java25CompilerPlugin.Compile.class)),
                        super.dependencies()))
                .orElse(super.dependencies());
        }

        /**
         * Compiles the test source code in the provided {@link PathSet} into the specified build {@link Path}.
         *
         * @param sourceCode the source code
         * @param modulePath the {@link ModulePath}
         * @param classPath  the {@link ClassPath}
         * @param buildPath  the build {@link Path}
         *
         * @return the {@link PathSet} containing the compiled classes
         * @throws Exception should compilation fail
         */
        public PathSet compile(final @From(DetectSourceFiles.class) PathSet sourceCode,
                               final @From(DetectTestModulePath.class) ModulePath modulePath,
                               final @From(DetectTestClassPath.class) ClassPath classPath,
                               final @From(CleanPlugin.CreateBuildPath.class) Path buildPath)
            throws Exception {

            // the path in which to place the compiled classes
            final Path targetPath = buildPath.resolve("test/" + this.target.get());

            return super.compile(sourceCode, modulePath, classPath, buildPath, targetPath);
        }
    }

    /**
     * A {@link build.spin.Task} to execute compiled tests in the {@link Project}.
     */
    @Named("test")
    @Description("Run JUnit tests")
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

        public PathSet test(final @From(DetectTestModulePath.class) ModulePath modulePath,
                            final @From(DetectTestClassPath.class) ClassPath classPath,
                            final @From(CleanPlugin.CreateBuildPath.class) Path buildPath) {

            return super.test(modulePath, classPath, buildPath);
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link Java25JUnitPlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Inject
        @System
        private JDKVersion defaultJavaVersion;

        @Override
        public boolean isDetectedIn(final Path path) {
            return (Files.exists(path.resolve("src/test/java")) && this.defaultJavaVersion.major() == 25)
                || Files.exists(path.resolve("src/test/java25"));
        }
    }
}
