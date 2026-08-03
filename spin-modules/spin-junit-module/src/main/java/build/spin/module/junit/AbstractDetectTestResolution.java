package build.spin.module.junit;

/*-
 * #%L
 * Spin Junit Module
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

import build.base.option.JDKVersion;
import build.base.version.Version;
import build.spin.Project;
import build.spin.module.java.AbstractDetectResolution;
import build.spin.module.java.JavaCompilerPlugin;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * An {@link AbstractDetectResolution} specialised for JUnit test scopes.
 *
 * <p>In addition to the project's declared dependencies, this resolution always resolves the
 * JUnit Platform ConsoleLauncher and the JUnit Jupiter engine — spin's own test-runner
 * infrastructure that projects are not expected to declare themselves.  Resolving them here,
 * inside the detection task, means they pass through {@code ModuleGraphClassifier} alongside
 * every other candidate, so there is one authoritative split between module-path and classpath
 * with no duplication.
 */
public abstract class AbstractDetectTestResolution
    extends AbstractDetectResolution {

    @Inject
    private Project project;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    @Inject
    private TargetDirectoryName target;

    @Inject
    private JDKVersion javaVersion;

    @Inject
    private ModuleVersioning versioning;

    // Delegates to AbstractDetectResolution.resolveCompiledOutput -- the same multi-build-tool
    // (spin/Maven/Gradle) lookup used for sibling-project candidates -- rather than hardcoding
    // spin's own .build/main/<target> convention. A project built via Maven or Gradle without ever
    // having been built by spin directly has no .build directory at all, so the hardcoded path
    // resolved to nothing and this project's own main output was silently absent from its test
    // resolution's candidates -- surfacing as "cannot find symbol" for any type test sources
    // reference from main, even in the same package.
    @Override
    protected Stream<Path> additionalSiblingCandidates() {
        final boolean hasMatchingCompilerPlugin = this.project.plugins(JavaCompilerPlugin.class)
            .anyMatch(p -> p.getJavaVersion().major() == this.javaVersion.major());
        if (!hasMatchingCompilerPlugin) {
            return Stream.empty();
        }
        return resolveCompiledOutput(this.project.path(), this.buildDirectoryName.get(), this.target.get()).stream();
    }

    @Override
    protected Stream<Artifact> additionalArtifacts() {
        final String jupiterVersion = jupiterVersion(this.versioning);
        final String platformVersion = derivePlatformVersion(jupiterVersion);

        return Stream.of(
            Artifact.parse("org.junit.platform:junit-platform-console:" + platformVersion),
            Artifact.parse("org.junit.jupiter:junit-jupiter-engine:" + jupiterVersion));
    }

    /**
     * Resolves the JUnit Jupiter version to use, falling back to {@code "5.6.0"} when the
     * workspace declares none.
     * <p>
     * The lookup key must be the real JPMS module name of {@code junit-jupiter-api}
     * ({@code org.junit.jupiter.api}), not its groupId ({@code org.junit.jupiter}) — a
     * {@link ModuleVersioning} that prefers ground-truth module names read from jars (as
     * {@code PomDependencyGraphWalker} does) never registers a version under the bare groupId.
     */
    static String jupiterVersion(final ModuleVersioning versioning) {
        return versioning.getVersion("org.junit.jupiter.api")
            .map(Version::get)
            .orElse("5.6.0");
    }

    /**
     * Extracts the major version number from a Jupiter version string (e.g. {@code "6.0.3"} → {@code 6}).
     * Returns {@code 0} if the version cannot be parsed.
     */
    static int jupiterMajorVersion(final String jupiterVersion) {
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
    static String derivePlatformVersion(final String jupiterVersion) {
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
