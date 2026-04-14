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

import build.spawn.jdk.option.ClassPath;
import build.spin.Task;
import build.spin.module.modulesystem.CompilationResolution;

/**
 * An abstract {@link Task} that projects the {@code -classpath} entries out of a
 * {@link CompilationResolution} produced by {@link AbstractDetectResolution}.
 *
 * <p>Concrete subclasses are thin inner classes of the enclosing plugin that wire the
 * {@link CompilationResolution} source via {@code @From}.
 */
public abstract class AbstractDetectClassPath
    implements Task<ClassPath> {

    /**
     * Projects the classpath entries from the given {@link CompilationResolution}.
     *
     * @param resolution the {@link CompilationResolution}
     * @return the {@link ClassPath}
     */
    protected ClassPath project(final CompilationResolution resolution) {
        return ClassPath.of(resolution.classPath().stream());
    }
}
