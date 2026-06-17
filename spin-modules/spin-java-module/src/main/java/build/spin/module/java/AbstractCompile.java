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
import build.base.version.Version;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spawn.application.Application;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Name;
import build.spawn.application.option.StandardErrorSubscriber;
import build.spawn.jdk.JDK;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.JDKHome;
import build.spawn.jdk.option.ModulePath;
import build.spawn.platform.local.LocalMachine;
import build.spin.Invocable;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.Workspace;
import build.spin.annotation.System;
import build.spin.common.ProcessFailedException;
import build.spin.common.reactive.ConditionalConsumingObserver;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.CompilationResolution;
import build.spin.module.modulesystem.CompilerArguments;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
    private JDKModuleDescriptor moduleDescriptor;

    @Inject
    private ModuleVersioning versioning;

    @Inject
    private ModuleCatalog catalog;

    @Inject
    private Artifact.Resolver resolver;

    @Inject
    @System
    private JDKVersion systemJavaVersion;

    @Inject
    private JDKVersion javaVersion;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    @Inject
    private TargetDirectoryName targetDirectoryName;

    private String buildProcessorModulePath() {
        return AnnotationProcessorPaths.build(
            this.moduleDescriptor, this.project,
            this.catalog, this.versioning, this.resolver,
            this.buildDirectoryName, this.targetDirectoryName);
    }

    @Override
    public Stream<Reference> dependencies() {
        final Workspace workspace = this.project.workspace();

        // locate projects with in the Workspace that this project requires
        // (and add if they are Java project, add a prerequisite on the appropriate
        //  JavaCompilerPlugin.Compiler task for the project)
        final Stream<Reference> requiresDeps = this.moduleDescriptor.requiresClauses()
            .map(r -> r.requiresModuleName().toString())
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
                        .filter(plugin -> name.endsWith(plugin.getModuleDescriptor().moduleName().toString())
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

        // also depend on any workspace annotation processor modules so they are compiled before us
        final Stream<Reference> processorDeps = AnnotationProcessorPaths
            .annotationProcessorProjects(this.moduleDescriptor, this.project)
            .flatMap(prj -> prj.plugins(JavaCompilerPlugin.class)
                .findFirst()
                .map(plugin -> prj.invocables()
                    .filter(definition -> definition.getPlugin() == plugin
                        && JavaCompilerPlugin.Compile.class.isAssignableFrom(definition.getTaskClass()))
                    .findFirst()
                    .map(Invocable::getTaskClass)
                    .map(taskClass -> Reference.of(prj, taskClass))
                    .stream())
                .orElse(Stream.empty()));

        return Stream.concat(requiresDeps, processorDeps);
    }

    /**
     * Compiles the source code in the provided {@link PathSet} into the specified build {@link Path}.
     *
     * @param sourceCode the source code
     * @param resolution the {@link CompilationResolution} (module-path and classpath)
     * @param buildPath  the build {@link Path} (.build)
     * @param targetPath the path in which to place the compiled classes
     * @return the {@link PathSet} containing the compiled classes
     * @throws Exception should compilation fail
     */
    protected PathSet compile(final PathSet sourceCode,
                              final CompilationResolution resolution,
                              final Path buildPath,
                              final Path targetPath)
        throws Exception {

        final ModulePath modulePath = ModulePath.of(resolution.modulePath().stream());
        final ClassPath classPath = ClassPath.of(resolution.classPath().stream());

        // compilation output location varies depending on whether the plugin is
        // using the system provided version of java
        final boolean isDefaultJavaVersion = this.javaVersion.major() == this.systemJavaVersion.major();

        // determine the version of the Module being compiled (or use the system provided version)
        final Version version = this.versioning
            .getVersion(this.moduleDescriptor.moduleName().toString())
            .orElse(ModuleVersioning.DEFAULT_VERSION);

        final Activity compilation = this.recorder
            .commence("Compiling %d file(s) for [%s] as [%s] ", sourceCode.size(), this.project.path(), version);

        // determine the target location for the compiles classes based on the JDKVersion
        final Path target;

        if (isDefaultJavaVersion) {
            // when the system provided JDKVersion and the JDKVersion for the Plugin are the same,
            // we use the specified targetPath as the target
            target = targetPath;
        } else {
            // otherwise place the compiled classes in a target folder for the major version of java
            target = targetPath.resolve("../" + targetPath.getFileName() + "-" + this.javaVersion.major() + "/");
        }

        // create the target path for the compiled classes
        try {
            Files.createDirectories(target);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create compilation target [" + target + "]", e);
        }

        // when this project isn't using the system version of java, augment the classpath
        // with the default classes folder so previously compiled classes are available
        final ClassPath compilationClassPath;
        if (this.javaVersion.major() != this.systemJavaVersion.major()) {
            final PathSetBuilder builder = PathSetBuilder.create();
            builder.addAll(classPath.stream());
            builder.add(targetPath);
            compilationClassPath = ClassPath.of(builder.build().stream());
        } else {
            compilationClassPath = classPath;
        }

        // create an "argument" file for "javac"
        // include the version number in the arguments file name
        // (so we can tell the arguments being used to compile with this plugin)
        final Path arguments = buildPath.resolve("arguments-" + this.javaVersion.major());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(arguments))) {
            // include the "javac" options

            // include the output path for compiled classes
            writer.println("-d " + Strings.doubleQuoteIfContainsWhiteSpace(target.toString()));

            // preserve method/constructor parameter names in bytecode for reflection
            writer.println("-parameters");

            // include -verbose (for debugging)
            writer.println("-verbose");

            // pin the release; --release and --enable-preview are Java 9+ only
            if (this.javaVersion.isModular()) {
                writer.println("--release " + this.javaVersion.major());
                writer.println("--enable-preview");
            } else {
                writer.println("-source " + this.javaVersion.major());
                writer.println("-target " + this.javaVersion.major());
            }

            // when the source set contains a module-info.java the compilation is named-module mode:
            // use the module path and classpath as classified by the detection tasks.
            // when there is no module-info.java the sources belong to the unnamed module and JPMS
            // split-package enforcement would reject packages that already exist in a named module
            // (e.g. test sources in build.base.foundation while base.foundation is on the module
            // path).  In that case collapse everything onto the classpath so the unnamed module can
            // see all dependencies without JPMS boundaries.
            final boolean hasModuleInfo = sourceCode.stream()
                .anyMatch(p -> "module-info.java".equals(p.getFileName().toString()));

            if (hasModuleInfo) {
                writer.println("--module-version " + version);
                if (!modulePath.isEmpty()) {
                    this.recorder.diagnostic("Module Path (%d entries)", modulePath.size());
                    writer.println("--module-path " + Strings.doubleQuoteIfContainsWhiteSpace(
                        modulePath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
                }
                if (!compilationClassPath.isEmpty()) {
                    this.recorder.diagnostic("Class Path (%d entries)", compilationClassPath.size());
                    writer.println("-classpath " + Strings.doubleQuoteIfContainsWhiteSpace(
                        compilationClassPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
                }
            } else {
                // unnamed-module sources: merge module path + classpath into one flat classpath
                final ClassPath flatClassPath = ClassPath.of(
                    Stream.concat(modulePath.stream(), compilationClassPath.stream()));
                if (!flatClassPath.isEmpty()) {
                    this.recorder.diagnostic("Class Path [unnamed-module] (%d entries)", flatClassPath.size());
                    writer.println("-classpath " + Strings.doubleQuoteIfContainsWhiteSpace(
                        flatClassPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
                }
            }

            // add annotation processor modules (+ their full transitive dep closure) to the processor path
            // --processor-module-path is Java 9+; Java 8 uses -processorpath
            final String processorModulePath = buildProcessorModulePath();
            if (!processorModulePath.isEmpty()) {
                final String processorFlag = this.javaVersion.isModular() ? "--processor-module-path" : "-processorpath";
                writer.println(processorFlag + " "
                    + Strings.doubleQuoteIfContainsWhiteSpace(processorModulePath));

                // direct generated source files to a predictable directory so javadoc can find them
                final Path generatedSources = buildPath.resolve("main/generated-sources");
                try {
                    Files.createDirectories(generatedSources);
                } catch (final IOException e) {
                    throw new RuntimeException("Failed to create generated-sources directory [" + generatedSources + "]", e);
                }
                writer.println("-s " + Strings.doubleQuoteIfContainsWhiteSpace(generatedSources.toString()));
            }

            // include any project-declared javac args (e.g. --release N, --enable-preview,
            // <compilerArgs> from maven-compiler-plugin). Resource is workspace-scoped and
            // resolves the per-project effective pom.
            this.project.findResource(CompilerArguments.class).ifPresent(args ->
                args.get(this.project).forEach(writer::println));

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
        final ErrorCapture captured = new ErrorCapture();

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
                } else {
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
                } else {
                    error.set(string);
                }
            })
            .with(string -> string.startsWith("["), string -> {
                flushError(error, captured);
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

            // flush any error lines that were not followed by a subsequent "[" line
            flushError(error, captured);

            // output the exit value for the completion
            javac.exitValue()
                .ifPresent(value -> {
                    if (value == 0) {
                        compilation.complete();
                    } else {
                        final ProcessFailedException exception =
                            new ProcessFailedException("Compilation Failed (exit code: " + value + ")",
                                captured.output());

                        compilation.completeExceptionally(exception);

                        throw exception;
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
        } else {
            // when this java version is not the system version, move the compiled classes into the appropriate
            // versions folder
            final Path versions = targetPath.resolve("META-INF/versions/");

            try {
                Files.createDirectories(versions);
            } catch (final IOException e) {
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

    private void flushError(final Capture<String> error, final ErrorCapture captured) {
        error.ifPresent(e -> {
            if (ErrorCapture.isJavacWarning(e)) {
                this.recorder.warn(e);
            } else {
                this.recorder.error(e);
                captured.append(e);
            }
        });
        error.clear();
    }

}
