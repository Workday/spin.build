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

import build.base.foundation.Exceptional;
import build.base.foundation.Introspection;
import build.base.foundation.Strings;
import build.base.foundation.stream.Streams;
import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.base.version.Version;
import build.spawn.application.Application;
import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Name;
import build.spawn.jdk.JDK;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.JDKHome;
import build.spawn.platform.local.LocalMachine;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.common.util.Invocables;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Plugin} supporting the dynamic compilation and execution of custom Java-based {@link Task}s
 * for a {@link Project}.
 *
 * @author brian.oliver
 * @since Apr-2020
 */
public class CustomizationPlugin
    implements Plugin {

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private Project project;

    @Inject
    private Path path;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    @Inject
    private LocalMachine machine;

    @Inject
    private Artifact.Resolver resolver;

    @Inject
    private ModuleCatalog catalog;

    @Inject
    private JDKVersion javaVersion;

    @Inject
    private TargetDirectoryName target;

    @Inject
    private ModuleVersioning versioning;

    /**
     * The custom created {@link Invocable}s.
     */
    private final AtomicReference<ArrayList<Invocable<?>>> taskDefinitions;

    /**
     * Constructs a {@link CustomizationPlugin} {@link Plugin}.
     */
    public CustomizationPlugin() {
        this.taskDefinitions = new AtomicReference<>();
    }

    /**
     * Constructs a {@link PathSetBuilder} containing the resolved dependencies required for the compilation and
     * use of the Java-based customized {@link Task}s.
     *
     * @param stream the {@link Stream} of initial {@link ModuleDescriptor.Requires} declarations
     * @return the {@link PathSetBuilder}
     */
    public PathSetBuilder getDependencies(final Stream<ModuleDescriptor.Requires> stream) {

        final PathSetBuilder builder = PathSetBuilder.create();

        // the modules that have been visited (for transitive inclusion)
        final HashSet<String> includedModules = new HashSet<>();

        // establish a stack of modules to resolve and include in the ClassPath
        final Stack<ModuleDescriptor.Requires> stack = new Stack<>();
        Streams.reverse(stream).forEach(stack::push);

        while (!stack.isEmpty()) {

            // obtain the next required module to resolve
            final ModuleDescriptor.Requires requires = stack.pop();

            // attempt to find the required module within the root project
            final Optional<Project> dependency = this.project.workspace()
                .stream()
                .filter(prj ->
                        prj.getPlugin(Java25CompilerPlugin.class)
                            .map(java -> {
                                // include the required modules of the required module for processing when it is
                                // "transitive" and we've not already included them

                                // obtain the ModuleDescriptor for the project
                                final ModuleDescriptor moduleDescriptor = java.getModuleDescriptor();

                                // TODO: if the module descriptor is this descriptor, we've got a cycle

                                // include the project iff is the required module
                                if (requires.name().equals(moduleDescriptor.name())) {

                                    if (requires.isTransitive()) {

                                        if (!includedModules.contains(requires.name())) {

                                            // place the required module back in the queue
                                            // (as the transitive requires must occur before the required module)
                                            stack.push(requires);

                                            // include all of the required modules of the required module
                                            // (if not already processed)
                                            Streams.reverse(moduleDescriptor.requires())
                                                .filter(r -> !includedModules.contains(r.name()))
                                                .forEach(stack::push);

                                            // we've now included the transitive dependencies
                                            includedModules.add(requires.name());

                                            return false;
                                        }
                                        else {
                                            return true;
                                        }
                                    }
                                    else {
                                        return true;
                                    }
                                }
                                else {
                                    return false;
                                }
                            })
                            .orElse(false)
                       ).findFirst();

            if (dependency.isPresent()) {
                // include the path to the dependency classes and resources
                final Path dependencyPath = dependency.get().path();

                builder.add(dependencyPath.resolve(this.buildDirectoryName.get() + "/main/" + this.target.get()));
            }
            else if (!includedModules.contains(requires.name())) {

                // determine the Version of the Module to be used
                final Version moduleVersion = requires.version()
                    .orElseGet(() -> this.versioning.getVersion(requires.name())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "The version of module [" + requires.name()
                                + " is not defined in Versioning (version.properties)")));

                // use the Catalog to locate the Artifact
                final Optional<Artifact> optional = this.catalog.getArtifact(requires.reference());

                if (optional.isPresent()) {
                    final Artifact artifact = optional.get();

                    this.recorder.info("Discovered External Dependency [%s] to be resolved", artifact);

                    // and then the Resolver to resolve the path to it
                    this.resolver.resolve(artifact)
                        .ifPresent(p -> {

                            this.recorder.info("External Dependency [%s] resolved to [%s]", artifact, p);

                            // include the path to the artifact
                            builder.add(p);

                            // attempt to resolve the ModuleDescriptor for the external dependency
                            final Exceptional<ModuleDescriptor> optionalDescriptor =
                                this.resolver.getModuleDescriptor(artifact, this.catalog, this.versioning);

                            // include the transitive dependencies of the external dependency
                            // TODO: if the ModuleDescriptor requires this module, we have a cycle!
                            optionalDescriptor.ifPresent(descriptor ->
                                Streams.reverse(descriptor.requires())
                                    .filter(ModuleDescriptor.Requires::isTransitive)
                                    .filter(r -> !includedModules.contains(r.name()))
                                    .peek(r -> this.recorder
                                        .info("Including transitive dependency [%s] for [%s]", r.name(), artifact))
                                    .forEach(stack::push));
                        }).orElseThrow(() -> new IllegalStateException("Failed to resolve artifact [" + artifact + "]"));
                }
                else {
                    this.recorder.warn(
                        "Failed to locate [%s] in Module Catalog.  It won't be included in the classpath",
                        requires.name());
                }
            }
        }

        return builder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stream<Invocable<?>> invocables() {
        // only compile the customizations once, regardless of how many times we're asked for the Task.Definitions
        return this.taskDefinitions.updateAndGet(existing -> {
            if (existing == null) {

                // detect the source code to compile
                final PathSetBuilder builder = PathSetBuilder.create();

                final Path sourceCodePath = this.path.resolve("src/build/java");
                if (Files.exists(sourceCodePath)) {
                    try {
                        Files.walk(sourceCodePath)
                            .sorted(Comparator.reverseOrder())
                            .filter(p -> p.getFileName().toString().endsWith(".java")
                                && !p.getFileName().toString().equals("module-info.java"))
                            .forEach(builder::add);
                    }
                    catch (final IOException e) {
                        throw new RuntimeException(
                            "Failed to walk the Build Customization Source Code at " + sourceCodePath, e);
                    }
                }

                // include the Build.java in the project root
                final Path buildJavaPath = this.path.resolve("Build.java");
                if (Files.exists(buildJavaPath)) {
                    builder.add(buildJavaPath);
                }

                final PathSet sourceCode = builder.build();

                // establish the build output Path
                final Path buildPath = this.path.resolve(this.buildDirectoryName.get()).resolve("build/");
                try {
                    Files.createDirectories(buildPath);
                }
                catch (final IOException e) {
                    throw new RuntimeException("Failed to create 'build' directory [" + buildPath + "]", e);
                }

                // establish the target output path
                final Path target = buildPath.resolve(this.target.get() + "/");
                try {
                    Files.createDirectories(target);
                }
                catch (final IOException e) {
                    throw new RuntimeException("Failed to create 'target' directory [" + target + "]", e);
                }

                // create the "sources" file for "javac"
                final Path sources = buildPath.resolve("sources");
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(sources))) {
                    sourceCode.stream()
                        .peek(p -> this.recorder.diagnostic("Preparing customization [%s] for compilation", p))
                        .forEach(writer::println);
                }
                catch (final IOException e) {
                    throw new RuntimeException("Failed to create 'source' configuration to compile customizations", e);
                }

                // establish the ModuleDescriptor for the custom build
                final ModuleDescriptor moduleDescriptor;
                final Path moduleInfoPath = sourceCodePath.resolve("module-info.java");

                if (Files.exists(moduleInfoPath)) {

                    try (BufferedReader reader = Files.newBufferedReader(moduleInfoPath)) {
                        // the ModuleDescriptor contains the prerequisites
                        moduleDescriptor = ModuleDescriptor.parse(reader).build();
                    }
                    catch (final IOException e) {
                        throw new RuntimeException("Failed to read [" + moduleInfoPath + "]", e);
                    }
                }
                else {
                    // establish a default ModuleDescriptor
                    moduleDescriptor = ModuleDescriptor.Builder.create("build")
                        .noLocation()
                        .requires("build.spin.engine", EnumSet.of(ModuleDescriptor.Requires.Modifier.TRANSITIVE),
                            null)
                        .build();
                }

                // resolve the ClassPath from the ModuleDescriptor Requires
                //  (we don't need the entire ModuleDescriptor)
                final PathSetBuilder pathSetBuilder = getDependencies(moduleDescriptor.requires()
                    .filter(requires -> !requires.name().equals("build.spin.engine")));

                // include the current ClassPath (so we can inherit Spin)
                // (in the future, when this is a pre-packaged application, we'll only be able to use real dependencies)
                final String javaClassPath = System.getProperty("java.class.path");
                pathSetBuilder.add(javaClassPath);

                // remove the JRE and IDE dependencies!
                pathSetBuilder.removeIf(p -> {
                    final String string = p.toString();
                    return string.contains("jre") || string.contains("idea");
                });

                final ClassPath classPath = ClassPath.of(pathSetBuilder.build().stream());

                this.recorder.diagnostic("Discovered Customization ClassPath");
                classPath.stream()
                    .forEach(p -> this.recorder.diagnostic("Path [%s]", p));

                // create the "options" file for "javac"
                final Path options = buildPath.resolve("options");
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(options))) {

                    // include the ClassPath
                    if (!classPath.isEmpty()) {
                        final String cp = classPath.stream()
                            .map(Path::toString)
                            .reduce("", (left, right) -> left.isEmpty() ? right : left + File.pathSeparator + right);

                        writer.println("-classpath " + Strings.doubleQuoteIfContainsWhiteSpace(cp));
                    }
                }
                catch (final IOException e) {
                    throw new RuntimeException("Failed to create 'options' configuration to compile customizations", e);
                }

                // detect the JDK to use for compilation
                final JDK javaDevelopmentKit = JDK.current();

                // establish the "javac" executable based on the Java Development Kit
                final JDKHome javaHome = javaDevelopmentKit.home();
                final String executable = javaHome.path().resolve("bin/javac").toString();

                // launch "javac"
                try (Application javac = this.machine.launch(executable,
                    javaHome,
                    Name.of("javac " + javaDevelopmentKit.version().toString()),
                    Argument.of("@" + Strings.doubleQuoteIfContainsWhiteSpace(options.toString())),
                    Argument.of("@" + Strings.doubleQuoteIfContainsWhiteSpace(sources.toString())),
                    Argument.of("-g"), // always compile with debugging information
                    Argument.of("-d"),
                    Argument.of(Strings.doubleQuoteIfContainsWhiteSpace(target.toString())),
                    Console.ofSystem())) {

                    // wait for "javac" to exit
                    try {
                        javac.onExit().get();

                        // output the exit value for the completion
                        javac.exitValue()
                            .ifPresent(value -> {
                                this.recorder.info("Java customization compilation finished with exit code %d",
                                    value);

                                if (value != 0) {
                                    throw new RuntimeException(
                                        "Customization Compilation Failed (exit code :" + value + ")");
                                }
                            });
                    }
                    catch (final InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Failed to execute customization compilation", e);
                    }
                }

                // establish a custom ClassLoader to load the compiled customizations
                final URLClassLoader classLoader;

                final PathSetBuilder urlsBuilder = PathSetBuilder.create();

                // include the Paths on the ClassPath that aren't on the System Class Path
                urlsBuilder.addAll(classPath.stream()
                    .filter(p -> !javaClassPath.contains(p.getFileName().toString())));

                // include the compiled classes
                urlsBuilder.add(target);

                final URL[] urls = urlsBuilder.build().stream()
                    .map(p -> {
                        try {
                            return p.toUri().toURL();
                        }
                        catch (final MalformedURLException e) {
                            throw new RuntimeException("Failed to create custom URLClassLoader for [" + p + "]", e);
                        }
                    })
                    .toArray(URL[]::new);

                classLoader = new URLClassLoader(urls);

                // obtain the Build class using the custom ClassLoader
                final Class<?> buildClass;
                try {
                    buildClass = classLoader.loadClass("Build");
                }
                catch (final ClassNotFoundException e) {
                    throw new RuntimeException("Failed to load Build.class for customizations", e);
                }

                // create Task.Definitions defined by the Build class
                return Introspection.getAll(buildClass, Class::getDeclaredClasses)
                    .filter(c -> Modifier.isPublic(c.getModifiers()))
                    .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                    .filter(c -> Modifier.isStatic(c.getModifiers()))
                    .filter(Task.class::isAssignableFrom)
                    .map(c -> (Class<Task<Object>>) c)
                    .map(c -> Invocables.create(this.project, this, c))
                    .collect(Collectors.toCollection(ArrayList::new));
            }

            return existing;
        }).stream();
    }

    /**
     * The {@link Plugin.MetaClass} for {@link CustomizationPlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Override
        public boolean isDetectedIn(final Path path) {
            final boolean containsBuildModule = Files.exists(path.resolve("src/build/java/Build.java"));
            final boolean containsBuildPath = Files.exists(path.resolve("Build.java"))
                && !path.resolve("Build.java").toAbsolutePath().toString().endsWith("src/build/java/Build.java");

            return containsBuildModule || containsBuildPath;
        }
    }
}
