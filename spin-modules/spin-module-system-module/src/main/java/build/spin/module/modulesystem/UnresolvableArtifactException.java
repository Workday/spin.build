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
 * A {@link ModuleSystemException} thrown when an {@link Artifact} could not be resolved by a {@link Artifact.Resolver}.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public class UnresolvableArtifactException
    extends UnresolvableResourceException {

    /**
     * The {@link Artifact} for which the resource could not be resolved.
     */
    private final Artifact artifact;

    /**
     * Constructs an {@link UnresolvableArtifactException}.
     *
     * @param artifact the {@link Artifact}
     * @param cause the causing {@link Throwable}
     */
    public UnresolvableArtifactException(final Artifact artifact,
                                         final Throwable cause) {

        super("Failed to resolve the resource ["
            + Objects.requireNonNull(artifact, "The Artifact must not be null")
            + "]", cause);

        this.artifact = artifact;
    }

    /**
     * Constructs an {@link UnresolvableArtifactException}.
     *
     * @param artifact the {@link Artifact}
     */
    public UnresolvableArtifactException(final Artifact artifact) {
        this(artifact, null);
    }

    /**
     * Obtains the {@link Artifact} for which the resource was unresolvable.
     *
     * @return the unresolvable {@link Artifact}
     */
    public Artifact getArtifact() {
        return this.artifact;
    }
}
