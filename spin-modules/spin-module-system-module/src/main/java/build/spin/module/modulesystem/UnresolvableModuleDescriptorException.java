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
 * A {@link ModuleSystemException} thrown when a {@link build.codemodel.jdk.descriptor.JDKModuleDescriptor} for an {@link Artifact} can't be resolved
 * using a {@link Artifact.Resolver}.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class UnresolvableModuleDescriptorException
    extends ModuleSystemException {

    /**
     * The {@link Artifact} for which the {@link build.codemodel.jdk.descriptor.JDKModuleDescriptor} could not be resolved.
     */
    private final Artifact artifact;

    /**
     * Constructs an {@link UnresolvableModuleDescriptorException}.
     *
     * @param artifact the {@link Artifact}
     * @param cause the causing {@link Exception}
     */
    public UnresolvableModuleDescriptorException(final Artifact artifact, final Exception cause) {
        super("Failed to determine the Module Descriptor for the Artifact ["
            + Objects.requireNonNull(artifact, "The Artifact must not be null").toString()
            + "]", cause);

        this.artifact = artifact;
    }

    /**
     * Constructs an {@link UnresolvableModuleDescriptorException}.
     *
     * @param artifact the {@link Artifact}
     */
    public UnresolvableModuleDescriptorException(final Artifact artifact) {
        this(artifact, null);
    }

    /**
     * Obtains the {@link Artifact} for which the {@link build.codemodel.jdk.descriptor.JDKModuleDescriptor} was unresolvable.
     *
     * @return the {@link Artifact}
     */
    public Artifact getArtifact() {
        return this.artifact;
    }
}
