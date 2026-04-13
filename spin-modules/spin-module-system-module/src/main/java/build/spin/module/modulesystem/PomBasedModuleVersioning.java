package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
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
import build.codemodel.injection.PostInject;
import build.spin.Project;
import build.spin.Resource;
import build.spin.Workspace;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link ModuleVersioning} {@link Resource} that derives dependency versions by parsing the
 * {@code pom.xml} files present in a Maven workspace, for use when no {@code version.properties}
 * is available.
 * <p>
 * Workspace-walking and transitive-dependency traversal is delegated to {@link PomWorkspaceWalker};
 * this class only provides the visitor that records each discovered coordinate's version under
 * every JPMS module-name candidate.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public class PomBasedModuleVersioning
    implements ModuleVersioning, Resource {

    private static final String POM_FILENAME = "pom.xml";
    private static final String VERSION_PROPERTIES_FILENAME = "version.properties";

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private Project project;

    private ModuleVersioning versioning;

    @PostInject
    private void onInjected() {
        final Path localRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        this.versioning = buildFromWorkspace(this.project.path(), localRepo, this.recorder);
    }

    /**
     * Builds a {@link ModuleVersioning} from a Maven workspace. Package-private to allow direct
     * testing without the DI container.
     */
    static ModuleVersioning buildFromWorkspace(final Path workspacePath, final TelemetryRecorder recorder) {
        final Path localRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        return buildFromWorkspace(workspacePath, localRepo, recorder);
    }

    /**
     * Builds a {@link ModuleVersioning} from a Maven workspace using the given local repository
     * root. Package-private to allow direct testing with a temporary local repository.
     */
    static ModuleVersioning buildFromWorkspace(final Path workspacePath,
                                               final Path localRepo,
                                               final TelemetryRecorder recorder) {
        final Map<String, ModuleDescriptor.Version> versions = new LinkedHashMap<>();

        PomWorkspaceWalker.walk(workspacePath, localRepo, recorder,
            (names, groupId, artifactId, rawVersion) -> {
                try {
                    final ModuleDescriptor.Version version = ModuleDescriptor.Version.parse(rawVersion);
                    names.forEach(name -> versions.putIfAbsent(name, version));
                } catch (final Exception e) {
                    recorder.warn(e, "PomBasedModuleVersioning failed to parse version [%s] for [%s:%s]",
                        rawVersion, groupId, artifactId);
                }
            });

        recorder.diagnostic("PomBasedModuleVersioning loaded %d version mappings for [%s]",
            versions.size(), workspacePath.getFileName());
        return new MapBackedVersioning(versions);
    }

    @Override
    public Optional<ModuleDescriptor.Version> getVersion(final String moduleName) {
        return this.versioning.getVersion(moduleName);
    }

    @Override
    public Optional<ModuleDescriptor.Version> getVersion(final ModuleDescriptor descriptor) {
        return this.versioning.getVersion(descriptor);
    }

    /**
     * The {@link Resource.MetaClass} for {@link PomBasedModuleVersioning}.
     */
    public static class MetaClass
        implements Resource.MetaClass {

        @Override
        public boolean isWorkspace(final Path path) {
            return PomXmlUtils.isMavenWorkspaceRoot(path)
                && !Files.exists(path.resolve(VERSION_PROPERTIES_FILENAME));
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            return project instanceof Workspace
                && Files.exists(project.path().resolve(POM_FILENAME))
                && !Files.exists(project.path().resolve(VERSION_PROPERTIES_FILENAME));
        }
    }

    /**
     * Simple {@link ModuleVersioning} backed by a {@link Map}.
     */
    private static final class MapBackedVersioning implements ModuleVersioning {

        private final Map<String, ModuleDescriptor.Version> versions;

        private MapBackedVersioning(final Map<String, ModuleDescriptor.Version> versions) {
            this.versions = versions;
        }

        @Override
        public Optional<ModuleDescriptor.Version> getVersion(final String moduleName) {
            return Optional.ofNullable(this.versions.get(moduleName));
        }

        @Override
        public Optional<ModuleDescriptor.Version> getVersion(final ModuleDescriptor descriptor) {
            return descriptor == null ? Optional.empty() : getVersion(descriptor.name());
        }
    }
}
