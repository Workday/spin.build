package build.spin.application;

/*-
 * #%L
 * Spin Command Line Application
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

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Bootstrap that bridges {@code exec-maven-plugin}'s flat classpath into a proper modular JVM
 * launch for spin.
 *
 * <p>{@code exec-maven-plugin}'s {@code <classpath/>} placeholder produces a single flat classpath;
 * its {@code <modulepath/>} placeholder dumps every dependency onto {@code --module-path} without
 * any classification logic. Neither is correct for spin because spin's dependency closure contains
 * jars with split packages. This launcher expands the flat classpath (unwrapping any pathing-jar
 * produced by {@code longClasspath=true}) and delegates to
 * {@link ModuleGraphClassifier#classifyAndResolve} to compute the real module graph from
 * {@value #ROOT_MODULE}, then re-execs the JVM as a proper modular application via {@code -m}.
 *
 * <p>Scope: this class exists only to fix the Maven exec bridge. It is not used by spin's jlink
 * packaging (see {@code AbstractJavaLinker}) or its compile-time classpath detection (see
 * {@code AbstractDetectCompilationClassPath}). Those use the same classifier with different
 * parent-configuration / before-finder arguments.
 */
public final class Launcher {

    private static final String ROOT_MODULE = "build.spin.application";

    private static final String MAIN_CLASS = "build.spin.application.Spin";

    private Launcher() {}

    public static void main(final String[] args) throws Exception {
        final List<Path> allJars = expandClassPath(System.getProperty("java.class.path", ""));

        final ModuleGraphClassifier.Classification classification =
            ModuleGraphClassifier.classifyAndResolve(
                allJars,
                Set.of(ROOT_MODULE),
                ROOT_MODULE,
                ModuleLayer.boot().configuration(),
                ModuleFinder.of(),
                msg -> {});

        final List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());

        // Forward -D/-X/etc. arguments from the current JVM so the child sees the same
        // system properties and tuning flags (e.g. java.util.logging.manager overrides).
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());

        if (!classification.modulePath().isEmpty()) {
            command.add("--module-path");
            command.add(join(classification.modulePath()));
        }

        if (!classification.classPath().isEmpty()) {
            command.add("-cp");
            command.add(join(classification.classPath()));
        }

        command.add("-m");
        command.add(ROOT_MODULE + "/" + MAIN_CLASS);
        command.addAll(Arrays.asList(args));

        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }

    private static String join(final List<Path> paths) {
        return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * Expand a classpath string into the underlying jar paths, unwrapping any pathing-jar
     * produced by {@code exec-maven-plugin}'s {@code longClasspath=true} mode (a single wrapper
     * jar whose {@code Class-Path} manifest attribute lists the real jars as space-separated
     * relative URIs).
     */
    private static List<Path> expandClassPath(final String classPath) throws IOException {
        final List<Path> result = new ArrayList<>();
        for (final String entry : classPath.split(File.pathSeparator)) {
            final String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final Path path = Path.of(trimmed);
            if (path.toString().endsWith(".jar") && path.toFile().exists()) {
                try (var jar = new JarFile(path.toFile())) {
                    final var manifest = jar.getManifest();
                    if (manifest != null) {
                        final var manifestCp = manifest.getMainAttributes().getValue("Class-Path");
                        if (manifestCp != null) {
                            final Path base = path.toAbsolutePath().getParent();
                            for (final String cpEntry : manifestCp.split("\\s+")) {
                                if (cpEntry.isBlank()) {
                                    continue;
                                }
                                final Path resolved = base != null
                                    ? base.resolve(Path.of(URI.create(cpEntry)))
                                    : Path.of(URI.create(cpEntry));
                                result.add(resolved.normalize());
                            }
                            continue;
                        }
                    }
                }
            }
            result.add(path);
        }
        return result;
    }
}
