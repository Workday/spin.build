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
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.From;
import build.spin.annotation.System;
import build.spin.module.clean.CleanPlugin;
import build.spin.module.java.AbstractCompile;
import build.spin.module.java.AbstractDetectCompilationClassPath;
import build.spin.module.java.AbstractDetectSourceFiles;
import build.spin.module.java.AbstractDetectSourcePaths;
import build.spin.module.java.Java8CompilerPlugin;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
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
     * The {@link JDKVersion} for the {@link Plugin}.
     */
    private final JDKVersion javaVersion;

    /**
     * Constructs a {@link Java8JUnitPlugin}.
     */
    public Java8JUnitPlugin() {
        this.javaVersion = JDKVersion.of(8);
    }

    @Override
    public JDKVersion getJavaVersion() {
        return this.javaVersion;
    }

    /**
     * A {@link Task} to determine the source paths for compilation.
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
     * A {@link Task} to determine the source files for compilation.
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
     * A {@link Task} to detect the {@link ClassPath} suitable for <strong>compiling</strong> tests for the {@link Project}.
     */
    @Named("detect.test.compilation.classpath")
    public static class DetectCompilationClassPath
        extends AbstractDetectCompilationClassPath {

        @Inject
        private Project project;

        @Inject
        private BuildDirectoryName buildDirectoryName;

        @Inject
        private TargetDirectoryName target;

        @Override
        public ClassPath create() {
            // when this is a Java project, we include the main/classes and main/resources as part of the class path
            return this.project.getPlugin(Java8CompilerPlugin.class)
                .map(java -> ClassPath.of(
                    Stream.of(this.project.path()
                        .resolve(this.buildDirectoryName.get() + "/main/" + this.target.get())),
                    super.create().paths()))
                .orElse(super.create());
        }
    }

    /**
     * A {@link Task} to compile the source code in the {@link Project}.
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
         * Compiles the source code in the provided {@link PathSet} into the specified build {@link Path},
         * using the specified {@link ClassPath}.
         *
         * @param sourceCode the source code
         * @param classPath the {@link ClassPath}
         * @param buildPath the build {@link Path}
         *
         * @return the {@link PathSet} containing the compiled classes
         * @throws Exception should compilation fail
         */
        public PathSet compile(final @From(DetectSourceFiles.class) PathSet sourceCode,
                               final @From(DetectCompilationClassPath.class) ClassPath classPath,
                               final @From(CleanPlugin.CreateBuildPath.class) Path buildPath)
            throws Exception {

            // the path in which to place the compiled classes
            final Path targetPath = buildPath.resolve("test/" + this.target.get());

            return super.compile(sourceCode, classPath, buildPath, targetPath);
        }
    }

    @Named("resolve.test.runtime.classpath")
    public static class ResolveRuntimeClassPath
        implements Task<ClassPath> {

        public ClassPath resolve(final @From(Java8JUnitPlugin.Compile.class) PathSet pathSet,
                                 final @From(DetectCompilationClassPath.class) ClassPath classPath) {

            return ClassPath.of(classPath.stream(), pathSet.stream());
        }
    }

    /**
     * A {@link Task} to execute compiled tests in the {@link Project}.
     */
    @Named("test")
    public static class Test
        extends AbstractTest {

        public PathSet test(final @From(ResolveRuntimeClassPath.class) ClassPath classPath,
                            final @From(CleanPlugin.CreateBuildPath.class) Path buildPath) {

            return super.test(classPath, buildPath);
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
