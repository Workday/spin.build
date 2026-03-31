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

import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.Application;
import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.application.option.Name;
import build.spawn.platform.local.LocalMachine;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.System;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleReference;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.stream.Collectors;
/**
 * An abstract {@link Task} to perform Java Linking using the Java Platform
 * <a href="https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html">jlink</a> tool
 * on the compiled and packaged {@link Artifact} for a {@link Project}.
 * <p>
 * The {@code script.ftl} file contains a <a href="https://freemarker.apache.org">Apache Freemarker</a>-based script
 * that will be used to create a unix-based script to execute the linked application.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public abstract class AbstractJavaLinker
    implements Task<Path> {

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private JavaPlatform platform;

    @Inject
    private LocalMachine machine;

    @Inject
    private Project project;

    @Inject
    private ModuleDescriptor descriptor;

    @Inject
    @System
    private JDKVersion systemJavaVersion;

    /**
     * Execute {@code jlink} on this {@link Project}
     *
     * @param buildPath the build path for the {@link Project}
     * @param analysis the {@link DependencyAnalysis} containing information for linking
     *
     * @return the {@link Path} the path of the {@code jlink} produced Java Runtime
     * @throws Exception should the {@link Task} execution fail
     */
    public Path jlink(final Path buildPath,
                      final DependencyAnalysis analysis)
        throws Exception {

        // establish the name of the package and script
        final var packageName = this.project.name();
        final var scriptName = packageName + ".sh";

        // establish the path in which to generate the jlink runtime package
        final var packagePath = buildPath.resolve(packageName);

        // ------
        // create a list of the Java Platform modules to link
        // (the rest are going on the ClassPath for now!)
        final var bootModuleNames = ModuleLayer.boot().modules().stream()
            .map(Module::getName)
            .collect(Collectors.toSet());

        final var moduleNames = analysis.platformModules()
            .map(ModuleReference::name)
            .filter(bootModuleNames::contains)  // only include modules that actually exist in this JDK
            .collect(Collectors.joining(","));

        // ------
        // create a configuration to run jlink
        final var jdk = this.platform.getVersion(this.systemJavaVersion.major())
            .or(this.platform::getLatest)
            .orElseThrow(() -> new RuntimeException("No JDK found for Java " + this.systemJavaVersion.major() + " and no latest JDK available on platform"));
        final var javaHome = jdk.home().path();
        final var jlinkPath = javaHome.resolve("bin/jlink");

        try (var jlink = this.machine.launch(Application.class,
            Executable.of(jlinkPath.toString()),
            Name.of("jlink"),
            Argument.of("--module-path"), Argument.of(analysis.modulePath()),
            Argument.of("--output"), Argument.of(packagePath),
            Argument.of("--add-modules"), Argument.of(moduleNames),
            Console.ofSystem())) {
            jlink.onExit().get();

            // copy modules into the package modules
            final var modulePath = packagePath.resolve("modules");
            Files.createDirectories(modulePath);

            // copy the modules into the linked module path (only they can be used with jlink)
            analysis.dependencies()
                .forEach(dependency -> dependency.artifactDescriptor().path()
                    .ifPresent(source -> {
                        try {
                            final var target = modulePath.resolve(source.getFileName());
                            Files.copy(source, target);
                        }
                        catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    }));

            // ---------
            // create the script to execute the application
            final var scriptPath = packagePath.resolve("bin");

            final var cfg = new Configuration(Configuration.VERSION_2_3_31);

            cfg.setDefaultEncoding("UTF-8");
            cfg.setClassForTemplateLoading(Java25CompilerPlugin.class, "/");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setLogTemplateExceptions(true);
            cfg.setWrapUncheckedExceptions(true);
            cfg.setFallbackOnNullLoopVariable(false);

            // establish the data model object for the template
            final var model = new HashMap<String, Object>();

            final var classPath = analysis.dependencies()
                .map(dependency -> dependency.artifactDescriptor().path().orElseThrow(() -> new IllegalStateException("No artifact path for dependency [" + dependency.artifactDescriptor().artifact() + "]")))
                .map(path -> "$CP/" + path.getFileName())
                .collect(Collectors.joining(":"));

            model.put("classpath", classPath);
            model.put("name", packageName);

            // include the version number (if present)
            this.descriptor.version()
                .ifPresent(version -> model.put("version", version.get()));

            // TODO: (one day... include all of the jlink configuration parameters from the configuration file)

            // acquire the Template
            final var template = cfg.getTemplate("script.ftl");

            // establish the output writer for the processed template
            try (var writer = Files.newBufferedWriter(scriptPath.resolve(scriptName))) {
                // process the Template (into the bin folder)
                template.process(model, writer);
            }

            // make the script executable
            scriptPath.resolve(scriptName).toFile().setExecutable(true);
        }

        return jlinkPath;
    }
}
