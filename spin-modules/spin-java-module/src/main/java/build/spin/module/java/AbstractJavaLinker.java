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

import build.base.flow.RecordingSubscriber;
import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.base.template.TextOut;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spawn.application.Application;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.application.option.Name;
import build.spawn.application.option.StandardOutputSubscriber;
import build.spawn.jdk.JDK;
import build.spawn.platform.local.LocalMachine;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.System;
import build.spin.common.ProcessFailedException;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleGraphClassifier;
import build.spin.module.modulesystem.ModuleReference;
import jakarta.inject.Inject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * An abstract {@link Task} to perform Java Linking using the Java Platform
 * <a href="https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html">jlink</a> tool
 * on the compiled and packaged {@link Artifact} for a {@link Project}.
 * <p>
 * A {@code ScriptTemplate} (generated from {@code ScriptTemplate.jt}) generates a unix-based script to execute the linked application.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public abstract class AbstractJavaLinker
    implements Task<Set<Path>> {

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private JavaPlatform platform;

    @Inject
    private LocalMachine machine;

    @Inject
    private Project project;

    @Inject
    private JDKModuleDescriptor descriptor;

    @Inject
    @System
    private JDKVersion systemJavaVersion;

    /**
     * Execute {@code jlink} on this {@link Project}, once per {@link TargetPlatform} a {@link JavaPlatform#targets()}
     * {@link JDK} is available for, i.e. staging a foreign-platform {@link JDK} is sufficient to have a runtime
     * image generated for it — no explicit target selection is required.
     *
     * @param buildPath the build path for the {@link Project}
     * @param analysis  the {@link DependencyAnalysis} containing information for linking
     * @return the {@link Set} of {@link Path}s of the {@code jlink} produced Java Runtimes, one per target platform
     * @throws Exception should the {@link Task} execution fail
     */
    public Set<Path> jlink(final Path buildPath,
                           final DependencyAnalysis analysis)
        throws Exception {

        // jlink only makes sense for executable applications. Skip silently for library modules.
        final Optional<String> mainClass = detectMainClass(this.project.path(), this.recorder);
        if (mainClass.isEmpty()) {
            this.recorder.diagnostic("Skipping jlink for [%s]: no main class found", this.project.path());
            return Set.of();
        }

        final var targets = this.platform.targets().toList();
        if (targets.isEmpty()) {
            throw new RuntimeException("No JDKs available for jlink");
        }

        // the host's own image always lives at the historical flat <packageName>/ path, resolved
        // dynamically per build host (never hardcoded) — so existing tooling that assumes that path
        // (e.g. spin's own self-hosting bootstrap) keeps working unmodified on any host platform.
        // Only additional, non-host targets get namespaced under <packageName>/<os>-<arch>/.
        final var hostTarget = JavaPlatform.hostTarget();

        final Set<Path> images = new LinkedHashSet<>();
        for (final var target : targets) {
            images.add(linkForTarget(buildPath, analysis, mainClass.get(), target, !target.equals(hostTarget)));
        }
        return images;
    }

    private Path linkForTarget(final Path buildPath,
                               final DependencyAnalysis analysis,
                               final String mainClass,
                               final TargetPlatform target,
                               final boolean namespaceByTarget)
        throws Exception {

        // establish the name of the package and script
        final var packageName = this.project.name();
        final var scriptName = packageName + ".sh";

        // establish the path in which to generate the jlink runtime package; namespaced unless this is
        // the host's own target — see the comment in jlink() above
        final var packagePath = namespaceByTarget
            ? buildPath.resolve(packageName).resolve(target.toString())
            : buildPath.resolve(packageName);

        // ------
        // resolve the JDK whose jmods define the *target* platform's modules.
        final var targetJdk = this.platform.getVersion(this.systemJavaVersion.major(), target)
            .or(() -> this.platform.getLatest(target))
            .orElseThrow(() -> new RuntimeException("No JDK found for target " + target + " and Java "
                + this.systemJavaVersion.major() + ", and no latest JDK available for that target"));
        final var targetJavaHome = targetJdk.home().path();

        // resolve the JDK whose jlink binary can actually be *executed* on this host — a jlink binary
        // built for a foreign target platform (e.g. a different OS or CPU architecture) cannot run here.
        // jlink treats .jmod files as portable data, so running the host's jlink against a foreign
        // target's --module-path (below) is how genuine cross-target linking works.
        final var hostJdk = this.platform.getVersion(this.systemJavaVersion.major())
            .or(this.platform::getLatest)
            .orElseThrow(() -> new RuntimeException(
                "No host-executable JDK found for Java " + this.systemJavaVersion.major()
                    + " to run jlink with, and no latest host JDK available"));
        final var jlinkPath = hostJdk.home().path().resolve("bin/jlink");

        // Derive the set of module names available in the target JDK by reading the
        // jmods/ directory.  We only need the names for --add-modules filtering; we do
        // NOT use ModuleFinder.of(.jmod files) because the JDK rejects .jmod reads at
        // execution time ("JMOD format not supported at execution time") — .jmod is a
        // link-time-only format.  Filename stripping is sufficient and reliable: the
        // file is always named <module-name>.jmod.
        final var jmodsDir = targetJavaHome.resolve("jmods");
        final Set<String> jdkModuleNames;
        if (Files.isDirectory(jmodsDir)) {
            try (var jmodPaths = Files.list(jmodsDir)) {
                jdkModuleNames = jmodPaths
                    .filter(p -> p.toString().endsWith(".jmod"))
                    .map(p -> {
                        final var n = p.getFileName().toString();
                        return n.substring(0, n.length() - ".jmod".length());
                    })
                    .collect(Collectors.toSet());
            }
        } else {
            jdkModuleNames = ModuleFinder.ofSystem().findAll().stream()
                .map(mr -> mr.descriptor().name())
                .collect(Collectors.toSet());
        }

        // ------
        // create a list of the Java Platform modules to link
        // (the rest are going on the ClassPath for now!)

        final var moduleNames = analysis.platformModules()
            .map(ModuleReference::name)
            .filter(jdkModuleNames::contains)  // only include modules that actually exist in this JDK
            .collect(Collectors.joining(","));

        // the host's jlink no longer implicitly resolves platform modules from "itself" the way it did
        // when jlink was always run from within the target JDK — the target's jmods must be on the
        // module path explicitly so jlink can find platform modules like java.base for the target
        final var jlinkModulePath = Files.isDirectory(jmodsDir)
            ? analysis.modulePath() + java.io.File.pathSeparator + jmodsDir
            : analysis.modulePath().toString();

        final var recordingObserver = new RecordingSubscriber<String>();
        final ErrorCapture captured = new ErrorCapture();

        // jlink's --strip-debug shells out to the host's native objcopy, which can't parse a foreign
        // target's binaries (e.g. running x86_64 objcopy against aarch64 or Mach-O native libraries) —
        // only strip when linking the host's own target
        final List<build.base.configuration.Option> jlinkOptions = new ArrayList<>(List.of(
            Executable.of(jlinkPath.toString()),
            Name.of("jlink"),
            Argument.of("--module-path"), Argument.of(jlinkModulePath),
            Argument.of("--output"), Argument.of(packagePath),
            Argument.of("--add-modules"), Argument.of(moduleNames)));
        if (!namespaceByTarget) {
            jlinkOptions.add(Argument.of("--strip-debug"));
        }
        jlinkOptions.addAll(List.of(
            Argument.of("--no-header-files"),
            Argument.of("--no-man-pages"),
            Argument.of("--compress"), Argument.of("zip-6"),
            Argument.of("--vm"), Argument.of("server"),
            StandardOutputSubscriber.of(recordingObserver),
            captured.triageSubscriber(ErrorCapture::isJvmNoise, this.recorder::warn, this.recorder::error)));

        try (var jlink = this.machine.launch(Application.class,
            jlinkOptions.toArray(build.base.configuration.Option[]::new))) {

            try {
                jlink.onExit().get();
            } catch (final Exception e) {
                throw new ProcessFailedException("jlink Execution Failed",
                    ErrorCapture.selectOutput(captured.output(), recordingObserver.items()), e);
            }

            if (jlink.exitValue().orElse(0) != 0) {
                throw new ProcessFailedException(
                    "Runtime Image Generation Failed (exit code: " + jlink.exitValue().orElse(-1) + ")",
                    ErrorCapture.selectOutput(captured.output(), recordingObserver.items()));
            }

            // -----
            // Classify application jars into --module-path (modules/) vs -cp (classpath/).
            //
            // The jlink subprocess above produces a Java runtime image, but the application
            // jars themselves still need to be copied into the image and launched by the
            // generated script. Historically they were copied flat into modules/ and launched
            // with `java -cp modules/* Spin`, which broke the moment any provider migrated
            // from @AutoService to a JPMS-native `provides` clause (such providers only work
            // when their jars are loaded as named modules).
            //
            // Classification uses ModuleFinder + Configuration.resolve on the real on-disk
            // jars — the same approach {@code build.spin.application.Launcher} uses for the
            // spin1 Maven-exec launch. Split-package conflicts are iteratively demoted to
            // classpath where the JPMS package-uniqueness rule doesn't apply; automatic
            // modules on --module-path still reach the demoted classes via ALL-UNNAMED.
            //
            // Dependency dedupe (both by Maven (groupId, artifactId) and by JPMS module
            // name) already happened upstream in {@link AbstractJavaDependencyAnalysis}, so
            // analysis.dependencies() is a clean canonical set here.
            //
            // Note: we use `classpath/` (not `lib/`) because jlink writes its runtime image
            // into packagePath/lib/modules and owns the lib/ directory.

            final var modulePath = packagePath.resolve("modules");
            final var classPathDir = packagePath.resolve("classpath");
            Files.createDirectories(modulePath);
            Files.createDirectories(classPathDir);

            final List<Path> candidatePaths = analysis.dependencies()
                .flatMap(dep -> dep.artifactDescriptor().path().stream())
                .toList();

            final var rootModule = this.descriptor.moduleName().toString();

            // Prefer classifyAndResolve so unreachable jars are pruned from modules/.
            // When spin runs from its own jlink image ModuleFinder.ofSystem() only covers
            // spin's modules, so resolving an app that requires JDK modules outside that
            // image (e.g. java.net.http) will fail — fall back to classify-only in that case.
            ModuleGraphClassifier.Classification classification;
            try {
                classification = ModuleGraphClassifier.classifyAndResolve(
                    candidatePaths,
                    Set.of(rootModule),
                    rootModule,
                    java.lang.module.Configuration.empty(),
                    ModuleFinder.ofSystem(),
                    msg -> this.recorder.info("[classify] %s", msg));
            } catch (final IllegalStateException e) {
                this.recorder.warn("[classify] classifyAndResolve failed (%s) — falling back to classify-only; "
                    + "unreachable jars will NOT be pruned from the module-path", e.getMessage());
                classification = ModuleGraphClassifier.classify(
                    candidatePaths,
                    Set.of(rootModule),
                    msg -> this.recorder.info("[classify] %s", msg));
            }
            final Set<Path> modulePathJars = new LinkedHashSet<>(classification.modulePath());

            final var nativePlatform = nativePlatformFor(target);
            final List<Path> classPathTargets = new ArrayList<>();
            for (final var source : candidatePaths) {
                final var targetDir = modulePathJars.contains(source) ? modulePath : classPathDir;
                final var destination = targetDir.resolve(source.getFileName());
                Files.copy(source, destination);
                if (nativePlatform.isPresent()) {
                    final var p = nativePlatform.get();
                    if (stripForeignNatives(destination, p.osDir(), p.archDir())) {
                        this.recorder.info("[jlink] stripped foreign native platforms from %s (kept %s/%s)",
                            destination.getFileName(), p.osDir(), p.archDir());
                    }
                }
                if (targetDir == classPathDir) {
                    classPathTargets.add(destination);
                }
            }

            // ---------
            // create the script to execute the application
            final var scriptPath = packagePath.resolve("bin");

            // The script template references $MP (modules/) and $LIB (classpath/). Only the
            // classpath entries are listed explicitly; the module-path is a single directory.
            final var classPath = classPathTargets.stream()
                .map(path -> "$LIB/" + path.getFileName())
                .collect(Collectors.joining(":"));

            try (var writer = Files.newBufferedWriter(scriptPath.resolve(scriptName))) {
                new ScriptTemplate(classPath, rootModule, mainClass, packageName).render(new TextOut(writer));
            }

            // make the script executable
            scriptPath.resolve(scriptName).toFile().setExecutable(true);
        }

        return packagePath;
    }

    private static Optional<String> detectMainClass(final Path projectPath,
                                                    final TelemetryRecorder recorder) {
        final Path srcDir = projectPath.resolve("src/main/java");
        if (!Files.isDirectory(srcDir)) {
            return Optional.empty();
        }
        try (var walk = Files.walk(srcDir)) {
            return walk
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("module-info.java"))
                .filter(AbstractJavaLinker::hasMainMethod)
                .map(p -> toClassName(p, srcDir))
                .peek(name -> recorder.diagnostic("[jlink] auto-detected main class: %s", name))
                .findFirst();
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private static boolean hasMainMethod(final Path javaFile) {
        try {
            return Files.readString(javaFile).contains("void main(");
        } catch (final IOException e) {
            return false;
        }
    }

    private static String toClassName(final Path javaFile, final Path srcDir) {
        final Path rel = srcDir.relativize(javaFile);
        final StringBuilder name = new StringBuilder();
        for (int i = 0; i < rel.getNameCount(); i++) {
            if (i > 0) {
                name.append('.');
            }
            final String part = rel.getName(i).toString();
            name.append(i == rel.getNameCount() - 1 ? part.replaceAll("\\.java$", "") : part);
        }
        return name.toString();
    }

    record NativePlatform(String osDir, String archDir) {
    }

    static Optional<NativePlatform> nativePlatformFor(final TargetPlatform target) {
        final String osDir = switch (target.operatingSystem()) {
            case MAC -> "Mac";
            case LINUX -> "Linux";
            case WINDOWS -> "Windows";
            case OTHER -> null;
        };
        if (osDir == null) {
            return Optional.empty();
        }

        final String archDir = switch (target.architecture()) {
            case AARCH64 -> "aarch64";
            case X86_64 -> "x86_64";
            case OTHER -> null;
        };
        if (archDir == null) {
            return Optional.empty();
        }

        return Optional.of(new NativePlatform(osDir, archDir));
    }

    // Normalises the arch segment found in a jar entry path to the same canonical values
    // produced by currentNativePlatform(), so the two can be compared directly.
    // Handles known aliases: aarch_64 (Netty), amd64/x64 (x86_64), i*86 (x86).
    static String normalizeEntryArch(final String entryArch) {
        return switch (entryArch.toLowerCase()) {
            case "x86_64", "amd64", "x64" -> "x86_64";
            case "aarch64", "arm64", "aarch_64" -> "aarch64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> entryArch.toLowerCase();
        };
    }

    // Returns the OS/arch components from a jar entry of the form: <prefix>/native/<OS>/<arch>/<file>,
    // or null if the entry doesn't match that structure.
    // The search requires a leading '/' before "native", so root-level entries like "native/Linux/..." are intentionally excluded.
    static String[] nativeOsArch(final String entryName) {
        final int idx = entryName.indexOf("/native/");
        if (idx < 0) {
            return null;
        }
        final var after = entryName.substring(idx + 8);
        final int first = after.indexOf('/');
        if (first < 0) {
            return null;
        }
        final int second = after.indexOf('/', first + 1);
        if (second < 0 || after.indexOf('/', second + 1) >= 0) {
            return null;
        }
        return new String[]{after.substring(0, first), after.substring(first + 1, second)};
    }

    // Returns true if any foreign-platform native entries were stripped from the jar.
    static boolean stripForeignNatives(final Path jar, final String osDir, final String archDir)
        throws IOException {
        boolean hasForeignNatives = false;
        try (var zf = new ZipFile(jar.toFile())) {
            for (final var e = zf.entries(); e.hasMoreElements();) {
                final var parts = nativeOsArch(e.nextElement().getName());
                if (parts != null && (!parts[0].equals(osDir) || !normalizeEntryArch(parts[1]).equals(archDir))) {
                    hasForeignNatives = true;
                    break;
                }
            }
            if (!hasForeignNatives) {
                return false;
            }
            final var tmp = Files.createTempFile(jar.getParent(), jar.getFileName().toString(), ".tmp");
            try {
                try (var out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                    for (final var e = zf.entries(); e.hasMoreElements();) {
                        final var entry = e.nextElement();
                        final var parts = nativeOsArch(entry.getName());
                        if (parts != null && (!parts[0].equals(osDir) || !normalizeEntryArch(parts[1]).equals(archDir))) {
                            continue;
                        }
                        out.putNextEntry(new ZipEntry(entry.getName()));
                        try (var is = zf.getInputStream(entry)) {
                            is.transferTo(out);
                        }
                        out.closeEntry();
                    }
                }
                Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
        }
        return true;
    }

}
