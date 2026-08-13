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

import build.base.telemetry.TelemetryRecorder;
import build.base.version.Version;
import build.codemodel.foundation.descriptor.RequiresModuleDescriptor;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;

import java.util.Optional;

/**
 * Shared catalog-wins-over-bytecode-hint version resolution for a single {@code requires} clause,
 * used by both {@link AbstractDetectResolution#resolveExternalArtifact} (the compile/module-path
 * resolution path) and {@link AbstractJavaDependencyAnalysis#resolveRequiredVersion} (the jdeps
 * analysis path).
 * <p>
 * A {@code requires} clause read from an already-published dependency's compiled
 * {@code module-info.class} can carry a {@code compiledVersion} hint — whatever version was on that
 * dependency's own module path back when <em>it</em> was compiled. That hint is frozen in the jar
 * forever; it does not track this workspace's own, current {@code version.properties}/pom-declared
 * version for the same module. The workspace-wide {@link ModuleVersioning} catalog (walked fresh
 * from every {@code pom.xml}/{@code version.properties}) is therefore always preferred over the
 * bytecode hint, with a divergence between the two surfaced via {@code recorder} rather than
 * silently resolved to the stale hint.
 */
final class RequiredVersionResolution {

    private RequiredVersionResolution() {
    }

    /**
     * Resolves the {@link Version} to use for {@code r}, preferring {@code versioning}'s catalog
     * entry over any compiled-version hint on the clause itself, and warning when the two disagree.
     *
     * @param r           the {@code requires} clause
     * @param moduleName  {@code r}'s required module name
     * @param contextName the name of the module/project declaring {@code r}, for diagnostics
     * @param versioning  the workspace-wide {@link ModuleVersioning}
     * @param recorder    the {@link TelemetryRecorder} for diagnostics
     * @return the resolved {@link Version}, if the catalog or the bytecode hint has one
     */
    static Optional<Version> resolve(final RequiresModuleDescriptor r,
                                     final String moduleName,
                                     final String contextName,
                                     final ModuleVersioning versioning,
                                     final TelemetryRecorder recorder) {

        final Optional<Version> moduleVersion = versioning.getVersion(moduleName);
        final Optional<Version> requiresVersion = JDKModuleDescriptor.requiresVersion(r);

        if (moduleVersion.isPresent()) {
            if (requiresVersion.isPresent() && !requiresVersion.get().equals(moduleVersion.get())) {
                recorder.warn(
                    "Require [%s] in [%s] declares version [%s] but the workspace ModuleVersioning "
                        + "catalog resolved [%s] — using the catalog version",
                    moduleName, contextName, requiresVersion.get(), moduleVersion.get());
            }
            return moduleVersion;
        }

        return requiresVersion;
    }
}
