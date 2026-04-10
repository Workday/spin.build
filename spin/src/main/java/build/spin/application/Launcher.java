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

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * jars with split packages (Maven Resolver 1.x ships {@code maven-artifact} and
 * {@code maven-repository-metadata}, both automatic modules, both exporting
 * {@code org.apache.maven.artifact.repository.metadata}). Dumping those onto {@code --module-path}
 * fails at boot with {@link ResolutionException}.
 *
 * <p>This launcher fixes that in the obvious way: on the classpath handed to it by Maven, it
 * uses {@link ModuleFinder} on the actual on-disk jars and {@link Configuration#resolve} to
 * compute the real module graph starting from {@value #ROOT_MODULE}. Split-package conflicts
 * are demoted to classpath where the JPMS uniqueness rule doesn't apply; automatic modules on
 * {@code --module-path} can still reach those classes via {@code ALL-UNNAMED}. Finally the JVM
 * is re-executed as a proper modular application via {@code -m}.
 *
 * <p>Scope: this class exists only to fix the Maven exec bridge. It is not used by spin's jlink
 * packaging (see {@code AbstractJavaLinker}) or its compile-time classpath detection (see
 * {@code AbstractDetectCompilationClassPath}).
 */
public final class Launcher {

    private static final String ROOT_MODULE = "build.spin.application";

    private static final String MAIN_CLASS = "build.spin.application.Spin";

    private Launcher() {}

    public static void main(final String[] args) throws Exception {
        final List<Path> allJars = expandClassPath(System.getProperty("java.class.path", ""));

        final Classification classification = classify(allJars);

        final List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());

        // Forward -D/-X/etc. arguments from the current JVM so the child sees the same
        // system properties and tuning flags (e.g. java.util.logging.manager overrides).
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());

        if (!classification.modulePath.isEmpty()) {
            command.add("--module-path");
            command.add(join(classification.modulePath));
        }

        if (!classification.classPath.isEmpty()) {
            command.add("-cp");
            command.add(join(classification.classPath));
        }

        command.add("-m");
        command.add(ROOT_MODULE + "/" + MAIN_CLASS);
        command.addAll(Arrays.asList(args));

        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }

    private record Classification(List<Path> modulePath, List<Path> classPath) {}

    /**
     * Classify jars into {@code --module-path} vs {@code -cp}.
     *
     * <p>Two passes: first, proactively demote any jars involved in a split-package conflict
     * (iteratively, since demoting one conflict can remove packages and reveal another);
     * second, resolve the module graph from the root and treat anything not in the resolved
     * configuration as classpath-only.
     */
    private static Classification classify(final List<Path> allJars) {
        final Set<Path> uniqueJars = new LinkedHashSet<>(allJars);
        final Set<Path> moduleCandidates = new LinkedHashSet<>(uniqueJars);

        while (true) {
            final Set<Path> conflicts = findSplitPackageConflicts(moduleCandidates);
            if (conflicts.isEmpty()) {
                break;
            }
            moduleCandidates.removeAll(conflicts);
        }

        final ModuleFinder finder = ModuleFinder.of(moduleCandidates.toArray(new Path[0]));
        final Configuration config;
        try {
            config = ModuleLayer.boot().configuration()
                .resolve(finder, ModuleFinder.of(), Set.of(ROOT_MODULE));
        }
        catch (final FindException | ResolutionException e) {
            throw new IllegalStateException("Unable to resolve JPMS module graph from root ["
                + ROOT_MODULE + "]: " + e.getMessage(), e);
        }

        final Set<Path> resolved = new LinkedHashSet<>();
        for (final ResolvedModule resolvedModule : config.modules()) {
            resolvedModule.reference().location()
                .map(Path::of)
                .ifPresent(resolved::add);
        }

        final List<Path> classPath = new ArrayList<>();
        for (final Path jar : uniqueJars) {
            if (!resolved.contains(jar)) {
                classPath.add(jar);
            }
        }

        return new Classification(new ArrayList<>(resolved), classPath);
    }

    /**
     * Scan the candidate set for packages owned by more than one module. Returns the set of
     * jars to demote — all parties to every conflict, except the root module, which is never
     * demoted.
     *
     * <p>Uses {@link java.lang.module.ModuleDescriptor#packages()}, which covers the JPMS
     * package-uniqueness rule regardless of whether the package is exported. Demoting both
     * sides of a conflict is safe: automatic modules on the module-path continue to reach
     * the demoted classes via {@code ALL-UNNAMED}.
     */
    private static Set<Path> findSplitPackageConflicts(final Set<Path> candidates) {
        final ModuleFinder finder = ModuleFinder.of(candidates.toArray(new Path[0]));
        final Map<String, List<ModuleReference>> packageOwners = new LinkedHashMap<>();
        for (final ModuleReference ref : finder.findAll()) {
            for (final String pkg : ref.descriptor().packages()) {
                packageOwners.computeIfAbsent(pkg, k -> new ArrayList<>()).add(ref);
            }
        }

        final Set<Path> demoted = new LinkedHashSet<>();
        for (final List<ModuleReference> owners : packageOwners.values()) {
            if (owners.size() < 2) {
                continue;
            }
            for (final ModuleReference ref : owners) {
                if (ROOT_MODULE.equals(ref.descriptor().name())) {
                    continue;
                }
                ref.location().map(Path::of).ifPresent(demoted::add);
            }
        }
        return demoted;
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
