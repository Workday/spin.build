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
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link ModuleCatalog} {@link Resource} that derives module-to-artifact mappings by parsing
 * the {@code pom.xml} files present in a Maven workspace, for use when no
 * {@code module-catalog.properties} is available.
 * <p>
 * Workspace-walking and transitive-dependency traversal is delegated to {@link PomWorkspaceWalker};
 * this class only provides the visitor that turns each discovered coordinate into an
 * {@link Artifact.Constraint} registered under every JPMS module-name candidate.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public class PomBasedModuleCatalog
    implements ModuleCatalog, Resource {

    private static final String POM_FILENAME = "pom.xml";
    private static final String MODULE_CATALOG_FILENAME = ProjectModuleCatalog.MODULE_CATALOG_FILENAME;

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private Project project;

    private ModuleCatalog catalog;

    @PostInject
    private void onInjected() {
        final Path localRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        this.catalog = buildFromWorkspace(this.project.path(), localRepo, this.recorder);
    }

    /**
     * Builds a {@link ModuleCatalog} from a Maven workspace. Package-private to allow direct
     * testing without the DI container.
     */
    static ModuleCatalog buildFromWorkspace(final Path workspacePath, final TelemetryRecorder recorder) {
        final Path localRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        return buildFromWorkspace(workspacePath, localRepo, recorder);
    }

    /**
     * Builds a {@link ModuleCatalog} from a Maven workspace using the given local repository root.
     * Package-private to allow direct testing with a temporary local repository.
     */
    static ModuleCatalog buildFromWorkspace(final Path workspacePath,
                                            final Path localRepo,
                                            final TelemetryRecorder recorder) {
        final ModuleCatalog result = ModuleCatalog.HeapBased.create();

        PomWorkspaceWalker.walk(workspacePath, localRepo, recorder,
            (names, groupId, artifactId, version) -> {
                try {
                    final Artifact.Constraint constraint = Artifact.Constraint.of(
                        Artifact.create(groupId, artifactId, version, "jar"));
                    names.forEach(name -> result.add(name, constraint));
                } catch (final Exception e) {
                    recorder.warn(e, "PomBasedModuleCatalog failed to register [%s:%s:%s]",
                        groupId, artifactId, version);
                }
            });

        recorder.diagnostic("PomBasedModuleCatalog loaded catalog for [%s]", workspacePath.getFileName());
        return result;
    }

    @Override
    public ModuleCatalog add(final String moduleName, final Artifact.Constraint constraint) {
        return this.catalog.add(moduleName, constraint);
    }

    @Override
    public Stream<Artifact.Constraint> constraints(final String moduleName) {
        return this.catalog.constraints(moduleName);
    }

    @Override
    public Optional<ModuleReference> getModuleReference(final Artifact artifact) {
        return this.catalog.getModuleReference(artifact);
    }

    /**
     * The {@link Resource.MetaClass} for {@link PomBasedModuleCatalog}.
     */
    public static class MetaClass
        implements Resource.MetaClass {

        @Override
        public boolean isWorkspace(final Path path) {
            return Files.exists(path.resolve("mvnw"))
                && Files.exists(path.resolve(POM_FILENAME))
                && !Files.exists(path.resolve(MODULE_CATALOG_FILENAME));
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            return project instanceof Workspace
                && Files.exists(project.path().resolve(POM_FILENAME))
                && !Files.exists(project.path().resolve(MODULE_CATALOG_FILENAME));
        }
    }
}
