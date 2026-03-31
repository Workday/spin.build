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

import java.util.Objects;

/**
 * A {@link ModuleSystemException} thrown when a cyclic dependency is detected between two {@link ModuleDescriptor}s.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class CyclicDependencyException
    extends ModuleSystemException {

    /**
     * The {@link ModuleDescriptor} in which the cyclic dependency is defined.
     */
    private final ModuleDescriptor descriptor;

    /**
     * The {@link ModuleDescriptor} causing the cycle.
     */
    private final ModuleDescriptor causing;

    /**
     * Constructs a {@link CyclicDependencyException}.
     *
     * @param descriptor the {@link ModuleDescriptor} containing the cyclic dependency
     * @param causing the {@link ModuleDescriptor} causing the cycle
     */
    public CyclicDependencyException(final ModuleDescriptor descriptor, final ModuleDescriptor causing) {
        super("Detected a cyclic dependency between Modules ["
            + Objects.requireNonNull(descriptor, "The ModuleDescriptor must not be null").name()
            + "] and ["
            + Objects.requireNonNull(causing, "The ModuleDescriptor causing the cycle must not be null").name()
            + "]");

        this.descriptor = descriptor;
        this.causing = causing;
    }

    /**
     * Obtains the {@link ModuleDescriptor} containing the cyclic dependency.
     *
     * @return the {@link ModuleDescriptor}
     */
    public ModuleDescriptor getDescriptor() {
        return this.descriptor;
    }

    /**
     * Obtains the {@link ModuleDescriptor} causing the cycle.
     *
     * @return the causing {@link ModuleDescriptor}
     */
    public ModuleDescriptor getCausing() {
        return this.causing;
    }
}
