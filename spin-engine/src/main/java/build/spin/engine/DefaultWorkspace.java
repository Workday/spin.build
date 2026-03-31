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
import build.spin.Workspace;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The default implementation of a {@link Workspace}.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public final class DefaultWorkspace
    extends AbstractProject
    implements Workspace {

    /**
     * Constructs a {@link DefaultWorkspace}.
     *
     * @param engine The {@link Engine}
     * @param path the {@link Path} of the {@link Workspace}
     * @param optionsByType the {@link Configuration} for the {@link Workspace}
     */
    @Inject
    public DefaultWorkspace(final Engine engine,
                            final Path path,
                            final Configuration optionsByType) {

        super(engine, Optional.empty(), path, optionsByType);
    }
}
