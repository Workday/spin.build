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
 * A {@link ModuleSystemException} thrown when a module could not be resolved using a {@link ModuleCatalog}.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class UnresolvableModuleException
    extends ModuleSystemException {

    /**
     * The {@link ModuleReference} which could not be resolved.
     */
    private final ModuleReference reference;

    /**
     * Constructs an {@link UnresolvableModuleException}.
     *
     * @param reference the {@link ModuleReference}
     */
    public UnresolvableModuleException(final ModuleReference reference) {
        super("The Artifact for Module ["
            + Objects.requireNonNull(reference, "The ModuleReference must not be null").name()
            + "] with version [" + reference.version() + "] could not be resolved.");

        this.reference = reference;
    }

    /**
     * Obtains the {@link ModuleReference} which could not be resolved.
     *
     * @return the {@link ModuleReference}
     */
    public ModuleReference getModuleReference() {
        return this.reference;
    }
}
