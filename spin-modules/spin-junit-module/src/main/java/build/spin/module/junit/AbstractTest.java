package build.spin.module.junit;

import build.base.configuration.Option;
import build.base.foundation.Exceptional;
import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
import build.base.option.WorkingDirectory;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.Console;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.application.option.Name;
import build.spawn.jdk.JDK;
import build.spawn.jdk.JDKApplication;
import build.spawn.jdk.option.ClassPath;
import build.spawn.jdk.option.JDKHome;
import build.spawn.jdk.option.MainClass;
import build.spawn.platform.local.LocalMachine;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.Category;
import build.spin.annotation.System;
import build.spin.module.java.JavaPlugin;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
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
    private Artifact.Resolver resolver;

    @Inject
    private ModuleVersioning versioning;

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
     * Execute tests in the specified build {@link Path}, using the provided {@link ClassPath}.
     *
     * @param classPath the runtime {@link ClassPath}
     * @param buildPath the build {@link Path}
     * @return the {@link PathSet} containing the JUnit Reports
     */
    protected PathSet test(final ClassPath classPath,
                           final Path buildPath) {

        // Resolve JUnit artifact versions from the workspace; fall back to known defaults
        final String jupiterVersion = this.versioning.getVersion("org.junit.jupiter")
            .map(v -> v.get())
            .orElse("5.6.0");
        final String platformVersion = derivePlatformVersion(jupiterVersion);
        final String apiguardianVersion = this.versioning.getVersion("org.apiguardian")
            .map(v -> v.get())
            .orElse("1.1.0");
        final String opentest4jVersion = this.versioning.getVersion("org.opentest4j")
            .map(v -> v.get())
            .orElse("1.2.0");

        // Use the Artifact.Resolver to resolve the required JUnit Launcher Artifacts
        final Stream<Path> junitArtifacts = Stream.of(
                "org.junit.platform:junit-platform-console:" + platformVersion,
                "org.junit.platform:junit-platform-reporting:" + platformVersion,
                "org.junit.platform:junit-platform-launcher:" + platformVersion,
                "org.junit.platform:junit-platform-engine:" + platformVersion,
                "org.junit.platform:junit-platform-commons:" + platformVersion,
                "org.apiguardian:apiguardian-api:jar:" + apiguardianVersion,
                "org.opentest4j:opentest4j:jar:" + opentest4jVersion,
                "org.junit.jupiter:junit-jupiter-engine:" + jupiterVersion)
            .map(Artifact::parse)
            .map(this.resolver::resolve)
            .filter(Exceptional::isPresent)
            .map(Exceptional::orElseThrow);

        // the path in which to place reports
        final Path reportPath = buildPath.resolve("reports/tests");

        // establish the ClassPath for the JUnit Tests
        // (include the test/classes on the compilation class path)
        final PathSetBuilder builder = PathSetBuilder.create();
        builder.addAll(junitArtifacts);
        builder.addAll(classPath.paths());

        // include the multi-version compiled classes (as JUnit can't detect them for some reason)
        // when this plugin isn't for the default java version
        final Path targetPath = buildPath.resolve("test/" + this.target.get());
        if (this.javaVersion.major() != defaultJavaVersion.major()) {
            this.project.plugins(JUnitPlugin.class)
                .map(JavaPlugin::getJavaVersion)
                .map(JDKVersion::major)
                .sorted((first, second) -> second - first)
                .forEach(version -> builder.add(targetPath.resolve("META-INF/versions/" + version)));
        }

        final ClassPath junitClassPath = ClassPath.of(builder.build().stream());

        // establish the "java" executable based on the Java Development Kit
        final JDKHome javaHome = this.javaDevelopmentKit.home();
        final String executable = javaHome.path().resolve("bin/java").toString();

        // JUnit 6+ uses subcommands; JUnit 5 uses flat options
        final boolean useSubcommand = jupiterMajorVersion(jupiterVersion) >= 6;

        final ArrayList<Option> args = new ArrayList<>();
        args.add(Executable.of(executable));
        args.add(WorkingDirectory.of(this.project.path().toString()));
        args.add(javaHome);
        args.add(Name.of("JUnit Platform"));
        args.add(junitClassPath);
        args.add(MainClass.of("org.junit.platform.console.ConsoleLauncher"));
        if (useSubcommand) {
            args.add(Argument.of("execute"));
        }
        args.add(Argument.of("--reports-dir=" + reportPath));
        args.add(Argument.of("--scan-classpath"));
        args.add(Console.ofSystem());

        try (JDKApplication junit = this.machine.launch(
            JDKApplication.class,
            args.toArray(Option[]::new))) {

            junit.onExit().get();

            // output the exit value
            junit.exitValue()
                .ifPresent(value -> {
                    this.recorder.info("JUnit Platform finished with exit code %d", value);

                    if (value != 0) {
                        throw new RuntimeException("JUnit Failed (exit code " + value + ")");
                    }
                });
        }
        catch (final Exception e) {
            this.recorder.error(e, "Failed to execute JUnit");

            throw new RuntimeException("JUnit Failure", e);
        }

        return PathSetBuilder.create().build();
    }

    /**
     * Extracts the major version number from a version string (e.g. {@code "6.0.3"} → {@code 6}).
     * Returns {@code 0} if the version cannot be parsed.
     */
    private static int jupiterMajorVersion(final String jupiterVersion) {
        final int dot = jupiterVersion.indexOf('.');
        final String majorStr = dot < 0 ? jupiterVersion : jupiterVersion.substring(0, dot);
        try {
            return Integer.parseInt(majorStr);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Derives the JUnit Platform version from the JUnit Jupiter version.
     * <p>
     * JUnit 6+: platform version matches Jupiter (e.g. {@code 6.0.3} → {@code 6.0.3}).
     * JUnit 5: platform major is {@code 1} (e.g. {@code 5.6.0} → {@code 1.6.0}).
     */
    private static String derivePlatformVersion(final String jupiterVersion) {
        final int dot = jupiterVersion.indexOf('.');
        if (dot < 0) {
            return jupiterVersion;
        }
        if (jupiterMajorVersion(jupiterVersion) >= 6) {
            return jupiterVersion;
        }
        return "1" + jupiterVersion.substring(dot);
    }
}
