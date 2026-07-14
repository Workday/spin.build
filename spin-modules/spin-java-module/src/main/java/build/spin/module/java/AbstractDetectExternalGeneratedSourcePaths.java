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
import build.spin.common.task.DetectExternalGeneratedSourcePaths;
import jakarta.inject.Inject;

import java.nio.file.Path;

/**
 * Detects generated source {@link Path}s from any prior build, for use as compilation input.
 *
 * <p>Checks Maven's conventional location ({@code target/generated-sources/*}). Unlike
 * {@link AbstractDetectGeneratedSourcePaths}, spin's own annotation-processor output
 * ({@code .build/main/generated-sources}) is never surfaced here, since that content is
 * spin's own {@code -s} target and is not a separate, independently generated source root.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public abstract class AbstractDetectExternalGeneratedSourcePaths
    implements DetectExternalGeneratedSourcePaths {

    @Inject
    private Path projectPath;

    @Override
    public PathSet detect() {
        return AbstractDetectGeneratedSourcePaths.detectExternal(this.projectPath);
    }
}
