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
 * A {@link Task} that detects generated source root directories produced by tools other than an
 * annotation processor (e.g. protobuf, ANTLR, JAXB) from a prior build, for use as compilation
 * input.
 *
 * <p>Unlike {@link DetectGeneratedSourcePaths}, this task's result is always fed into compilation:
 * nothing else recompiles this content, whether or not an annotation processor is also active.
 *
 * @author reed.vonredwitz
 * @see DetectGeneratedSourcePaths
 * @since Jul-2026
 */
public interface DetectExternalGeneratedSourcePaths
    extends Task<PathSet> {

    /**
     * Creates a {@link PathSet} containing the generated source root directories for a project.
     *
     * @return the {@link PathSet}
     */
    PathSet detect();
}
