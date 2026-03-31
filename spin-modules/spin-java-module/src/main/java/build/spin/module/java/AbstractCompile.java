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

import build.base.foundation.Capture;
import build.base.foundation.Strings;
import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
import build.base.telemetry.Activity;
import build.base.telemetry.Meter;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.Application;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Name;
import build.spawn.application.option.StandardErrorSubscriber;
import build.spawn.jdk.JDK;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.JDKHome;
import build.spawn.platform.local.LocalMachine;
import build.spin.Invocable;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.Workspace;
import build.spin.annotation.System;
import build.spin.common.reactive.ConditionalConsumingObserver;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static build.spin.module.clean.CleanPlugin.delete;

/**
 * An abstract {@link Task} to compile Java Source Code using the Java Platform {@code javac} command.
 *
 * @author brian.oliver
 * @since Oct-2019
 */
public abstract class AbstractCompile
    implements Task<PathSet> {

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private LocalMachine machine;

    @Inject
    private Project project;

    @Inject
    private JDK javaDevelopmentKit;

    @Inject
    private ModuleDescriptor moduleDescriptor;

    @Inject
    private ModuleVersioning versioning;

    @Inject
    @System
    private JDKVersion systemJavaVersion;

    @Inject
    private JDKVersion javaVersion;

    @Override
    public Stream<Reference> dependencies() {
        final Workspace workspace = this.project.workspace();

        // locate projects with in the Workspace that this project requires
        // (and add if they are Java project, add a prerequisite on the appropriate
        //  JavaCompilerPlugin.Compiler task for the project)
        return this.moduleDescriptor.requires()
            .map(ModuleDescriptor.Requires::name)
            .flatMap(name -> workspace.stream()
                .map(prj -> {
                    // capture the JavaCompilerPlugin in the Project with the same or lower JDKVersion used by
                    // this JavaPlugin (we can't be dependent on a JDKVersion higher than that required by this JavaPlugin)
                    final Capture<JavaCompilerPlugin> capture = Capture.empty();

                    prj.plugins(JavaCompilerPlugin.class)
                        .forEach(plugin -> {
                            if ((capture.isPresent()
                                && plugin.getJavaVersion().compareTo(capture.get().getJavaVersion()) > 0)
                                || !capture.isPresent()) {

                                capture.set(plugin);
                            }
                        });

                    // TODO: if the module requires this module, we have a cycle!

                    // NOTE: using "endsWith" here is super important.
                    // It allows project names to match module names for automatic modules.
                    return capture
                        .filter(plugin -> name.endsWith(plugin.getModuleDescriptor().name())
                            || prj.name().equals(name))
                        .map(plugin ->
                            // locate the JavaCompilerPlugin.Compiler task for the CompilerPlugin
                            prj.invocables()
                                .filter(definition -> definition.getPlugin() == plugin
                                    && JavaCompilerPlugin.Compile.class.isAssignableFrom(definition.getTaskClass()))
                                .findFirst()
                                .map(Invocable::getTaskClass)
                                .map(taskClass -> Reference.of(prj, taskClass))
                                .orElse(null))
                        .orElse(null);
                })
                .filter(Objects::nonNull));
    }

    /**
     * Compiles the source code in the provided {@link PathSet} into the specified build {@link Path},
     * using the specified {@link ClassPath}.
     *
     * @param sourceCode the source code
     * @param classPath the {@link ClassPath}
     * @param buildPath the build {@link Path} (.build)
     * @param targetPath the path in which to place the compiled classes
     *
     * @return the {@link PathSet} containing the compiled classes
     * @throws Exception should compilation fail
     */
    protected PathSet compile(final PathSet sourceCode,
                              final ClassPath classPath,
                              final Path buildPath,
                              final Path targetPath)
        throws Exception {

        // compilation output location varies depending on whether the plugin is
        // using the system provided version of java
        final boolean isDefaultJavaVersion = this.javaVersion.major() == this.systemJavaVersion.major();

        // determine the version of the Module being compiled (or use the system provided version)
        final ModuleDescriptor.Version version = this.versioning
            .getVersion(this.moduleDescriptor)
            .orElse(ModuleDescriptor.Version.DEFAULT);

        final Activity compilation = this.recorder
            .commence("Compiling %d file(s) for [%s] as [%s] ", sourceCode.size(), this.project.path(), version);

        // determine the target location for the compiles classes based on the JDKVersion
        final Path target;

        if (isDefaultJavaVersion) {
            // when the system provided JDKVersion and the JDKVersion for the Plugin are the same,
            // we use the specified targetPath as the target
            target = targetPath;
        }
        else {
            // otherwise place the compiled classes in a target folder for the major version of java
            target = targetPath.resolve("../" + targetPath.getFileName() + "-" + this.javaVersion.major() + "/");
        }

        // create the target path for the compiled classes
        try {
            Files.createDirectories(target);
        }
        catch (final IOException e) {
            throw new RuntimeException("Failed to create compilation target [" + target + "]", e);
        }

        // determine the ClassPath for the compilation
        final PathSetBuilder builder = PathSetBuilder.create();
        builder.addAll(classPath.stream());

        // when this project isn't using the system version of java, include the default classes folder for the project
        // as these previously compiled classes may be required for this compilation
        if (this.javaVersion.major() != this.systemJavaVersion.major()) {
            builder.add(targetPath);
        }

        final ClassPath compilationClassPath = ClassPath.of(builder.build().stream());

        // create an "argument" file for "javac"
        // include the version number in the arguments file name
        // (so we can tell the arguments being used to compile with this plugin)
        final Path arguments = buildPath.resolve("arguments-" + this.javaVersion.major());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(arguments))) {
            // include the "javac" options

            // include the output path for compiled classes
            writer.println("-d " + Strings.doubleQuoteIfContainsWhiteSpace(target.toString()));

            // include -verbose (for debugging)
            writer.println("-verbose");

            // include the compilation classpath (iff it's defined)
            if (!compilationClassPath.isEmpty()) {
                this.recorder.diagnostic("Compilation ClassPath");
                compilationClassPath.stream()
                    .forEach(path -> this.recorder.diagnostic("Path [%s]", path));

                final String cp = compilationClassPath.stream()
                    .map(Path::toString)
                    .reduce("", (left, right) -> left.isEmpty() ? right : left + File.pathSeparator + right);

                writer.println("-classpath " + Strings.doubleQuoteIfContainsWhiteSpace(cp));
            }

            // lastly include the source code to compile
            sourceCode.stream()
                .peek(path -> this.recorder.diagnostic("Preparing [%s] for compilation", path))
                .forEach(writer::println);
        }

        // establish the "javac" executable based on the Java Development Kit
        final JDKHome javaHome = this.javaDevelopmentKit.home();
        final String executable = javaHome.path().resolve("bin/javac").toString();

        // establish the StandardOutputObserver to observe and translate "javac" verbose output into Telemetry
        final AtomicInteger parseCount = new AtomicInteger(0);
        final AtomicInteger checkingCount = new AtomicInteger(0);

        final Capture<Meter> compiling = Capture.empty();
        final Capture<Activity> parsing = Capture.empty();

        final String parsingPrefix = "[parsing started";
        final String compilingPrefix = "[checking ";

        // a Capture for the last error message
        final Capture<String> error = Capture.empty();

        final ConditionalConsumingObserver<String> observer = ConditionalConsumingObserver.Builder.<String>create()
            .with(string -> string.startsWith(parsingPrefix), __ -> {
                if (parseCount.getAndIncrement() == 0) {
                    parsing.set(this.recorder.commence("Parsing"));
                }
            })
            .with(string -> string.startsWith(compilingPrefix), string -> {
                if (checkingCount.getAndIncrement() == 0) {
                    parsing.ifPresent(Activity::complete);
                    compiling.set(this.recorder.commence(parseCount.get(), "Compiling"));
                }
                else {
                    compiling.ifPresent(meter ->
                        meter.progress("Compiling [%s]",
                            string.substring(parsingPrefix.length(), string.length() - 1)));
                }
            })
            .with(string -> string.startsWith("[total"), __ -> {
                parsing.ifPresent(Activity::complete);
                compiling.ifPresent(Meter::complete);
            })
            .with(string -> !string.startsWith("["), string -> {
                // capture the error
                if (error.isPresent()) {
                    error.set(error.get() + "\n" + string);
                }
                else {
                    error.set(string);
                }
            })
            .with(string -> string.startsWith("["), string -> {
                // record and clear the error
                error.ifPresent(e -> this.recorder.error(e));
                error.clear();
            })
            .build();

        // launch "javac"
        try (
            Application javac = this.machine.launch(executable,
                javaHome,
                Name.of("javac " + this.javaDevelopmentKit.version().toString()),
                Argument.of("@" + Strings.doubleQuoteIfContainsWhiteSpace(arguments.toString())),
                StandardErrorSubscriber.of(observer))) {

            // wait for "javac" to exit
            javac.onExit().get();

            // output the exit value for the completion
            javac.exitValue()
                .ifPresent(value -> {
                    if (value == 0) {
                        compilation.complete();
                    }
                    else {
                        final RuntimeException runtimeException =
                            new RuntimeException("Compilation Failed (exit code :" + value + ")");

                        compilation.completeExceptionally(runtimeException);

                        throw runtimeException;
                    }
                });

            if (javac.exitValue().isEmpty()) {
                compilation.complete();
            }
        }

        // move the compiled classes into the appropriate location based on the version of java used to compile them
        final Path path;

        if (isDefaultJavaVersion) {
            path = target;
        }
        else {
            // when this java version is not the system version, move the compiled classes into the appropriate
            // versions folder
            final Path versions = targetPath.resolve("META-INF/versions/");

            try {
                Files.createDirectories(versions);
            }
            catch (final IOException e) {
                throw new RuntimeException("Failed to create [" + versions + "]", e);
            }

            path = versions.resolve(this.javaVersion.major() + "/");

            // FUTURE: here we must remove the folder before the move, otherwise the move will fail
            // however this may disrupt conditional compilation in the future
            delete(path);

            Files.move(target, path, StandardCopyOption.REPLACE_EXISTING);
        }

        return PathSetBuilder.create(path).build();
    }
}
