package build.spin;

/*-
 * #%L
 * Spin API
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
 * An {@link Exception} thrown when executing a {@link Task} failed for a {@link Program}.
 *
 * @author brian.oliver
 * @since Nov-2019
 */
public class ProgramExecutionException
    extends Exception {

    /**
     * The {@link Program}.
     */
    private final Program program;

    /**
     * The {@link Reference} for the {@link Task} that failed.
     */
    private final Reference reference;

    /**
     * Constructs a {@link ProgramExecutionException}.
     *
     * @param program the {@link Program}
     * @param reference the {@link Reference}
     * @param message the message
     */
    public ProgramExecutionException(final Program program,
        final Reference reference,
        final String message) {
            
        this(program, reference, message, null);
    }

    /**
     * Constructs a {@link ProgramExecutionException}.
     *
     * @param program the {@link Program}
     * @param reference the {@link Reference}
     * @param throwable the {@link Throwable} causing the failure
     */
    public ProgramExecutionException(final Program program,
        final Reference reference,
        final Throwable throwable) {

        this(program, reference, null, throwable);
    }

    /**
     * Constructs a {@link ProgramExecutionException}.
     *
     * @param program the {@link Program}
     * @param reference the {@link Reference}
     * @param message the message
     * @param throwable the {@link Throwable} causing the failure
     */
    public ProgramExecutionException(final Program program,
        final Reference reference,
        final String message,
        final Throwable throwable) {

        super(message, throwable);
        this.program = Objects.requireNonNull(program, "The Program must not be null");
        this.reference = Objects.requireNonNull(reference, "The Task.Reference must not be null");
    }

    /**
     * Obtains the {@link Reference} for the {@link Task} that failed.
     *
     * @return the {@link Reference}
     */
    public Reference getReference() {
        return this.reference;
    }

    /**
     * Obtains the {@link Program} for which the {@link ProgramExecutionException} occurred.
     * 
     * @return the {@link Program}
     */
    public Program program() {
        return this.program;
    }
}
