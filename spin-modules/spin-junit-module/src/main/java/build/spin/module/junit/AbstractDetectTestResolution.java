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

import build.spin.module.java.AbstractDetectResolution;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;
import jakarta.inject.Inject;

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
    private ModuleVersioning versioning;

    @Override
    protected Stream<Artifact> additionalArtifacts() {
        final String jupiterVersion = this.versioning.getVersion("org.junit.jupiter")
            .map(ModuleDescriptor.Version::get)
            .orElse("5.6.0");
        final String platformVersion = derivePlatformVersion(jupiterVersion);

        return Stream.of(
            Artifact.parse("org.junit.platform:junit-platform-console:" + platformVersion),
            Artifact.parse("org.junit.jupiter:junit-jupiter-engine:" + jupiterVersion));
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
        }
        catch (final NumberFormatException e) {
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
