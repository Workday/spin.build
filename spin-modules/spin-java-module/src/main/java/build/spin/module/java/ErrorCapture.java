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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Captures stderr output from a subprocess for inclusion in a {@link build.spin.common.ProcessFailedException}.
 * Use {@link #subscriber(Consumer)} when the subscriber can log every line uniformly;
 * use {@link #append(String)} directly when the caller needs custom routing (e.g. error vs. warning triage).
 */
public final class ErrorCapture {

    private static final Predicate<String> FAILURES_HEADER =
        Pattern.compile("^Failures \\(\\d+\\):$").asMatchPredicate();

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
     * <p>
     * Also matches {@code "... VM warning: JVMCI Compiler disabled due to -Xint"} — a GraalVM-only
     * line: {@code -Xshare:dump} implies {@code -Xint}, and GraalVM notes that its JVMCI-based JIT is
     * therefore inactive. The dump never compiles anything by design, so this is expected.
     */
    public static boolean isCdsDumpNoise(final String line) {
        return line.contains("[warning][cds]")
            || line.contains("VM warning: JVMCI Compiler disabled due to -Xint");
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
     * Extracts the {@code Failures (N):} section from JUnit {@code ConsoleLauncher} stdout — the
     * block naming each failed test and its exception — discarding the full pass/fail tree and any
     * telemetry the tests themselves printed to stdout. Blank lines separating individual failures
     * are preserved (runs of them collapsed to one); leading and trailing blank lines are trimmed.
     * Returns {@code stdout} unchanged when no such section is present.
     */
    public static String junitFailures(final String stdout) {
        final String[] lines = stdout.split("\n", -1);

        final int start = failuresHeaderLine(lines);
        if (start < 0) {
            return stdout;
        }

        int end = lines.length;
        for (int i = start + 1; i < lines.length; i++) {
            if (lines[i].startsWith("Test run finished")) {
                end = i;
                break;
            }
        }

        return Stream.of(lines).skip(start).limit(end - start)
            .map(String::stripTrailing)
            .collect(Collectors.joining("\n"))
            .strip()
            .replaceAll("\n{3,}", "\n\n");
    }

    private static int failuresHeaderLine(final String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (FAILURES_HEADER.test(lines[i].stripTrailing())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds the failure message for a non-zero JUnit {@code ConsoleLauncher} exit: the trimmed
     * {@link #junitFailures(String) failure summary} from {@code stdout} followed by whatever was
     * captured on {@code stderr}, so the named failures survive even when the run also wrote to
     * stderr. Either part is omitted when empty; both empty yields an empty string.
     *
     * <p>When {@code stdout} has no {@code Failures (N):} section — a launch error, a bad argument,
     * a crash before any test ran — the real diagnostic is on {@code stderr}, so that is returned
     * alone rather than prefixed with the entire console tree. Only when {@code stderr} is also
     * empty does the unparsed {@code stdout} become the message, as a last resort.
     */
    public static String junitFailureReport(final String stderr, final String stdout) {
        if (failuresHeaderLine(stdout.split("\n", -1)) < 0 && !stderr.isEmpty()) {
            return stderr;
        }
        return Stream.of(junitFailures(stdout), stderr)
            .filter(part -> !part.isEmpty())
            .collect(Collectors.joining("\n\n"));
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
