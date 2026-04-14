package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
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

import java.nio.file.Path;
import java.util.List;

/**
 * The result of source-graph dependency resolution: a partition of all candidate jars and
 * compiled-class directories into those that belong on {@code --module-path} and those that
 * belong on {@code -classpath}.
 *
 * <p>Produced by {@code AbstractDetectResolution} (one instance per plugin scope) and
 * consumed by the two thin projection tasks {@code AbstractDetectModulePath} and
 * {@code AbstractDetectClassPath}.
 */
public record CompilationResolution(List<Path> modulePath, List<Path> classPath) {

    public CompilationResolution {
        modulePath = List.copyOf(modulePath);
        classPath  = List.copyOf(classPath);
    }
}
