package build.spin.engine;

/*-
 * #%L
 * Spin Engine
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

import build.base.configuration.Configuration;
import build.spin.Engine;
import build.spin.Project;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The default implementation of a {@link Project}.
 *
 * @author brian.oliver
 * @since Jun-2019
 */
public final class DefaultProject
    extends AbstractProject {

    /**
     * Constructs a {@link DefaultProject}.
     *
     * @param engine The {@link Engine}
     * @param parent the {@link Optional} parent {@link Project}
     * @param path the {@link Path} of the {@link Project}
     * @param optionsByType the {@link Configuration} for the {@link Project}
     */
    @Inject
    public DefaultProject(final Engine engine,
                          final Optional<Project> parent,
                          final Path path,
                          final Configuration optionsByType) {

        super(engine, parent, path, optionsByType);
    }
}
