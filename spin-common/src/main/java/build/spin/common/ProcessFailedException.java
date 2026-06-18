package build.spin.common;

/*-
 * #%L
 * Spin Common Library
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

/**
 * Thrown when an external process (javac, javadoc, jlink, jdeps, etc.) exits with a non-zero
 * exit code. Carries the process's stderr output separately from the exception message so that
 * callers can surface it in the right place without embedding newlines in the cause chain.
 */
public class ProcessFailedException
    extends RuntimeException {

    private final String output;

    public ProcessFailedException(final String message, final String output) {
        super(message);
        this.output = output;
    }

    public ProcessFailedException(final String message, final String output, final Throwable cause) {
        super(message, cause);
        this.output = output;
    }

    /**
     * Returns the captured stderr output from the failed process, or an empty string if none.
     */
    public String output() {
        return this.output;
    }

    /**
     * Walks the cause chain of {@code throwable} and returns the first {@link ProcessFailedException}
     * found, or {@code throwable} itself if none is present. Use this when constructing a wrapper
     * exception to ensure the root process failure is not buried under generic {@link RuntimeException}
     * wrappers.
     */
    public static Throwable unwrap(final Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ProcessFailedException) {
                return current;
            }
        }
        return throwable;
    }
}
