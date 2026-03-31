package build.spin.module.junit;

import build.base.foundation.Exceptional;
import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;
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
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.nio.file.Path;
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

        // Use the Artifact.Resolver to resolve the required JUnit Launcher Artifacts
        final Stream<Path> junitArtifacts = Stream.of(
                "org.junit.platform:junit-platform-console:1.6.0",
                "org.junit.platform:junit-platform-reporting:1.6.0",
                "org.junit.platform:junit-platform-launcher:1.6.0",
                "org.junit.platform:junit-platform-engine:1.6.0",
                "org.junit.platform:junit-platform-commons:1.6.0",
                "org.apiguardian:apiguardian-api:jar:1.1.0",
                "org.opentest4j:opentest4j:jar:1.2.0",
                "org.junit.jupiter:junit-jupiter-engine:5.6.0")
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

        try (JDKApplication junit = this.machine.launch(
            JDKApplication.class,
            Executable.of(executable),
            javaHome,
            Name.of("JUnit Platform"),
            junitClassPath,
            MainClass.of("org.junit.platform.console.ConsoleLauncher"),
            Argument.of("--reports-dir=" + reportPath),
            Argument.of("--scan-classpath"),
            Console.ofSystem())) {

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
}
