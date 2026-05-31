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

/**
 * A {@link Task} that detects all source files in a {@link Project} — both declared and
 * annotation-processor generated — for use by analysis consumers such as javadoc.
 *
 * @author reed.vonredwitz
 * @see DetectSourceFiles
 * @see DetectAllSourcePaths
 * @since May-2026
 */
public interface DetectAllSourceFiles
    extends Task<PathSet> {

    /**
     * Creates a {@link PathSet} containing all source files for a {@link Project}.
     *
     * @param pathSet the {@link PathSet} of source root directories to search
     * @return the {@link PathSet}
     */
    PathSet detect(PathSet pathSet);
}
