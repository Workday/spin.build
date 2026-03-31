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
 * A {@link ModuleSystemException} thrown when a {@link ModuleReference} is expected to have a
 * {@link ModuleDescriptor.Version}, but it is missing.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class MissingModuleVersionException
    extends ModuleSystemException {

    /**
     * The {@link ModuleReference} for which the {@link ModuleDescriptor.Version} is missing.
     */
    private final ModuleReference reference;

    /**
     * Constructs an {@link MissingModuleVersionException}.
     *
     * @param reference the {@link ModuleReference}
     */
    public MissingModuleVersionException(final ModuleReference reference) {
        super("The Module Descriptor for Module ["
            + Objects.requireNonNull(reference, "The ModuleReference must not be null").name()
            + "] is missing version information");

        if (reference.version().isPresent()) {
            throw new IllegalArgumentException("The provided module [" + reference.name() + "] "
                + "unexpectedly has version [" + reference.version().get() + "]");
        }

        this.reference = reference;
    }

    /**
     * Obtains the {@link ModuleReference} that is missing {@link ModuleDescriptor.Version} information.
     *
     * @return the {@link ModuleReference}
     */
    public ModuleReference getReference() {
        return this.reference;
    }
}
