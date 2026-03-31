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

import java.util.Optional;

/**
 * Provides the ability to resolve a required {@link ModuleDescriptor.Version} for a module.
 *
 * @author brian.oliver
 * @since May-2020
 */
public interface ModuleVersioning {

    /**
     * Attempts to obtain the {@link ModuleDescriptor.Version} for the specified module name.
     *
     * @param moduleName the module name
     * @return {@link Optional} {@link ModuleDescriptor.Version}
     */
    Optional<ModuleDescriptor.Version> getVersion(String moduleName);

    /**
     * Attempts to obtain the {@link ModuleDescriptor.Version} for the specified {@link ModuleDescriptor}.
     *
     * @param descriptor the {@link ModuleDescriptor}
     * @return {@link Optional} {@link ModuleDescriptor.Version}
     */
    Optional<ModuleDescriptor.Version> getVersion(ModuleDescriptor descriptor);
}
