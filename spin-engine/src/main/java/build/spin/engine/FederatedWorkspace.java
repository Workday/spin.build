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
 * A {@link Workspace} federating multiple physical root {@link build.spin.Project} trees under a single,
 * synthetic parentless root.
 * <p>
 * Unlike {@link DefaultWorkspace}, a {@link FederatedWorkspace} is never itself detected on the file system —
 * it declares no {@link build.spin.Plugin}s or {@link build.spin.Resource}s of its own. Each physical root is
 * instead discovered as an ordinary child {@link build.spin.Project} (via the same discovery used for any
 * nested {@link build.spin.Project}), which means every {@link build.spin.Project} in every physical root
 * shares this {@link Workspace} as the terminus of its parent chain, and {@link build.spin.Project#stream()}
 * called on this {@link Workspace} yields the union of every root's tree.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public final class FederatedWorkspace
    extends AbstractProject
    implements Workspace {

    /**
     * Constructs a {@link FederatedWorkspace}.
     *
     * @param engine        the {@link Engine}
     * @param path          a synthetic {@link Path} for the {@link FederatedWorkspace} (the common ancestor of
     *                      its physical roots)
     * @param optionsByType the {@link Configuration} for the {@link FederatedWorkspace}
     */
    @Inject
    public FederatedWorkspace(final Engine engine,
                              final Path path,
                              final Configuration optionsByType) {

        super(engine, Optional.empty(), path, optionsByType);
    }
}
