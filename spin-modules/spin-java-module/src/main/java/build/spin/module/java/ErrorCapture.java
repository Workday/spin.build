package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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

import build.base.flow.Consumer;
import build.spawn.application.option.StandardErrorSubscriber;

import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Captures stderr output from a subprocess for inclusion in a {@link build.spin.common.ProcessFailedException}.
 * Use {@link #subscriber(Consumer)} when the subscriber can log every line uniformly;
 * use {@link #append(String)} directly when the caller needs custom routing (e.g. error vs. warning triage).
 */
public final class ErrorCapture {

    private final StringBuilder buffer = new StringBuilder();

    /**
     * Returns a {@link StandardErrorSubscriber} that passes each line to {@code log} and appends it.
     * Callers typically pass {@code line -> this.recorder.error(line)}.
     */
    public StandardErrorSubscriber subscriber(final Consumer<String> log) {
        return StandardErrorSubscriber.of(line -> {
            log.onNext(line);
            append(line);
        });
    }

    /**
     * Returns {@code true} for javac stderr lines that are warnings rather than errors:
     * {@code ": warning:"} diagnostics and {@code "Note:"} informational lines.
     */
    public static boolean isJavacWarning(final String line) {
        return line.contains(": warning:") || line.startsWith("Note:");
    }

    /**
     * Returns {@code true} for JVM stderr lines that are warnings rather than errors:
     * blank lines and {@code "WARNING:"} prefixed lines.
     */
    public static boolean isJvmNoise(final String line) {
        return line.isBlank()
            || line.startsWith("WARNING:");
    }

    /**
     * Returns {@code true} for JVM stderr lines that are spawn agent lifecycle output and should
     * be silently discarded rather than logged at any level.
     */
    public static boolean isSpawnAgentOutput(final String line) {
        return line.startsWith("[SpawnAgent:");
    }

    /**
     * Returns {@code true} for {@code -Xlog:cds} warning lines emitted by {@code -Xshare:dump}
     * (e.g. {@code [0.123s][warning][cds] Skipping jdk/proxy2/$Proxy1: Unsupported location}).
     * These use a lowercase, timestamp-bracketed format that {@link #isJvmNoise} doesn't recognize;
     * dynamic-proxy and JFR-event classes are routinely and harmlessly ineligible for CDS archiving,
     * so every static dump logs a batch of them regardless of application correctness.
     */
    public static boolean isCdsDumpNoise(final String line) {
        return line.contains("[warning][cds]");
    }

    /**
     * Returns a {@link StandardErrorSubscriber} that routes each line to {@code warn} when
     * {@code isNoise} matches, or to {@code error} (and appends it) otherwise.
     */
    public StandardErrorSubscriber triageSubscriber(final Predicate<String> isNoise,
                                                    final Consumer<String> warn,
                                                    final Consumer<String> error) {
        return StandardErrorSubscriber.of(line -> {
            if (isNoise.test(line)) {
                warn.onNext(line);
            } else {
                error.onNext(line);
                append(line);
            }
        });
    }

    /**
     * Returns {@code stderr} when non-empty, otherwise joins {@code stdout} lines with newlines.
     * Use when a tool (e.g. jdeps) may write error messages to stdout rather than stderr.
     */
    public static String selectOutput(final String stderr, final Stream<String> stdout) {
        return stderr.isEmpty() ? stdout.collect(Collectors.joining("\n")) : stderr;
    }

    /**
     * Appends {@code text} to the captured output, inserting a newline separator when non-empty.
     */
    public void append(final String text) {
        if (!this.buffer.isEmpty()) {
            this.buffer.append('\n');
        }
        this.buffer.append(text);
    }

    /**
     * Returns all captured output as a single string.
     */
    public String output() {
        return this.buffer.toString();
    }
}
