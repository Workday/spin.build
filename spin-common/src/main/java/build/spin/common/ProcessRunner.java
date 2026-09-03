package build.spin.common;

/*-
 * #%L
 * Spin Common
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

import build.spawn.application.Application;

import java.util.function.Supplier;

/**
 * Waits for a launched {@link Application} to exit, uniformly wrapping both a failed/interrupted
 * wait and a non-zero exit code as a {@link ProcessFailedException} carrying {@code output}. Every
 * subprocess-driving task (javac, javadoc, jlink, jdeps, JUnit, Checkstyle, gpg, ...) funnels its
 * "wait for the tool, then fail loudly if it didn't succeed" step through here, so a wait that
 * itself fails (e.g. an interrupt) can no longer escape uncaught. Lives in {@code spin-common}
 * (alongside {@link ProcessFailedException}) rather than any one module, since every module that
 * launches a subprocess via {@code LocalMachine} already depends on {@code spin-common}.
 */
public final class ProcessRunner {

    private ProcessRunner() {
    }

    /**
     * @param process the launched process to wait on
     * @param label   identifies the tool in thrown {@link ProcessFailedException} messages,
     *                e.g. {@code "Compilation"} or {@code "jlink"}
     * @param output  supplies the captured output to attach to a failure; invoked only when the
     *                process actually failed, so callers may flush buffered output lazily here
     * @throws ProcessFailedException if the wait fails/is interrupted, or the process exits non-zero
     */
    public static void await(final Application process, final String label, final Supplier<String> output) {
        try {
            process.onExit().get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessFailedException(label + " Execution Failed", output.get(), e);
        } catch (final Exception e) {
            throw new ProcessFailedException(label + " Execution Failed", output.get(), e);
        }

        final int exitValue = process.exitValue().orElse(0);
        if (exitValue != 0) {
            throw new ProcessFailedException(label + " Failed (exit code: " + exitValue + ")", output.get());
        }
    }
}
