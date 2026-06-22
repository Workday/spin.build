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
    private JDKModuleDescriptor descriptor;

    @Inject
    @System
    private JDKVersion systemJavaVersion;

    /**
     * Execute {@code jlink} on this {@link Project}
     *
     * @param buildPath the build path for the {@link Project}
     * @param analysis  the {@link DependencyAnalysis} containing information for linking
     * @return the {@link Path} the path of the {@code jlink} produced Java Runtime
     * @throws Exception should the {@link Task} execution fail
     */
    public Path jlink(final Path buildPath,
                      final DependencyAnalysis analysis)
        throws Exception {

        // jlink only makes sense for executable applications. Skip silently for library modules.
        final Optional<String> mainClass = detectMainClass(this.project.path(), this.recorder);
        if (mainClass.isEmpty()) {
            this.recorder.diagnostic("Skipping jlink for [%s]: no main class found", this.project.path());
            return buildPath;
        }

        // establish the name of the package and script
        final var packageName = this.project.name();
        final var scriptName = packageName + ".sh";

        // establish the path in which to generate the jlink runtime package
        final var packagePath = buildPath.resolve(packageName);

        // ------
        // resolve the JDK to use for linking
        final var jdk = this.platform.getVersion(this.systemJavaVersion.major())
            .or(this.platform::getLatest)
            .orElseThrow(() -> new RuntimeException("No JDK found for Java " + this.systemJavaVersion.major() + " and no latest JDK available on platform"));
        final var javaHome = jdk.home().path();
        final var jlinkPath = javaHome.resolve("bin/jlink");

        // Derive the set of module names available in the target JDK by reading the
        // jmods/ directory.  We only need the names for --add-modules filtering; we do
        // NOT use ModuleFinder.of(.jmod files) because the JDK rejects .jmod reads at
        // execution time ("JMOD format not supported at execution time") — .jmod is a
        // link-time-only format.  Filename stripping is sufficient and reliable: the
        // file is always named <module-name>.jmod.
        final var jmodsDir = javaHome.resolve("jmods");
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

        final var recordingObserver = new RecordingSubscriber<String>();
        final ErrorCapture captured = new ErrorCapture();
        try (var jlink = this.machine.launch(Application.class,
            Executable.of(jlinkPath.toString()),
            Name.of("jlink"),
            Argument.of("--module-path"), Argument.of(analysis.modulePath()),
            Argument.of("--output"), Argument.of(packagePath),
            Argument.of("--add-modules"), Argument.of(moduleNames),
            Argument.of("--strip-debug"),
            Argument.of("--no-header-files"),
            Argument.of("--no-man-pages"),
            Argument.of("--compress"), Argument.of("zip-6"),
            Argument.of("--vm"), Argument.of("server"),
            StandardOutputSubscriber.of(recordingObserver),
            captured.triageSubscriber(ErrorCapture::isJvmNoise, this.recorder::warn, this.recorder::error))) {

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
                classification = ModuleGraphClassifier.classify(
                    candidatePaths,
                    Set.of(rootModule),
                    msg -> this.recorder.info("[classify] %s", msg));
            }
            final Set<Path> modulePathJars = new LinkedHashSet<>(classification.modulePath());

            final var nativePlatform = currentNativePlatform();
            final List<Path> classPathTargets = new ArrayList<>();
            for (final var source : candidatePaths) {
                final var targetDir = modulePathJars.contains(source) ? modulePath : classPathDir;
                final var target = targetDir.resolve(source.getFileName());
                Files.copy(source, target);
                if (nativePlatform.isPresent()) {
                    final var p = nativePlatform.get();
                    if (stripForeignNatives(target, p.osDir(), p.archDir())) {
                        this.recorder.info("[jlink] stripped foreign native platforms from %s (kept %s/%s)",
                            target.getFileName(), p.osDir(), p.archDir());
                    }
                }
                if (targetDir == classPathDir) {
                    classPathTargets.add(target);
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
                new ScriptTemplate(classPath, rootModule, mainClass.get(), packageName).render(new TextOut(writer));
            }

            // make the script executable
            scriptPath.resolve(scriptName).toFile().setExecutable(true);
        }

        return jlinkPath;
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

    private record NativePlatform(String osDir, String archDir) {
    }

    private static Optional<NativePlatform> currentNativePlatform() {
        // java.lang.System is used explicitly — `System` alone resolves to the @System annotation import.
        final var osName = java.lang.System.getProperty("os.name", "").toLowerCase();
        final var osArch = java.lang.System.getProperty("os.arch", "").toLowerCase();

        final String osDir;
        if (osName.contains("mac")) {
            osDir = "Mac";
        } else if (osName.contains("linux")) {
            osDir = "Linux";
        } else if (osName.contains("windows")) {
            osDir = "Windows";
        } else if (osName.contains("freebsd")) {
            osDir = "FreeBSD";
        } else if (osName.contains("sunos")) {
            osDir = "SunOS";
        } else if (osName.contains("aix")) {
            osDir = "AIX";
        } else {
            return Optional.empty();
        }

        final String archDir = switch (osArch) {
            case "aarch64", "arm64" -> "aarch64";
            case "x86_64", "amd64" -> "x86_64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "ppc64le" -> "ppc64le";
            case "ppc64" -> "ppc64";
            case "ppc" -> "ppc";
            case "s390x" -> "s390x";
            case "riscv64" -> "riscv64";
            case "loongarch64" -> "loongarch64";
            default -> osArch;
        };

        return Optional.of(new NativePlatform(osDir, archDir));
    }

    // Returns the OS/arch components from a jar entry of the form: <prefix>/native/<OS>/<arch>/<file>,
    // or null if the entry doesn't match that structure.
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
        final var foreignPlatforms = new java.util.LinkedHashSet<String>();
        try (var zf = new ZipFile(jar.toFile())) {
            for (final var e = zf.entries(); e.hasMoreElements();) {
                final var parts = nativeOsArch(e.nextElement().getName());
                if (parts != null && !parts[0].equals(osDir)) {
                    foreignPlatforms.add(parts[0] + "/" + parts[1]);
                }
            }
            if (foreignPlatforms.isEmpty()) {
                return false;
            }
            final var tmp = Files.createTempFile(jar.getParent(), jar.getFileName().toString(), ".tmp");
            try {
                try (var out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                    for (final var e = zf.entries(); e.hasMoreElements();) {
                        final var entry = e.nextElement();
                        final var parts = nativeOsArch(entry.getName());
                        if (parts != null && !parts[0].equals(osDir)) {
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
