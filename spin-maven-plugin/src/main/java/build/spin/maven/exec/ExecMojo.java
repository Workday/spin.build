package build.spin.maven.exec;

/*-
 * #%L
 * Spin Maven Plugin
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

import build.spin.module.modulesystem.ModuleGraphClassifier;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Forks a JVM with a properly classified module-path for self-hosting spin builds.
 *
 * <p>Collects jars from either the plugin's own class realm ({@code scope=plugin}) or from the
 * Maven project's compile / runtime / test classpath, runs them through
 * {@link ModuleGraphClassifier#classifyAndResolve} to split them into a curated
 * {@code --module-path} and {@code -cp}, then forks a child JVM via
 * {@code -m rootModule/mainClass}.
 *
 * <p>This replaces the {@code exec-maven-plugin} + {@code Launcher.java} pattern used for spin's
 * self-hosting build. {@code exec-maven-plugin}'s {@code <classpath/>} placeholder dumps every
 * dependency onto a flat classpath, which cannot handle split packages. This goal does the
 * classification that {@code exec-maven-plugin} cannot.
 */
@Mojo(
    name = "exec",
    defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true)
public class ExecMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor pluginDescriptor;

    /**
     * Fully-qualified main class name (e.g. {@code build.spin.application.Spin}).
     */
    @Parameter(property = "spin.exec.mainClass", required = true)
    private String mainClass;

    /**
     * Root JPMS module name (e.g. {@code build.spin.application}). Used both as the resolution
     * root for {@link ModuleGraphClassifier#classifyAndResolve} and as the module half of the
     * {@code -m rootModule/mainClass} argument.
     */
    @Parameter(property = "spin.exec.rootModule", required = true)
    private String rootModule;

    /**
     * Source of jar candidates: {@code compile}, {@code runtime}, {@code test}, or {@code plugin}.
     * Use {@code plugin} to classify the plugin's own class realm (for self-hosting builds where
     * the spin jars are loaded as plugin dependencies rather than project dependencies).
     */
    @Parameter(property = "spin.exec.scope", defaultValue = "runtime")
    private String scope;

    /**
     * Optional {@code -Xmx} value, e.g. {@code 512m}. Omitted when blank.
     */
    @Parameter(property = "spin.exec.maxHeap")
    private String maxHeap;

    /**
     * Extra JVM flags inserted before {@code --module-path} (e.g. {@code --enable-preview}).
     */
    @Parameter(property = "spin.exec.additionalJvmArgs")
    private List<String> additionalJvmArgs;

    /**
     * Arguments passed to {@code mainClass} after the {@code -m} flag.
     */
    @Parameter(property = "spin.exec.arguments")
    private List<String> arguments;

    /**
     * Working directory for the forked process. Defaults to {@code ${project.basedir}}.
     */
    @Parameter(property = "spin.exec.workingDirectory", defaultValue = "${project.basedir}", required = true)
    private File workingDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final List<Path> candidates;
        try {
            candidates = resolveCandidates();
        } catch (final DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to resolve candidates: " + e.getMessage(), e);
        }

        getLog().info("classifying " + candidates.size() + " candidates (scope=" + scope + ")");

        final ModuleGraphClassifier.Classification classification =
            ModuleGraphClassifier.classifyAndResolve(
                candidates,
                Set.of(rootModule),
                rootModule,
                ModuleLayer.boot().configuration(),
                ModuleFinder.of(),
                msg -> getLog().info("[classify] " + msg));

        getLog().info("module-path=" + classification.modulePath().size()
            + " class-path=" + classification.classPath().size());

        final List<String> command = buildCommand(classification);
        getLog().info("exec: " + String.join(" ", command));

        try {
            final int exitCode = new ProcessBuilder(command)
                .directory(workingDirectory)
                .inheritIO()
                .start()
                .waitFor();
            if (exitCode != 0) {
                throw new MojoFailureException(
                    rootModule + "/" + mainClass + " exited with code " + exitCode);
            }
        } catch (final IOException | InterruptedException e) {
            throw new MojoExecutionException("exec failed: " + e.getMessage(), e);
        }
    }

    private List<Path> resolveCandidates() throws DependencyResolutionRequiredException {
        return switch (scope) {
            case "compile" -> toPathList(project.getCompileClasspathElements());
            case "runtime" -> toPathList(project.getRuntimeClasspathElements());
            case "test" -> toPathList(project.getTestClasspathElements());
            case "plugin" -> pluginDescriptor.getArtifacts().stream()
                .map(Artifact::getFile)
                .filter(Objects::nonNull)
                .map(File::toPath)
                .toList();
            default -> throw new IllegalArgumentException("Unknown scope: " + scope
                + " (expected compile, runtime, test, or plugin)");
        };
    }

    private static List<Path> toPathList(final List<String> elements) {
        return elements.stream().map(Path::of).toList();
    }

    private List<String> buildCommand(final ModuleGraphClassifier.Classification classification) {
        final List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());

        if (maxHeap != null && !maxHeap.isBlank()) {
            command.add("-Xmx" + maxHeap);
        }

        if (additionalJvmArgs != null) {
            command.addAll(additionalJvmArgs);
        }

        if (!classification.modulePath().isEmpty()) {
            command.add("--module-path");
            command.add(joinPaths(classification.modulePath()));
        }

        if (!classification.classPath().isEmpty()) {
            command.add("-cp");
            command.add(joinPaths(classification.classPath()));
        }

        command.add("-m");
        command.add(rootModule + "/" + mainClass);

        if (arguments != null) {
            command.addAll(arguments);
        }

        return command;
    }

    private static String joinPaths(final List<Path> paths) {
        return paths.stream()
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
    }
}
