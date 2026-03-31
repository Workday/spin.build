package build.spin.common.task;

/*-
 * #%L
 * Spin Common Library
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

import build.base.io.PathSet;
import build.spin.Project;
import build.spin.Task;

import java.nio.file.Path;

/**
 * A {@link Task} that detects the root {@link Path}s for source in a {@link Project}.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
public interface DetectSourcePaths
    extends Task<PathSet> {

    /**
     * Creates a {@link PathSet} containing the root {@link Path}s for source in a {@link Project}.
     *
     * @return the {@link PathSet}
     */
    PathSet detect();
}
