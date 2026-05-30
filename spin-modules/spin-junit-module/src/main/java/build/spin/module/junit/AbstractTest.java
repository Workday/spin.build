package build.spin.module.junit;

import build.base.configuration.Option;
import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
import build.base.option.WorkingDirectory;
import build.base.telemetry.TelemetryRecorder;
import build.base.version.Version;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.application.option.Name;
import build.spawn.jdk.JDK;
import build.spawn.jdk.JDKApplication;
import build.spawn.jdk.option.AddModules;
import build.spawn.jdk.option.AddOpens;
import build.spawn.jdk.option.AddReads;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.JDKHome;
import build.spawn.jdk.option.JDKOption;
import build.spawn.jdk.option.MainClass;
import build.spawn.jdk.option.ModulePath;
import build.spawn.jdk.option.PatchModule;
import build.spawn.platform.local.LocalMachine;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.Category;
import build.spin.annotation.System;
import build.spin.common.ProcessFailedException;
import build.spin.module.java.ErrorCapture;
import build.spin.module.java.JavaCompilerPlugin;
import build.spin.module.java.JavaPlugin;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.module.modulesystem.TestArguments;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * An abstract {@link Task} to execute tests using the JUnit Platform.
 *
 * @author brian.oliver
 * @since Aug-2020s
 */
@Category("test")
@Category("build")
public abstract class AbstractTest
    implements Task<PathSet> {

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private LocalMachine machine;

    @Inject
    private JDK javaDevelopmentKit;

    @Inject
    private ModuleVersioning versioning;

    @Inject
    private JDKModuleDescriptor moduleDescriptor;

    @Inject
    private JDKVersion javaVersion;

    @Inject
    @System
    private JDKVersion defaultJavaVersion;

    @Inject
    private Project project;

    @Inject
    private TargetDirectoryName target;

    /**
     * Execute tests in the specified build {@link Path}, using the provided module-path and classpath.
     *
     * @param modulePath the runtime {@link ModulePath} (from DetectTestModulePath)
     * @param classPath  the runtime {@link ClassPath} (from DetectTestClassPath)
     * @param buildPath  the build {@link Path}
     * @return the {@link PathSet} containing the JUnit Reports
     */
    protected PathSet test(final ModulePath modulePath,
                           final ClassPath classPath,
                           final Path buildPath) {

        // JUnit 6+ uses subcommands; JUnit 5 uses flat options
        final String jupiterVersion = this.versioning.getVersion("org.junit.jupiter")
            .map(Version::get)
            .orElse("5.6.0");
        final boolean useSubcommand = AbstractDetectTestResolution.jupiterMajorVersion(jupiterVersion) >= 6;

        final Path testClassesDir = buildPath.resolve("test/" + this.target.get());
        final Path reportPath = buildPath.resolve("reports/tests");

        // include the multi-version compiled classes for non-default JDK test plugins
        final ClassPath effectiveClassPath;
        if (this.javaVersion.major() != this.defaultJavaVersion.major()) {
            final PathSetBuilder builder = PathSetBuilder.create();
            builder.addAll(classPath.stream());
            this.project.plugins(JUnitPlugin.class)
                .map(JavaPlugin::getJavaVersion)
                .map(JDKVersion::major)
                .sorted((first, second) -> second - first)
                .forEach(version -> builder.add(testClassesDir.resolve("META-INF/versions/" + version)));
            effectiveClassPath = ClassPath.of(builder.build().stream());
        } else {
            effectiveClassPath = classPath;
        }

        final boolean hasMainModuleInfo = Files.exists(
            this.project.path().resolve("src/main/java/module-info.java"));
        final boolean hasTestModuleInfo = Files.exists(
            this.project.path().resolve("src/test/java/module-info.java"));

        // Derive the root module name.  The JUnit plugin's injected ModuleDescriptor uses a
        // project-derived name when no src/test/java/module-info.java exists (e.g. "base.foundation"
        // from "base-foundation").  For cases that need the real JPMS module name we get it from
        // the matching JavaCompilerPlugin instead.
        final String rootModule;
        if (hasMainModuleInfo && !hasTestModuleInfo) {
            rootModule = this.project.plugins(JavaCompilerPlugin.class)
                .filter(plugin -> plugin.getJavaVersion().major() == this.javaVersion.major())
                .findFirst()
                .map(JavaCompilerPlugin::getModuleDescriptor)
                .map(d -> d.moduleName().toString())
                .orElse(this.moduleDescriptor.moduleName().toString());
        } else {
            rootModule = this.moduleDescriptor.moduleName().toString();
        }

        final JDKHome javaHome = this.javaDevelopmentKit.home();
        final String executable = javaHome.path().resolve("bin/java").toString();

        final ArrayList<Option> args = new ArrayList<>();
        args.add(Executable.of(executable));
        args.add(WorkingDirectory.of(this.project.path().toString()));
        args.add(javaHome);
        args.add(Name.of("JUnit Platform"));

        this.project.findResource(TestArguments.class).ifPresent(testArgs ->
            testArgs.get(this.project)
                .map(token -> resolveArtifactCoord(token, modulePath, classPath))
                .forEach(token -> args.add(JDKOption.of(token))));

        if (hasTestModuleInfo) {
            // case A — test sources have their own module-info.java: the test module is a
            // named module; JUnit selects tests by module name.
            final ModulePath augmentedModulePath =
                ModulePath.of(modulePath.stream(), Stream.of(testClassesDir));
            args.add(augmentedModulePath);
            args.add(effectiveClassPath);
            args.add(AddModules.of(rootModule));
            args.add(MainClass.of("org.junit.platform.console.ConsoleLauncher"));
            if (useSubcommand) {
                args.add(Argument.of("execute"));
            }
            args.add(Argument.of("--select-module=" + rootModule));
        } else if (hasMainModuleInfo) {
            // case B — main sources are a named module but test sources have no module-info.java.
            // Patch the main module with test classes so that JPMS service providers declared
            // via "provides...with" in module-info.java remain visible to ServiceLoader.
            // Emit --add-opens for every compiled test package so JUnit's reflection can
            // instantiate and invoke test classes without InaccessibleObjectException.
            args.add(modulePath);
            // classpath entry makes ClassLoader.getResourceAsStream() find test resources;
            // --patch-module alone only serves Module.getResourceAsStream() in named-module context.
            final ClassPath classPathWithTestDir = ClassPath.of(
                Stream.of(testClassesDir), effectiveClassPath.stream());
            args.add(classPathWithTestDir);
            args.add(PatchModule.of(rootModule, testClassesDir.toString()));
            args.add(AddModules.of(rootModule, "ALL-MODULE-PATH"));
            args.add(AddReads.of(rootModule, "ALL-UNNAMED"));

            // Add --add-reads for every named-module JAR on the module path that the main
            // module doesn't explicitly require (e.g. assertj, JUnit APIs used in tests).
            // ModuleFinder reads the module-info.class from each JAR to get its real name.
            modulePath.stream()
                .filter(p -> !Files.isDirectory(p))
                .forEach(jar -> {
                    try {
                        ModuleFinder.of(jar).findAll().forEach(ref ->
                            args.add(AddReads.of(rootModule, ref.descriptor().name())));
                    } catch (final Exception ignored) {
                        // skip JARs that cannot be opened as modules
                    }
                });

            try (Stream<Path> walk = Files.walk(testClassesDir)) {
                walk.filter(Files::isDirectory)
                    .map(testClassesDir::relativize)
                    .map(Path::toString)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.replace(File.separatorChar, '.'))
                    .forEach(pkg ->
                        args.add(AddOpens.of(rootModule, pkg, "org.junit.platform.commons")));
            } catch (final IOException e) {
                this.recorder.warn("Could not scan test classes for --add-opens: %s", e.getMessage());
            }

            args.add(MainClass.of("org.junit.platform.console.ConsoleLauncher"));
            if (useSubcommand) {
                args.add(Argument.of("execute"));
            }
            args.add(Argument.of("--select-module=" + rootModule));
        } else {
            // case C — no module-info.java anywhere: fully non-modular project.
            // Flatten everything onto the classpath and use --scan-classpath.
            final ClassPath withTestClasses = ClassPath.of(
                modulePath.stream(),
                effectiveClassPath.stream(),
                Stream.of(testClassesDir));
            args.add(withTestClasses);
            args.add(MainClass.of("org.junit.platform.console.ConsoleLauncher"));
            if (useSubcommand) {
                args.add(Argument.of("execute"));
            }
            args.add(Argument.of("--scan-classpath"));
        }

        args.add(Argument.of("--reports-dir=" + reportPath));
        args.add(Console.ofSystem());

        final ErrorCapture captured = new ErrorCapture();
        args.add(captured.subscriber(line -> this.recorder.error(line)));

        try (JDKApplication junit = this.machine.launch(
            JDKApplication.class,
            args.toArray(Option[]::new))) {

            junit.onExit().get();

            junit.exitValue()
                .ifPresent(value -> {
                    if (value != 0) {
                        throw new ProcessFailedException(
                            "JUnit Failed (exit code " + value + ")",
                            captured.output());
                    }
                });
        } catch (final Exception e) {
            this.recorder.error(e, "Failed to execute JUnit");

            throw new RuntimeException("JUnit Failure", e);
        }

        return PathSetBuilder.create().build();
    }

    /**
     * Pattern matching {@code ${groupId:artifactId:type[:classifier]}} as emitted by the
     * {@code maven-dependency-plugin:properties} goal.
     */
    private static final java.util.regex.Pattern COORD_REF =
        java.util.regex.Pattern.compile("\\$\\{([^:}]+):([^:}]+):([^:}]+)(?::([^:}]+))?}");

    /**
     * Substitutes any {@code ${groupId:artifactId:type[:classifier]}} occurrences in the given
     * token with the absolute path of a matching jar found on the supplied module/class paths.
     * <p>
     * Matching is by parent-path segments {@code <localRepo>/<groupId-as-path>/<artifactId>/<version>}
     * and filename {@code <artifactId>-<version>[-<classifier>].<type>}. If no match is found, the
     * literal coord reference is left in place so the JVM surfaces a clear error.
     */
    private static String resolveArtifactCoord(final String token,
                                               final ModulePath modulePath,
                                               final ClassPath classPath) {
        if (token.indexOf('$') < 0) {
            return token;
        }
        final Matcher matcher = COORD_REF.matcher(token);
        if (!matcher.find()) {
            return token;
        }
        final StringBuilder out = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            final String groupId = matcher.group(1);
            final String artifactId = matcher.group(2);
            final String type = matcher.group(3);
            final String classifier = matcher.group(4);
            final Path resolved = findJar(groupId, artifactId, type, classifier, modulePath, classPath);
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                resolved != null ? resolved.toString() : matcher.group(0)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Path findJar(final String groupId,
                                final String artifactId,
                                final String type,
                                final String classifier,
                                final ModulePath modulePath,
                                final ClassPath classPath) {
        final String groupPath = groupId.replace('.', '/');
        return Stream.concat(modulePath.stream(), classPath.stream())
            .filter(path -> matchesCoord(path, groupPath, artifactId, type, classifier))
            .map(Path::toAbsolutePath)
            .findFirst()
            .orElse(null);
    }

    private static boolean matchesCoord(final Path path,
                                        final String groupPath,
                                        final String artifactId,
                                        final String type,
                                        final String classifier) {
        final String filename = path.getFileName().toString();
        if (!filename.startsWith(artifactId + "-") || !filename.endsWith("." + type)) {
            return false;
        }
        // expected parent layout: <...>/<groupPath>/<artifactId>/<version>/<filename>
        final Path parent = path.getParent();
        if (parent == null || parent.getParent() == null) {
            return false;
        }
        final String pathStr = parent.toString().replace(File.separatorChar, '/');
        if (!pathStr.endsWith("/" + groupPath + "/" + artifactId + "/" + parent.getFileName().toString())) {
            return false;
        }
        final String version = parent.getFileName().toString();
        final String expected = classifier == null
            ? artifactId + "-" + version + "." + type
            : artifactId + "-" + version + "-" + classifier + "." + type;
        return filename.equals(expected);
    }

}
