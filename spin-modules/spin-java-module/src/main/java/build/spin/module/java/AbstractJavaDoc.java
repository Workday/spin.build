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

import build.base.foundation.Strings;
import build.base.io.PathSet;
import build.base.option.JDKVersion;
import build.base.telemetry.Activity;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.Application;
import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Name;
import build.spawn.jdk.JDK;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.JDKHome;
import build.spawn.jdk.option.ModulePath;
import build.spawn.platform.local.LocalMachine;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.System;
import build.spin.module.configuration.Configuration;
import build.spin.module.configuration.Source;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.Verbose;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
/**
 * An abstract {@link Task} that compiles and produces Java Documentation for the Java Source Code in a {@link Project}
 * using the Java Platform {@code javadoc} command.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public abstract class AbstractJavaDoc
    implements JavaCompilerPlugin.JavaDoc {

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
    private JDKVersion defaultJavaVersion;

    @Inject
    private JDKVersion javaVersion;

    @Inject
    private Verbose verbose;

    @Inject
    @Configuration
    @Source("build.spin.module.javadoc")
    private Optional<Properties> properties;

    /**
     * Compiles and produces Java Documentation from the source code in the provided {@link PathSet}, outputting it
     * into the specified build {@link Path}.
     *
     * @param sourceCode the source code
     * @param modulePath the {@link ModulePath} (empty for non-modular projects)
     * @param classPath the {@link ClassPath}
     * @param buildPath the build {@link Path} (.build)
     * @param javaPlatformURL the {@link Optional} {@link URL} for the external Java Development Kit documentation
     * @return the {@link Path} containing the generated documentation
     * @throws Exception should documentation fail
     */
    protected Path javadoc(final PathSet sourceCode,
                           final ModulePath modulePath,
                           final ClassPath classPath,
                           final Path buildPath,
                           final Optional<URL> javaPlatformURL)
        throws Exception {

        // the path in which to place the javadoc
        final Path targetPath = buildPath.resolve("main/javadoc");

        // only generate Java Documentation when the Project default JDKVersion
        // is the same as the JDKVersion for the Java Plugin that defines this JavaDoc Task
        // (we can't generate for lower or higher versions as the javadoc tool is not reliable when
        //  attempting to generate javadoc across different tools)
        if (this.defaultJavaVersion.major() == this.javaVersion.major()) {
            // determine the version of the Module being documented (or use a default version)
            final ModuleDescriptor.Version version = this.versioning
                .getVersion(this.moduleDescriptor)
                .orElse(ModuleDescriptor.Version.DEFAULT);

            final Activity documentation = this.recorder
                .commence("Generating Documentation %d file(s) for [%s]", sourceCode.size(), this.project.path());

            // create the target path for the compiled classes
            try {
                Files.createDirectories(targetPath);
            }
            catch (final IOException e) {
                documentation.completeExceptionally(e);
                throw new RuntimeException("Failed to create documentation target [" + targetPath + "]", e);
            }

            // create an "argument" file for "javadoc"
            // include the version number in the arguments file name
            // (so we can tell the arguments being used to compile with this plugin)
            final Path arguments = buildPath.resolve("arguments-javadoc");
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(arguments))) {
                // include the "javadoc" options

                // include the output path for documentation
                writer.println("-d " + Strings.doubleQuoteIfContainsWhiteSpace(targetPath.toString()));

                // include -verbose (for debugging)
                if (this.verbose == Verbose.ENABLED) {
                    writer.println("-verbose");
                }
                else {
                    writer.println("-quiet");
                }

                // include the module-path and classpath from the detection tasks
                if (!modulePath.isEmpty()) {
                    final String mp = modulePath.stream()
                        .map(Path::toString)
                        .reduce("", (l, r) -> l.isEmpty() ? r : l + File.pathSeparator + r);
                    writer.println("--module-path " + Strings.doubleQuoteIfContainsWhiteSpace(mp));
                }
                if (!classPath.isEmpty()) {
                    final String cp = classPath.stream()
                        .map(Path::toString)
                        .reduce("", (l, r) -> l.isEmpty() ? r : l + File.pathSeparator + r);
                    writer.println("-classpath " + Strings.doubleQuoteIfContainsWhiteSpace(cp));
                }

                // include the -link(s) to external documentation
                // TODO: we should get this from an external "catalog"

                // output the version of Java we're using (only if the URL is reachable)
                // connect() only establishes TCP; we must read from the stream to confirm
                // HTTP-level reachability before passing the URL to javadoc
                javaPlatformURL.ifPresent(url -> {
                    try {
                        final var connection = url.openConnection();
                        connection.setConnectTimeout(3000);
                        connection.setReadTimeout(3000);
                        try (var stream = connection.getInputStream()) {
                            stream.read();
                        }
                        writer.println("-link " + url);
                    }
                    catch (final java.io.IOException e) {
                        this.recorder.diagnostic("Skipping external documentation link for [%s]: %s", url, e.getMessage());
                    }
                });

                // output links to each of the "external" projects
                // TODO: https://javadoc.io/doc/<groupId>/<artifactId>/<version>

                // lastly include the source code to compile
                sourceCode.stream()
                    .peek(path -> this.recorder.diagnostic("Preparing [%s] for documentation", path))
                    .forEach(writer::println);
            }

            // establish the "javadoc" executable based on the Java Development Kit
            final JDKHome javaHome = this.javaDevelopmentKit.home();
            final String executable = javaHome.path().resolve("bin/javadoc").toString();

            // launch "javadoc"
            try (Application javadoc = this.machine.launch(executable,
                javaHome,
                Name.of("javadoc " + this.javaDevelopmentKit.version().toString()),
                Argument.of("@" + Strings.doubleQuoteIfContainsWhiteSpace(arguments.toString())),
                Console.ofSystem())) {

                // wait for "javadoc" to exit
                javadoc.onExit().get();

                // output the exit value for the completion
                javadoc.exitValue()
                    .ifPresent(value -> {
                        if (value == 0) {
                            documentation.complete();
                        }
                        else {
                            final RuntimeException runtimeException =
                                new RuntimeException("Documentation Generation Failed (exit code :" + value + ")");

                            documentation.completeExceptionally(runtimeException);

                            throw runtimeException;
                        }
                    });

                if (!javadoc.exitValue().isPresent()) {
                    documentation.complete();
                }
            }
        }

        return targetPath;
    }
}
