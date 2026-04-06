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
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.jar.JarFile;
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

        // determine if this is a modular project (has a module-info.java in the source set)
        final boolean isModular = sourceCode.stream()
            .anyMatch(path -> path.getFileName().toString().equals("module-info.java"));

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

            if (!compilationClassPath.isEmpty()) {
                if (isModular) {
                    final var classification = classifyJars(compilationClassPath.stream().toList());
                    if (!classification.namedModules().isEmpty()) {
                        this.recorder.diagnostic("Module Path (%d jars)", classification.namedModules().size());
                        final String mp = classification.namedModules().stream()
                            .map(Path::toString)
                            .reduce("", (l, r) -> l.isEmpty() ? r : l + File.pathSeparator + r);
                        writer.println("--module-path " + Strings.doubleQuoteIfContainsWhiteSpace(mp));
                    }
                    if (!classification.unnamedJars().isEmpty()) {
                        this.recorder.diagnostic("Class Path (%d unnamed jars)", classification.unnamedJars().size());
                        final String cp = classification.unnamedJars().stream()
                            .map(Path::toString)
                            .reduce("", (l, r) -> l.isEmpty() ? r : l + File.pathSeparator + r);
                        writer.println("-classpath " + Strings.doubleQuoteIfContainsWhiteSpace(cp));
                    }
                }
                else {
                    // non-modular project: legacy -classpath behaviour
                    this.recorder.diagnostic("Compilation ClassPath");
                    final String cp = compilationClassPath.stream()
                        .map(Path::toString)
                        .reduce("", (left, right) -> left.isEmpty() ? right : left + File.pathSeparator + right);
                    writer.println("-classpath " + Strings.doubleQuoteIfContainsWhiteSpace(cp));
                }
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

    /**
     * The result of {@link #classifyJars(List)}: the jars that belong on {@code --module-path}
     * and those that must go to {@code -classpath}.
     */
    public record JarClassification(List<Path> namedModules, List<Path> unnamedJars) {}

    /**
     * The result of {@link #resolveConflicts}: jars that must be excluded from
     * {@code --module-path} due to split-package issues.
     *
     * @param superseded older version duplicates and subset jars — route to classpath at compile
     *                   time or discard entirely for jdeps; the newer/superset jar stays on the
     *                   module path
     * @param demoted    true split-package conflicts where at least one jar is a proper module —
     *                   the non-proper module(s) move to classpath in all contexts
     */
    public record ConflictResolution(Set<Path> superseded, Set<Path> demoted) {}

    /**
     * Classifies a flat list of jars into named-module jars (for {@code --module-path}) and
     * unnamed jars (for {@code -classpath}), delegating conflict resolution to
     * {@link #resolveConflicts}.
     *
     * @param classpath all dependency jars to classify
     * @return a {@link JarClassification} holding the two lists
     */
    public static JarClassification classifyJars(final List<Path> classpath) {
        final var resolution = resolveConflicts(classpath, msg -> {});
        final List<Path> namedModules = new ArrayList<>();
        final List<Path> unnamedJars = new ArrayList<>();
        for (final Path jar : classpath) {
            if (resolution.superseded().contains(jar) || resolution.demoted().contains(jar)) {
                unnamedJars.add(jar);
            }
            else {
                // Jars without module-info.class or Automatic-Module-Name are promoted to
                // --module-path so the JPMS module finder can discover them by filename
                // (filename-derived automatic modules). Without this promotion, module-info.java
                // directives like "requires undertow.core" would fail with "module not found".
                namedModules.add(jar);
            }
        }
        return new JarClassification(namedModules, unnamedJars);
    }

    /**
     * Analyses a list of jars/directories for JPMS split-package conflicts and returns which
     * paths should be excluded from {@code --module-path}. The algorithm:
     * <ol>
     *   <li>Scans each path for its packages via {@link #getPackages}.</li>
     *   <li>Detects version duplicates (same base name) — keeps newest, supersedes rest.</li>
     *   <li>Detects subset jars (A's packages ⊆ B's) — supersedes the subset.</li>
     *   <li>Detects true split packages — demotes non-proper modules to classpath.</li>
     * </ol>
     *
     * <p>This algorithm replaces a previous list of hardcoded exclusions. Known cases it handles:
     * <ul>
     *   <li>{@code commons-logging} vs {@code jcl-over-slf4j} / AMN {@code org.apache.commons.logging}:
     *       the proper named module supersedes the old unnamed jar (version duplicate or superset).</li>
     *   <li>{@code jboss-logmanager} leaking {@code org.wildfly.common.*} packages owned by
     *       {@code wildfly-common}: wildfly-common is a proper module, so jboss-logmanager is demoted.</li>
     *   <li>{@code failureaccess} re-exporting {@code com.google.common.util.concurrent.internal}
     *       owned by guava: guava (proper module) wins, failureaccess (subset) is superseded.</li>
     *   <li>{@code slf4j.api} / {@code slf4j.simple} co-owning {@code org.slf4j}: handled as a
     *       true split if both land in the same candidate list.</li>
     * </ul>
     *
     * <p><b>Known limitation — version dedup requires package overlap:</b> version-duplicate
     * detection only runs within the conflict-detected set (jars sharing at least one package).
     * Two versions of the same library whose packages were reorganized between releases will not
     * be detected as duplicates and both will land on {@code --module-path}, where JPMS will
     * reject them with "two versions of module X found". This is rare in practice but worth
     * noting for unusual dependency graphs.
     *
     * <p><b>Two proper modules sharing a package:</b> if both conflicting jars are proper named
     * modules (have {@code module-info.class}), neither is demoted. JPMS will then report this
     * as a fatal split-package error at runtime. This is intentional — two proper modules with
     * a genuine package conflict represent a broken dependency graph that cannot be silently
     * resolved.
     *
     * @param candidates all paths to analyse
     * @param log        receives a pre-formatted message for each resolution decision
     * @return the conflict resolution result
     */
    public static ConflictResolution resolveConflicts(final List<Path> candidates,
                                                      final Consumer<String> log) {
        // scan packages for each candidate
        final Map<Path, Set<String>> jarPackages = new HashMap<>();
        for (final Path path : candidates) {
            final Set<String> packages = getPackages(path);
            if (!packages.isEmpty()) {
                jarPackages.put(path, packages);
            }
        }

        // invert: package → list of paths that contain it
        final Map<String, List<Path>> packageOwners = new HashMap<>();
        jarPackages.forEach((path, packages) -> {
            for (final String pkg : packages) {
                packageOwners.computeIfAbsent(pkg, k -> new ArrayList<>()).add(path);
            }
        });

        // collect packages claimed by more than one jar
        final Map<String, List<Path>> conflicts = new HashMap<>();
        packageOwners.forEach((pkg, owners) -> {
            if (owners.size() > 1) {
                conflicts.put(pkg, owners);
            }
        });

        final Set<Path> superseded = new HashSet<>();
        final Set<Path> demoted = new HashSet<>();

        if (!conflicts.isEmpty()) {
            final Set<Path> allConflicting = new HashSet<>();
            conflicts.values().forEach(allConflicting::addAll);

            // version duplicates: group by base name, keep newest via semantic version ordering
            final Map<String, List<Path>> byBaseName = new HashMap<>();
            for (final Path path : allConflicting) {
                byBaseName.computeIfAbsent(deriveBaseName(path), k -> new ArrayList<>()).add(path);
            }
            for (final var entry : byBaseName.entrySet()) {
                final var group = entry.getValue();
                if (group.size() > 1) {
                    group.sort(jarVersionDescending());
                    final Path kept = group.get(0);
                    for (int i = 1; i < group.size(); i++) {
                        log.accept(String.format("Version duplicate [%s] superseded by [%s] — dropping",
                            group.get(i).getFileName(), kept.getFileName()));
                        superseded.add(group.get(i));
                    }
                }
            }

            // subset detection: if A's packages ⊆ B's, supersede A
            final var remaining = new HashSet<>(allConflicting);
            remaining.removeAll(superseded);
            for (final Path a : new ArrayList<>(remaining)) {
                if (superseded.contains(a)) {
                    continue;
                }
                final Set<String> pkgsA = jarPackages.getOrDefault(a, Set.of());
                for (final Path b : remaining) {
                    if (a.equals(b) || superseded.contains(b)) {
                        continue;
                    }
                    final Set<String> pkgsB = jarPackages.getOrDefault(b, Set.of());
                    if (pkgsB.containsAll(pkgsA) && pkgsB.size() > pkgsA.size()) {
                        log.accept(String.format("Subset jar [%s] (%d pkgs) subsumed by [%s] (%d pkgs) — dropping",
                            a.getFileName(), pkgsA.size(), b.getFileName(), pkgsB.size()));
                        superseded.add(a);
                        break;
                    }
                }
            }

            // true split packages: demote non-proper modules to classpath
            for (final var entry : conflicts.entrySet()) {
                final List<Path> owners = entry.getValue().stream()
                    .filter(p -> !superseded.contains(p))
                    .toList();
                if (owners.size() > 1) {
                    log.accept(String.format("True split package [%s] across %s — demoting non-primary jars to classpath",
                        entry.getKey(), owners.stream().map(p -> p.getFileName().toString()).toList()));
                    final boolean hasProperModule = owners.stream().anyMatch(AbstractCompile::isProperModule);
                    owners.forEach(jar -> {
                        if (!hasProperModule || !AbstractCompile.isProperModule(jar)) {
                            demoted.add(jar);
                        }
                    });
                }
            }
        }

        return new ConflictResolution(superseded, demoted);
    }

    /**
     * Returns the set of dot-separated package names contained in the given jar or classes
     * directory (non-{@code META-INF} {@code .class} entries only).
     *
     * @param jar path to a jar file or compiled classes directory
     * @return dot-separated package names (e.g. {@code "com.example.foo"}), empty if unreadable
     */
    static Set<String> getPackages(final Path jar) {
        final Set<String> packages = new HashSet<>();
        if (jar == null || !Files.exists(jar)) {
            return packages;
        }
        if (Files.isDirectory(jar)) {
            try (var walk = Files.walk(jar)) {
                walk.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> jar.relativize(p.getParent()).toString()
                        .replace(java.io.File.separatorChar, '.'))
                    .filter(pkg -> !pkg.isEmpty() && !pkg.startsWith("META-INF"))
                    .forEach(packages::add);
            }
            catch (final IOException ignored) {
            }
            return packages;
        }
        if (!jar.toString().endsWith(".jar") || !Files.isRegularFile(jar)) {
            return packages;
        }
        try (JarFile jf = new JarFile(jar.toFile())) {
            final var entries = jf.entries();
            while (entries.hasMoreElements()) {
                final String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF")) {
                    final int lastSlash = name.lastIndexOf('/');
                    if (lastSlash > 0) {
                        packages.add(name.substring(0, lastSlash).replace('/', '.'));
                    }
                }
            }
        }
        catch (final IOException ignored) {
            // skip unreadable jars
        }
        return packages;
    }

    /**
     * Derives the base name of a jar by stripping its version suffix and {@code .jar} extension.
     * Used to detect when an unnamed jar is superseded by a named version of the same library
     * (e.g. {@code jboss-logging-3.3.1.Final.jar} → {@code jboss-logging}).
     *
     * @param jar path to the jar file
     * @return the base name without version or extension
     */
    public static String deriveBaseName(final Path jar) {
        if (jar == null) {
            return "";
        }
        String name = jar.getFileName().toString();
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replaceAll("-(\\d.*)$", "");
    }

    /**
     * Returns {@code true} if the given path is a <em>proper</em> JPMS module — one that carries
     * an explicit {@code module-info.class} (at the jar root or under a multi-release entry).
     * Automatic modules (only {@code Automatic-Module-Name} in the manifest) return {@code false}.
     * Use this to determine which jar "owns" a package when resolving split-package conflicts:
     * proper modules always win over automatic or unnamed modules.
     *
     * @param path path to a jar file or compiled classes directory
     * @return {@code true} if the path is a proper (explicitly-modularised) JPMS module
     */
    public static boolean isProperModule(final Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return Files.exists(path.resolve("module-info.class"));
        }
        if (!path.toString().endsWith(".jar") || !Files.isRegularFile(path)) {
            return false;
        }
        try (JarFile jarFile = new JarFile(path.toFile())) {
            if (jarFile.getEntry("module-info.class") != null) {
                return true;
            }
            final var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                if (entries.nextElement().getName()
                        .matches("META-INF/versions/\\d+/module-info\\.class")) {
                    return true;
                }
            }
            return false;
        }
        catch (final IOException ignored) {
            return false;
        }
    }

    /**
     * Returns {@code true} if the given path is a named JPMS module — either a proper module
     * (has {@code module-info.class} at the root or under a multi-release entry) or an automatic
     * module (declares {@code Automatic-Module-Name} in its manifest). Unnamed jars should go on
     * {@code -classpath}; named modules go on {@code --module-path}.
     *
     * @param path path to a jar file or compiled classes directory
     * @return {@code true} if the path is a named JPMS module
     */
    public static boolean isNamedModule(final Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        if (Files.isDirectory(path)) {
            // compiled classes directory: named if it contains a module-info.class
            return Files.exists(path.resolve("module-info.class"));
        }
        if (!path.toString().endsWith(".jar") || !Files.isRegularFile(path)) {
            return false;
        }
        try (JarFile jarFile = new JarFile(path.toFile())) {
            // proper module: has module-info.class at the root
            if (jarFile.getEntry("module-info.class") != null) {
                return true;
            }
            // multi-release jar: module-info.class may live under META-INF/versions/N/
            final var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                if (entries.nextElement().getName()
                        .matches("META-INF/versions/\\d+/module-info\\.class")) {
                    return true;
                }
            }
            // automatic module: Automatic-Module-Name in manifest
            final var manifest = jarFile.getManifest();
            return manifest != null
                && manifest.getMainAttributes().getValue("Automatic-Module-Name") != null;
        }
        catch (final IOException ignored) {
            return false;
        }
    }

    /**
     * Returns a {@link Comparator} that orders jars by version descending using semantic version
     * ordering so that {@code 11.0.0} wins over {@code 2.0.0} (lexicographic ordering would
     * incorrectly prefer {@code 2} because {@code '2' > '1'}).
     */
    static Comparator<Path> jarVersionDescending() {
        return (a, b) -> {
            final String va = extractVersion(a);
            final String vb = extractVersion(b);
            if (va.isEmpty() && vb.isEmpty()) {
                return 0;
            }
            if (va.isEmpty()) {
                return 1; // b has a version, b wins
            }
            if (vb.isEmpty()) {
                return -1; // a has a version, a wins
            }
            try {
                return Artifact.Version.parse(vb).compareTo(Artifact.Version.parse(va)); // descending
            }
            catch (final IllegalArgumentException ignored) {
                return vb.compareTo(va); // fallback to lexicographic for unparseable strings
            }
        };
    }

    private static String extractVersion(final Path jar) {
        final String base = deriveBaseName(jar);
        final String name = jar.getFileName().toString().replace(".jar", "");
        return name.length() > base.length() ? name.substring(base.length() + 1) : "";
    }
}
