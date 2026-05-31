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
import build.spin.Task;

/**
 * A {@link Task} that merges declared and annotation-processor generated source root directories
 * into a single {@link PathSet} for use by analysis consumers.
 *
 * <p>Unlike {@link DetectSourcePaths}, this task is safe to use in analysis-only contexts because
 * it never participates in compilation.
 *
 * @author reed.vonredwitz
 * @since May-2026
 * @see DetectSourcePaths
 * @see DetectAllSourceFiles
 */
public interface DetectAllSourcePaths
    extends Task<PathSet> {

    /**
     * Creates a {@link PathSet} containing all source root directories for a project — both
     * declared and annotation-processor generated.
     *
     * @param declared  the {@link PathSet} of declared source root directories
     * @param generated the {@link PathSet} of annotation-processor generated source root directories
     * @return the merged {@link PathSet}
     */
    PathSet detect(PathSet declared, PathSet generated);
}
