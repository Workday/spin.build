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
import build.spin.annotation.Description;
import build.spin.annotation.From;
import build.spin.annotation.System;
import build.spin.common.task.AbstractDetectSourcePaths;
import build.spin.common.task.SourcePathKind;
import build.spin.module.clean.CleanPlugin;
import build.spin.module.java.AbstractCompile;
import build.spin.module.java.AbstractDetectResolution;
import build.spin.module.java.Java25CompilerPlugin;
import build.spin.module.modulesystem.CompilationResolution;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        extends AbstractDetectResolution {
    }

    /**
     * A {@link build.spin.Task} to compile the source code in the {@link Project}.
     */
    @Named("test-compile")
    @Description("Compile test sources")
    public static class Compile
        extends AbstractCompile
        implements JUnitPlugin.Compile {

        private static final String TARGET_PREFIX = SourcePathKind.TEST.outputPrefix().orElseThrow();

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
                               final @From(CleanPlugin.CreateBuildPath.class) Path buildPath,
                               final @From(Java25CompilerPlugin.Compile.class) Optional<PathSet> ownMainOutput)
            throws Exception {

            // the path in which to place the compiled classes
            final Path targetPath = buildPath.resolve(TARGET_PREFIX + this.target.get());

            final PathSet sourceCode = build.spin.common.task.DetectSourcePaths.filesOf(sourcePaths, SourcePathKind.TEST);

            // DetectTestResolution's own "this project's own main output" seed is best-effort and can
            // race Java25CompilerPlugin.Compile (see AbstractDetectResolution#create); dependencies()
            // below already force-orders this task after that Compile task, so merge its result in
            // directly here rather than trusting DetectTestResolution's (possibly stale) snapshot.
            // Must still route through the same module-path-vs-classpath decision as every other
            // resolution candidate -- base's own main output is frequently a named module, and a named
            // module placed on the classpath is invisible to JUnit's modular (-m) launch.
            final CompilationResolution effectiveResolution = ownMainOutput
                .map(output -> resolution.withAdditional(output, AbstractDetectResolution::isNamedModule))
                .orElse(resolution);

            return super.compile(sourceCode, effectiveResolution, buildPath, targetPath);
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

        public PathSet test(final @From(DetectTestResolution.class) CompilationResolution resolution,
                            final @From(CleanPlugin.CreateBuildPath.class) Path buildPath,
                            final @From(Compile.class) PathSet compiledTestClasses,
                            final @From(Java25CompilerPlugin.Compile.class) Optional<PathSet> ownMainOutput) {

            // see the identical comment on Compile#compile above -- this task's own dependencies()
            // already force-orders it after Java25CompilerPlugin.Compile (transitively, via Compile
            // above), so merge its result in directly rather than trusting DetectTestResolution's
            // (possibly stale) snapshot.
            final CompilationResolution effectiveResolution = ownMainOutput
                .map(output -> resolution.withAdditional(output, AbstractDetectResolution::isNamedModule))
                .orElse(resolution);

            return super.test(effectiveResolution, buildPath, compiledTestClasses);
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
            final String sourceRoot = SourcePathKind.TEST.sourceRoot().orElseThrow();
            return (Files.exists(path.resolve(sourceRoot + "java")) && this.defaultJavaVersion.major() == 25)
                || Files.exists(path.resolve(sourceRoot + "java25"));
        }
    }
}
