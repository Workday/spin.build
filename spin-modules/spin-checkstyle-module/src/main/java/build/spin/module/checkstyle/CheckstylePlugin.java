package build.spin.module.checkstyle;

/*-
 * #%L
 * Spin Checkstyle Module
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

import build.base.configuration.Option;
import build.base.flow.RecordingSubscriber;
import build.base.io.PathSet;
import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Name;
import build.spawn.application.option.StandardOutputSubscriber;
import build.spawn.jdk.JDKApplication;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.MainClass;
import build.spawn.platform.local.LocalMachine;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.After;
import build.spin.annotation.Description;
import build.spin.annotation.From;
import build.spin.annotation.System;
import build.spin.common.ProcessFailedException;
import build.spin.module.java.AbstractDetectSourceFiles;
import build.spin.module.java.ErrorCapture;
import build.spin.module.java.JavaCompilerPlugin;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.CheckstyleArguments;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A {@link Plugin} defining <a href="https://checkstyle.org/">Checkstyle</a> {@link Task}s.
 *
 * @author brian.oliver
 * @since Oct-2019
 */
public class CheckstylePlugin
    implements Plugin {

    /**
     * A {@link Task} to perform <a href="https://checkstyle.org/">Checkstyle</a> analysis on a {@link Project}.
     */
    @Named("checkstyle")
    @Description("Run Checkstyle static analysis")
    @After(JavaCompilerPlugin.Compile.class)
    public static class Checkstyle
        implements Task {

        @Inject
        private TelemetryRecorder recorder;

        @Inject
        private Project project;

        @Inject
        private LocalMachine machine;

        @Inject
        private Artifact.Resolver resolver;

        @Inject
        @System
        private JDKVersion javaVersion;

        /**
         * Used only when the project declares no {@code maven-checkstyle-plugin} {@code
         * <dependencies>} of its own (see {@link CheckstyleArguments#additionalCheckArtifacts}) —
         * i.e. it isn't pinning a Checkstyle version itself.
         */
        private static final String DEFAULT_CHECKSTYLE_COORDINATE = "com.puppycrawl.tools:checkstyle:13.9.0";

        public void check(final @From(JavaCompilerPlugin.DetectSourceFiles.class) Stream<PathSet> sourceCode) {

            final List<String> declaredArtifacts = this.project.findResource(CheckstyleArguments.class)
                .map(args -> args.additionalCheckArtifacts(this.project).toList())
                .orElseGet(List::of);
            final boolean declaresCheckstyleCore = declaredArtifacts.stream()
                .anyMatch(coordinate -> coordinate.startsWith("com.puppycrawl.tools:checkstyle:"));

            // determine the paths to launch Checkstyle (resolve transitively for each top-level artifact)
            final LinkedHashSet<Path> checkstyleArtifactPaths = new LinkedHashSet<>();
            (declaresCheckstyleCore
                    ? declaredArtifacts.stream()
                    : Stream.concat(Stream.of(DEFAULT_CHECKSTYLE_COORDINATE), declaredArtifacts.stream()))
                .map(Artifact::parse)
                .flatMap(artifact -> this.resolver.resolveTransitive(artifact)
                    .map(List::stream)
                    .orElse(Stream.empty()))
                .forEach(checkstyleArtifactPaths::add);

            final Stream<Path> checkstyleArtifacts = checkstyleArtifactPaths.stream();

            // the checkstyle configuration: the project's own maven-checkstyle-plugin <configLocation>
            // if declared, otherwise the workspace-level "checkstyle/checkstyle.xml" convention
            final Path workspacePath = this.project.workspace().path();
            final Path configurationPath = this.project.findResource(CheckstyleArguments.class)
                .flatMap(args -> args.configurationPath(this.project))
                .orElseGet(() -> workspacePath.resolve("checkstyle/checkstyle.xml"));

            // create the Options to launch Checkstyle
            final List<Option> optionList = new ArrayList<>();
            optionList.add(Name.of("Checkstyle"));
            optionList.add(ClassPath.of(checkstyleArtifacts));
            optionList.add(MainClass.of("com.puppycrawl.tools.checkstyle.Main"));
            optionList.add(Argument.of("-c"));
            optionList.add(Argument.of(configurationPath.toString()));

            // src/test/java is out of scope for JavaCompilerPlugin.DetectSourceFiles (that task is
            // explicitly main-source-only), so it's only included here when the project's pom opts
            // in via <includeTestSourceDirectory> — same opt-in maven-checkstyle-plugin itself uses.
            final boolean includeTestSourceDirectory = this.project.findResource(CheckstyleArguments.class)
                .map(args -> args.includeTestSourceDirectory(this.project))
                .orElse(false);
            final Stream<PathSet> allSourceCode = includeTestSourceDirectory
                ? Stream.concat(sourceCode, Stream.of(testSourceFiles(this.project)))
                : sourceCode;

            // include the files in the source code
            final AtomicInteger sourceFiles = new AtomicInteger(0);
            allSourceCode.flatMap(PathSet::stream)
                .map(Path::toAbsolutePath)
                .peek(__ -> sourceFiles.incrementAndGet())
                .map(Path::toString)
                .map(Argument::of)
                .forEach(optionList::add);

            // perform check iff there's source files
            if (sourceFiles.get() > 0) {
                // Checkstyle's Main writes violations to stdout and reserves stderr for genuine
                // tool failures (e.g. a malformed configuration) — capture both so a failure can
                // report whichever one actually has content.
                final RecordingSubscriber<String> recordingObserver = new RecordingSubscriber<>();
                final ErrorCapture captured = new ErrorCapture();
                optionList.add(StandardOutputSubscriber.of(recordingObserver));
                optionList.add(captured.subscriber(this.recorder::error));

                try (JDKApplication checkstyle = this.machine.launch(JDKApplication.class,
                        optionList.toArray(new Option[0]))) {

                    try {
                        checkstyle.onExit().get();
                    }
                    catch (final Exception e) {
                        throw new ProcessFailedException("Checkstyle Execution Failed",
                            ErrorCapture.selectOutput(captured.output(), recordingObserver.items()), e);
                    }

                    final int exitValue = checkstyle.exitValue().orElse(0);
                    if (exitValue != 0) {
                        throw new ProcessFailedException(
                            "Checkstyle Failed (exit code: " + exitValue + ")",
                            ErrorCapture.selectOutput(captured.output(), recordingObserver.items()));
                    }

                    this.recorder.info("Checkstyle finished with exit code %d", exitValue);
                }
            }
        }

        /**
         * Walks {@code src/test/java} for {@code .java} files, mirroring the fixed relative path
         * {@code Java25JUnitPlugin.DetectSourcePaths}/{@code Java8JUnitPlugin.DetectSourcePaths}
         * use, without taking a task-graph dependency on the JUnit module (test frameworks other
         * than JUnit may be present, or none at all).
         */
        private static PathSet testSourceFiles(final Project project) {
            final Path testSourcePath = project.path().resolve("src/test/java");
            if (!Files.isDirectory(testSourcePath)) {
                return PathSet.empty();
            }
            return new AbstractDetectSourceFiles() { }.detect(PathSet.of(testSourcePath));
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link CheckstylePlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Override
        public boolean isDetectedIn(final Path path) {
            // for now we only detect checkstyle if the Workspace defines a "checkstyle" folder
            // which contains the checkstyle configuration
            return false;
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            if (project.plugins(JavaCompilerPlugin.class).findAny().isEmpty()) {
                return false;
            }
            final Path workspacePath = project.workspace().path();
            if (Files.exists(workspacePath.resolve("checkstyle"))) {
                return true;
            }
            // also detect a project-declared maven-checkstyle-plugin <configLocation>, so a pom
            // that configures Checkstyle its own way doesn't additionally need the workspace
            // "checkstyle" folder convention
            return project.findResource(CheckstyleArguments.class)
                .flatMap(args -> args.configurationPath(project))
                .filter(Files::exists)
                .isPresent();
        }
    }
}
