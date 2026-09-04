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

import build.base.configuration.ConfigurationBuilder;
import build.base.flow.Subscriber;
import build.base.flow.Subscription;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.jdk.JDK;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.spi.ToolProvider;

/**
 * Resolves and runs JDK command-line tools ({@code javac}, {@code javadoc}, {@code jlink},
 * {@code jdeps}, {@code java}, ...) under a JDK or runtime image's {@code bin/} directory.
 * <p>
 * Two ways to run a tool are supported: forked, as an {@link Executable} ready to add to a launch
 * {@link build.base.configuration.ConfigurationBuilder}, or in-process via {@link ToolProvider} —
 * see {@link #canRunInProcess(JDK)} — which avoids the cost of forking a whole child JVM (own
 * GC/JIT thread pools, startup cost) for what is otherwise a single-threaded, single-invocation
 * tool run.
 */
public final class JDKTools {

    private JDKTools() {
    }

    /**
     * @param home the JDK or runtime image home directory (e.g. {@code JDKHome#path()})
     * @param tool the tool name, e.g. {@code "javac"}
     * @return an {@link Executable} pointing at {@code home/bin/tool}
     */
    public static Executable executable(final Path home, final String tool) {
        return Executable.of(home.resolve("bin/" + tool).toString());
    }

    /**
     * Determines whether {@code jdk}'s tools can run in-process via {@link ToolProvider} instead of being
     * forked as a child JVM.
     * <p>
     * Resolving a {@link JDK} by major version alone is not sufficient: JDK resolution picks a JDK from
     * every installation discovered on the host, independent of which JDK is actually running this Spin
     * process — two installs can share a major version without being the same install. Running a tool
     * in-process is only valid when {@code jdk}'s home is the exact installation running this JVM.
     *
     * @param jdk the resolved {@link JDK}
     * @return {@code true} if tools from {@code jdk} can safely run in-process
     */
    public static boolean canRunInProcess(final JDK jdk) {
        try {
            final Path resolvedHome = jdk.home().path().toRealPath();
            final Path runningHome = Path.of(System.getProperty("java.home")).toRealPath();
            return resolvedHome.equals(runningHome);
        } catch (final IOException e) {
            return false;
        }
    }

    /**
     * Runs {@code toolName} in-process, dispatching both stdout and stderr lines to the same
     * {@code subscriber} — mirrors a forked invocation whose stdout and stderr are wired to one observer.
     *
     * @param toolName      the {@link ToolProvider} name (e.g. {@code "javac"})
     * @param subscriber    receives every stdout/stderr line, combined
     * @param configuration the {@link Argument}s to run the tool with — the same {@link ConfigurationBuilder}
     *                      used to launch the tool as a forked {@link build.spawn.application.Application}, so
     *                      argument-building never diverges between the in-process and forked code paths
     * @return the tool's exit code
     */
    public static int runInProcess(final String toolName, final Subscriber<? super String> subscriber,
                                   final ConfigurationBuilder configuration) {
        return runInProcess(toolName, subscriber, subscriber, configuration);
    }

    /**
     * Runs {@code toolName} in-process, dispatching stdout lines to {@code out} and stderr lines to
     * {@code err} separately.
     *
     * @param toolName      the {@link ToolProvider} name (e.g. {@code "jdeps"}, {@code "jlink"})
     * @param out           receives stdout lines
     * @param err           receives stderr lines
     * @param configuration the {@link Argument}s to run the tool with — the same {@link ConfigurationBuilder}
     *                      used to launch the tool as a forked {@link build.spawn.application.Application}, so
     *                      argument-building never diverges between the in-process and forked code paths
     * @return the tool's exit code
     */
    public static int runInProcess(final String toolName, final Subscriber<? super String> out,
                                   final Subscriber<? super String> err,
                                   final ConfigurationBuilder configuration) {

        final String[] arguments = configuration.stream(Argument.class)
            .map(Argument::get)
            .toArray(String[]::new);

        // LineDispatchingWriter.close() dispatches the trailing partial line and onComplete(); it never
        // does I/O, so this try-with-resources cannot throw — hence no checked IOException on this method
        try (
            LineDispatchingWriter outWriter = new LineDispatchingWriter(out);
            LineDispatchingWriter errWriter = new LineDispatchingWriter(err)) {

            return tool(toolName).run(new PrintWriter(outWriter), new PrintWriter(errWriter), arguments);
        }
    }

    private static ToolProvider tool(final String toolName) {
        return ToolProvider.findFirst(toolName)
            .orElseThrow(() -> new IllegalStateException("No in-process \"" + toolName + "\" ToolProvider available"));
    }

    /**
     * A {@link Writer} that buffers characters written to it and dispatches each completed line to a
     * {@link Subscriber}, mirroring how a forked child process's stdout/stderr is line-dispatched via
     * {@code StandardErrorSubscriber} — used so the same line-based telemetry parsing applies whether a JDK
     * tool runs in-process via {@link ToolProvider} or as a forked subprocess.
     */
    private static final class LineDispatchingWriter
        extends Writer {

        private final Subscriber<? super String> subscriber;

        private final StringBuilder buffer = new StringBuilder();

        LineDispatchingWriter(final Subscriber<? super String> subscriber) {
            this.subscriber = subscriber;
            // most Subscribers (e.g. RecordingSubscriber) reject onNext() until subscribed — there is
            // no real Publisher here, so perform that handshake ourselves with an unbounded, no-op
            // Subscription, mirroring what a forked process's line-publishing Publisher does before
            // ever calling onNext()
            this.subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long number) {
                    // unbounded from the start; nothing further to request
                }

                @Override
                public void cancel() {
                    // no upstream production to cancel
                }
            });
        }

        @Override
        public void write(final char[] chars, final int offset, final int length) {
            for (int i = offset; i < offset + length; i++) {
                final char c = chars[i];
                if (c == '\n') {
                    this.subscriber.onNext(this.buffer.toString());
                    this.buffer.setLength(0);
                } else if (c != '\r') {
                    this.buffer.append(c);
                }
            }
        }

        @Override
        public void flush() {
            // lines are dispatched as they complete; nothing to flush mid-line
        }

        @Override
        public void close() {
            if (!this.buffer.isEmpty()) {
                this.subscriber.onNext(this.buffer.toString());
                this.buffer.setLength(0);
            }
            this.subscriber.onComplete();
        }
    }
}
