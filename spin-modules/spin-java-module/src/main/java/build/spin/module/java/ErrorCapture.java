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
