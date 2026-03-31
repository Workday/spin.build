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
import build.base.io.PathSet;
import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Name;
import build.spawn.jdk.JDKApplication;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.MainClass;
import build.spawn.platform.local.LocalMachine;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.After;
import build.spin.annotation.From;
import build.spin.annotation.System;
import build.spin.module.java.JavaCompilerPlugin;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.MissingModuleVersionException;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.module.modulesystem.UnresolvableArtifactException;
import build.spin.module.modulesystem.UnresolvableModuleDescriptorException;
import build.spin.module.modulesystem.UnresolvableModuleException;
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
        private ModuleCatalog catalog;

        @Inject
        private ModuleVersioning versioning;

        @Inject
        @System
        private JDKVersion javaVersion;

        public void check(final @From(JavaCompilerPlugin.DetectSourceFiles.class) Stream<PathSet> sourceCode) {

            // establish a ModuleDescriptor.Resolver to resolve ModuleDescriptors for Checkstyle dependencies
            final ModuleDescriptor.Resolver resolver = ModuleDescriptor.Resolver
                .create(this.resolver, this.catalog, this.versioning);

            // determine the artifacts to launch Checkstyle
            final LinkedHashSet<Artifact> artifacts = new LinkedHashSet<>();
            Stream.of(
                    // include the Maven coordinates for the top-level artifacts we require
                    "com.puppycrawl.tools:checkstyle:8.8",
                    "com.workday:checkstyle-checks:4.0.13")

                // create an Artifact representation
                .map(Artifact::parse)

                // include the Artifacts themselves to resolve
                .peek(artifacts::add)

                // resolve the ModuleDescriptor for the Artifact
                // (and include them in the catalog when found)
                .map(artifact -> this.resolver.getModuleDescriptor(artifact, this.catalog, this.versioning)
                    .peek(moduleDescriptor -> this.catalog.add(moduleDescriptor.name(), artifact))
                    .orElseThrow(() -> new UnresolvableModuleDescriptorException(artifact)))

                // obtain the dependencies for the ModuleDescriptor
                .flatMap(descriptor -> descriptor.dependencies(resolver).stream())

                // obtain the Artifacts for each of the ModuleDescriptors
                .map(descriptor -> descriptor.version()
                    .map(__ -> descriptor.reference())
                    .map(reference -> this.catalog.getArtifact(reference)
                        .orElseThrow(() -> new UnresolvableModuleException(reference)))
                    .orElseThrow(() -> new MissingModuleVersionException(descriptor.reference())))
                .forEach(artifacts::add);

            // resolve the Paths to the Artifacts
            final Stream<Path> checkstyleArtifacts = artifacts.stream()
                .map(artifact -> this.resolver.resolve(artifact)
                    .orElseThrow(() -> new UnresolvableArtifactException(artifact)));

            // the path containing the checkstyle configuration
            final Path workspacePath = this.project.workspace().path();
            final Path configurationPath = workspacePath.resolve("checkstyle/checkstyle.xml");

            // create the Options to launch Checkstyle
            final List<Option> optionList = new ArrayList<>();
            optionList.add(Name.of("Checkstyle"));
            optionList.add(ClassPath.of(checkstyleArtifacts));
            optionList.add(MainClass.of("com.puppycrawl.tools.checkstyle.Main"));
            optionList.add(Argument.of("-c"));
            optionList.add(Argument.of(configurationPath.toString()));
            // include the files in the source code
            final AtomicInteger sourceFiles = new AtomicInteger(0);
            sourceCode.flatMap(PathSet::stream)
                .map(Path::toAbsolutePath)
                .peek(__ -> sourceFiles.incrementAndGet())
                .map(Path::toString)
                .map(Argument::of)
                .forEach(optionList::add);

            // perform check iff there's source files
            if (sourceFiles.get() > 0) {
                try (JDKApplication checkstyle = this.machine.launch(JDKApplication.class,
                        optionList.toArray(new Option[0]))) {
                    checkstyle.onExit().get();

                    // output the exit value
                    checkstyle
                        .exitValue()
                        .ifPresent(value -> this.recorder.info("Checkstyle finished with exit code %d", value));
                }
                catch (final Exception e) {
                    this.recorder.error(e, "Failed to execute Checkstyle");

                    throw new RuntimeException("Checkstyle Failure", e);
                }
            }
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
            final Path workspacePath = project.workspace().path();
            return project.plugins(JavaCompilerPlugin.class).findAny().isPresent()
                && Files.exists(workspacePath.resolve("checkstyle"));
        }
    }
}
