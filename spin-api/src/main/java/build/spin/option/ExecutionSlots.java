package build.spin.option;

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

import build.base.commandline.CommandLine;
import build.base.configuration.AbstractValueOption;
import build.base.configuration.Default;
import build.base.configuration.Option;

import java.util.Optional;

/**
 * An {@link Option} bounding how many {@link build.spin.Task}s a {@link build.spin.Program} may execute
 * concurrently, independent of how many are simultaneously ready in the dependency graph.
 *
 * <p>Most tasks launch a real CPU-bound JDK tool (compile, link, dependency analysis); running more of
 * those at once than there are cores oversubscribes the host. The value defaults to
 * {@link Runtime#availableProcessors()} — a rough proxy, since many tasks spend their slot largely
 * waiting on a JDK-tool subprocess that is itself multi-core.
 *
 * <p>When no {@link ExecutionSlots} option is configured, {@link #autodetect()} additionally consults the
 * {@code spin.execution.slots} system property (a positive integer), letting a build cap spin's host
 * footprint more tightly than one-slot-per-core without a full {@code .spin/} configuration. A property
 * that is set but not a positive integer is a configuration error and fails the build rather than being
 * silently ignored.
 *
 * @author reed.vonredwitz
 * @since Sep-2026
 */
public class ExecutionSlots
    extends AbstractValueOption<Integer> {

    /**
     * The text for the command line option.
     */
    public static final String OPTION = "--execution-slots";

    /**
     * The system property consulted by {@link #autodetect()} when no {@link ExecutionSlots} option is
     * otherwise configured.
     */
    public static final String PROPERTY = "spin.execution.slots";

    /**
     * Constructs an {@link ExecutionSlots}.
     *
     * @param slots the number of execution slots
     */
    private ExecutionSlots(final Integer slots) {
        super(slots);
    }

    /**
     * Creates an {@link ExecutionSlots} for the specified slot count.
     *
     * @param slots the number of execution slots (a positive integer)
     * @return an {@link ExecutionSlots}
     * @throws IllegalArgumentException if {@code slots} is not a positive integer
     */
    @CommandLine.Prefix(OPTION)
    @CommandLine.Description("Maximum number of Tasks that may execute their body concurrently")
    public static ExecutionSlots of(final Integer slots) {
        if (slots == null || slots <= 0) {
            throw new IllegalArgumentException(
                "The execution-slot count must be a positive integer, but was: " + slots);
        }
        return new ExecutionSlots(slots);
    }

    /**
     * Auto-detects an {@link ExecutionSlots}: the {@code spin.execution.slots} system property when set,
     * otherwise {@link Runtime#availableProcessors()}.
     *
     * @return an {@link ExecutionSlots}
     * @throws IllegalArgumentException if the {@code spin.execution.slots} property is set but is not a
     *     positive integer
     */
    @Default
    public static ExecutionSlots autodetect() {
        return fromSystemProperty()
            .orElseGet(() -> new ExecutionSlots(Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Reads the {@link #PROPERTY} system property into an {@link ExecutionSlots}, if it is set.
     *
     * @return the configured {@link ExecutionSlots}, or {@link Optional#empty()} if the property is unset
     *     or blank
     * @throws IllegalArgumentException if the property is set but is not a positive integer
     */
    private static Optional<ExecutionSlots> fromSystemProperty() {
        return Optional.ofNullable(System.getProperty(PROPERTY))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> {
                try {
                    return of(Integer.parseInt(value));
                } catch (final IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "The " + PROPERTY + " system property must be a positive integer, but was: "
                            + value, e);
                }
            });
    }
}
