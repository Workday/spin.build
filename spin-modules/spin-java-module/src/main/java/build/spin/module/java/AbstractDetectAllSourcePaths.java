package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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
import build.spin.common.task.DetectAllSourcePaths;

import java.util.stream.Stream;

/**
 * Merges declared and annotation-processor generated source root directories into a single
 * {@link PathSet} for analysis consumers.
 *
 * @author reed.vonredwitz
 * @since May-2026
 */
public abstract class AbstractDetectAllSourcePaths
    implements DetectAllSourcePaths {

    @Override
    public PathSet detect(final PathSet declared, final PathSet generated) {
        return Stream.concat(declared.stream(), generated.stream()).collect(PathSet.collector());
    }
}
