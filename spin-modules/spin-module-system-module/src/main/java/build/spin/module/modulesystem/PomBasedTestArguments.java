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
import build.spin.module.modulesystem.pom.GA;
import build.spin.module.modulesystem.pom.Plugin;
import build.spin.module.modulesystem.pom.PomReader;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A {@link TestArguments} {@link Resource} that derives test JVM arguments from the
 * {@code maven-surefire-plugin} {@code <argLine>} declared in a project's {@code pom.xml}, with
 * full parent-pom inheritance and {@code <pluginManagement>} merging.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public class PomBasedTestArguments
    implements TestArguments, Resource {

    private static final String POM_FILENAME = "pom.xml";
    private static final GA SUREFIRE = new GA("org.apache.maven.plugins", "maven-surefire-plugin");

    @Inject
    private TelemetryRecorder recorder;

    private PomReader pomReader;

    @PostInject
    private void onInjected() {
        final Path localRepository = Path.of(System.getProperty("user.home"), ".m2", "repository");
        this.pomReader = new PomReader(localRepository, this.recorder);
    }

    @Override
    public Stream<String> get(final Project project) {
        return this.pomReader.read(project.path().resolve(POM_FILENAME))
            .flatMap(pom -> pom.plugin(SUREFIRE))
            .map(Plugin::configuration)
            .flatMap(c -> c.textChild("argLine"))
            .stream()
            .flatMap(raw -> Stream.of(raw.trim().split("\\s+")))
            .filter(s -> !s.isEmpty());
    }

    /**
     * The {@link Resource.MetaClass} for {@link PomBasedTestArguments}.
     */
    public static class MetaClass
        implements Resource.MetaClass {

        @Override
        public boolean isWorkspace(final Path path) {
            return PomXmlUtils.isMavenWorkspaceRoot(path)
                && !PomXmlUtils.isSpinNativeWorkspace(path);
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            return project instanceof Workspace
                && Files.exists(project.path().resolve(POM_FILENAME))
                && !PomXmlUtils.isSpinNativeWorkspace(project.path());
        }
    }
}
