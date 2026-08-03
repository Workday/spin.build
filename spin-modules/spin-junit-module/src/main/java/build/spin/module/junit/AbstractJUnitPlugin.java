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
import build.codemodel.dependency.injection.Binder;
import build.codemodel.dependency.injection.Provides;
import build.codemodel.foundation.CodeModel;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spin.Project;
import build.spin.common.task.SourcePathKind;
import build.spin.module.java.AbstractDetectResolution;
import build.spin.module.java.AbstractJavaPlugin;
import build.spin.module.java.JavaCompilerPlugin;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.module.modulesystem.TestModuleDescriptor;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An abstract {@link JUnitPlugin} for Java-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public abstract class AbstractJUnitPlugin
    extends AbstractJavaPlugin
    implements JUnitPlugin {

    @Inject
    private CodeModel codeModel;

    private final AtomicReference<JDKModuleDescriptor> testModuleDescriptor = new AtomicReference<>();

    /**
     * The {@link JDKVersion} for the {@link JUnitPlugin}.
     */
    private final JDKVersion javaVersion;

    /**
     * Constructs an {@link AbstractJUnitPlugin}.
     *
     * @param javaVersion the {@link JDKVersion}
     */
    protected AbstractJUnitPlugin(final JDKVersion javaVersion) {
        this.javaVersion = javaVersion;
    }

    @Override
    public JDKVersion getJavaVersion() {
        return this.javaVersion;
    }

    @Override
    @Provides
    protected SourcePathKind sourceScope() {
        return SourcePathKind.TEST;
    }

    /**
     * Contributes the JUnit Platform ConsoleLauncher and Jupiter engine — spin's own test-runner
     * infrastructure that projects are not expected to declare themselves — to the {@code Set<Artifact>}
     * multibinding {@link AbstractDetectResolution} resolves alongside every other candidate, so there's
     * one authoritative module-path/classpath split with no duplication.
     *
     * @param binder the {@link Binder} to contribute bindings to
     */
    @Override
    public void contributeBindings(final Binder binder) {
        final String jupiterVersion = jupiterVersion(this.versioning);
        final String platformVersion = derivePlatformVersion(jupiterVersion);

        binder.bindSet(Artifact.class)
            .add(Artifact.parse("org.junit.platform:junit-platform-console:" + platformVersion))
            .add(Artifact.parse("org.junit.jupiter:junit-jupiter-engine:" + jupiterVersion));
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

    @Override
    @Provides
    public JDKModuleDescriptor getModuleDescriptor() {
        return this.testModuleDescriptor.updateAndGet(descriptor -> {
            if (descriptor == null) {
                final JDKModuleDescriptor base = super.getModuleDescriptor();
                // Use of() rather than createModuleDescriptor() so this test-augmented view does not
                // overwrite the canonical descriptor already registered by the compiler plugin's parse().
                final JDKModuleDescriptor merged = JDKModuleDescriptor.of(this.codeModel, base.moduleName());
                merged.include(base);

                this.project.findResource(TestModuleDescriptor.class)
                    .ifPresent(res -> merged.include(res.get(this.project)));

                this.project.plugins(JavaCompilerPlugin.class)
                    .filter(plugin -> plugin.getJavaVersion().major() == getJavaVersion().major())
                    .findFirst()
                    .ifPresent(plugin -> merged.include(plugin.getModuleDescriptor()));

                return merged;
            }
            return descriptor;
        });
    }

}
