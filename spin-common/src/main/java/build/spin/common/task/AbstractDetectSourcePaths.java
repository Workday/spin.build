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
import build.base.option.JDKVersion;
import build.spin.annotation.System;
import build.spin.option.BuildDirectoryName;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Detects the root directories for whichever {@link SourcePathKind}s a {@link build.spin.Plugin}
 * is responsible for, deferring the detection logic for each kind to {@link SourcePathKind#detect}.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public abstract class AbstractDetectSourcePaths
    implements DetectSourcePaths {

    @Inject
    private Path projectPath;

    @Inject
    @System
    private JDKVersion defaultJavaVersion;

    @Inject
    private JDKVersion javaVersion;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    /**
     * The {@link SourcePathKind}s this {@link build.spin.Plugin} is responsible for detecting.
     *
     * @return the {@link SourcePathKind}s to detect
     */
    protected abstract Set<SourcePathKind> kinds();

    @Override
    public Map<SourcePathKind, PathSet> detect() {
        final Map<SourcePathKind, PathSet> result = new EnumMap<>(SourcePathKind.class);
        kinds().forEach(kind -> result.put(kind,
            kind.detect(this.projectPath, this.defaultJavaVersion, this.javaVersion, this.buildDirectoryName.get())));
        return result;
    }
}
