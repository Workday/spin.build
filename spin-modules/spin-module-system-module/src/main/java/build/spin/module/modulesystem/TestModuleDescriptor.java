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

import build.spin.Project;
import build.spin.Resource;

/**
 * A {@link Resource} that provides additional {@link ModuleDescriptor} requires for test compilation,
 * for use when the project does not define a {@code src/test/java/module-info.java}.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public interface TestModuleDescriptor
    extends Resource {

    /**
     * Returns the {@link ModuleDescriptor} containing the requires to be included for test compilation
     * for the specified {@link Project}.
     *
     * @param project the {@link Project} for which to determine test requires
     * @return the {@link ModuleDescriptor}
     */
    ModuleDescriptor get(Project project);
}
