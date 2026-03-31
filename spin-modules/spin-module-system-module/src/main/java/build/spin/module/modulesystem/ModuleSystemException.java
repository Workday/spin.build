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
 * A {@link RuntimeException} thrown when interacting with a <i>Module System</i>.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class ModuleSystemException
    extends RuntimeException {

    /**
     * Constructs a new {@link ModuleSystemException} without a specified message and causing {@link Throwable}.
     */
    protected ModuleSystemException() {
        super();
    }

    /**
     * Constructs a new {@link ModuleSystemException} with the specified message,
     * without a causing {@link Throwable}.
     *
     * @param message the message
     */
    public ModuleSystemException(final String message) {
        super(message);
    }

    /**
     * Constructs a new {@link ModuleSystemException} with the specified message and causing {@link Throwable}.
     *
     * @param message the detail message
     * @param cause the optional causing {@link Throwable}
     */
    public ModuleSystemException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new runtime exception with the specified cause and a
     * detail message of {@code (cause==null ? null : cause.toString())}
     * (which typically contains the class and detail message of
     * {@code cause}).  This constructor is useful for runtime exceptions
     * that are little more than wrappers for other throwables.
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public ModuleSystemException(final Throwable cause) {
        super(cause);
    }

    /**
     * Obtain the {@link Optional}ly provided {@link Throwable} causing the {@link ModuleSystemException}.
     *
     * @return the {@link Optional} causing {@link Throwable}
     */
    public Optional<Throwable> cause() {
        return Optional.ofNullable(getCause());
    }
}
