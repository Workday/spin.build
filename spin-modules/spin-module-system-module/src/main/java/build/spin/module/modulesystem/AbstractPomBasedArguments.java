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
import build.spin.module.modulesystem.pom.ConfigNode;
import build.spin.module.modulesystem.pom.GA;
import build.spin.module.modulesystem.pom.Plugin;
import build.spin.module.modulesystem.pom.PomReader;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Shared base for {@link Resource}s that derive CLI-argument tokens from a single plugin's
 * {@code <configuration>} block in a project's {@code pom.xml}. Concrete subclasses declare the
 * target plugin GA and the config → token mapping; this class owns the {@link PomReader}
 * lifecycle and the uniform {@code get(Project)} flow.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public abstract class AbstractPomBasedArguments
    implements Resource {

    private static final String POM_FILENAME = "pom.xml";

    @Inject
    private TelemetryRecorder recorder;

    private PomReader pomReader;

    @PostInject
    private void onInjected() {
        final Path localRepository = Path.of(System.getProperty("user.home"), ".m2", "repository");
        this.pomReader = new PomReader(localRepository, this.recorder);
    }

    /**
     * The Maven plugin whose {@code <configuration>} block this resource reads.
     */
    protected abstract GA pluginGA();

    /**
     * Maps the plugin's effective {@code <configuration>} tree to the CLI argument tokens this
     * resource produces.
     */
    protected abstract Stream<String> toArgs(ConfigNode configuration);

    /**
     * Reads the project's effective pom, locates {@link #pluginGA()}, and streams tokens from
     * {@link #toArgs(ConfigNode)}. Returns an empty stream when the pom is missing, the plugin is
     * absent, or the mapping produces nothing.
     */
    public Stream<String> get(final Project project) {
        return this.pomReader.read(project.path().resolve(POM_FILENAME))
            .flatMap(pom -> pom.plugin(pluginGA()))
            .map(Plugin::configuration)
            .map(this::toArgs)
            .orElseGet(Stream::empty);
    }
}
