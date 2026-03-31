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
 * A {@link ModuleSystemException} thrown when a resource could not be resolved by a {@link Artifact.Resolver}.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class UnresolvableResourceException
    extends ModuleSystemException {

    /**
     * The coordinates of the resource which could not be resolved.
     */
    private final String coordinates;

    /**
     * Constructs an {@link UnresolvableResourceException}.
     *
     * @param coordinates the coordinates of the resource
     * @param cause the causing {@link Throwable}
     */
    public UnresolvableResourceException(final String coordinates,
                                         final Throwable cause) {

        super("Failed to resolve the resource ["
            + Objects.requireNonNull(coordinates, "The coordinates must not be null")
            + "]", cause);

        this.coordinates = coordinates.trim();
    }

    /**
     * Constructs an {@link UnresolvableResourceException}.
     *
     * @param coordinates the coordinates of the resource
     */
    public UnresolvableResourceException(final String coordinates) {
        this(coordinates, null);
    }

    /**
     * Obtains the coordinates of the resource that was unresolvable.
     *
     * @return the unresolvable coordinates
     */
    public String getCoordinates() {
        return this.coordinates;
    }
}
