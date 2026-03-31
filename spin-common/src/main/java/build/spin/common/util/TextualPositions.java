package build.spin.common.util;

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

import build.base.telemetry.TextualPosition;
import build.base.telemetry.TextualRange;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;

/**
 * Helper methods for working with {@link TextualPosition}, {@link TextualRange}, etc.
 *
 * @author ben.horowitz
 * @since Feb-2023
 */
public class TextualPositions {

    private TextualPositions() {
    }

    /**
     * Formats a {@link TextualPosition} relative to another path.
     *
     * <p>For example, if {@code position} is file {@code /foo/bar/baz} at line 2, column 3, and {@code relativeTo} is
     * {@code /foo/bar}, then the return value would be "baz:2:3".
     *
     * @param position   a position in a file
     * @param relativeTo the path to which {@code position}'s path is relative
     * @return a formatted string containing a file name, a line, and a column
     */
    public static String format(final TextualPosition position, final Path relativeTo) {
        final var path = relativeTo.relativize(Path.of(position.uri()));

        return "%s:%d:%d".formatted(path, position.line(), position.position());
    }

    /**
     * Returns a single line, given by a {@link TextualPosition#line()}, read {@link TextualPosition#uri()}.
     *
     * @param position the position
     * @return the line
     * @throws IOException propagated from read calls of the file, or if file contained fewer than
     *                     {@link TextualPosition#line()} lines.
     */
    public static String extractLine(final TextualPosition position)
        throws IOException {

        try (InputStream is = position.uri().toURL().openStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            InputStreamReader isr = new InputStreamReader(bis);
            BufferedReader reader = new BufferedReader(isr)) {

            String line = null;

            for (int i = 0; i < position.line() && (i == 0 || line != null); i++) {
                line = reader.readLine();
            }

            if (line == null) {
                final var message = "could not read line %d from %s: did it contain fewer than %d lines?"
                    .formatted(position.line(), position.uri(), position.line());
                throw new IOException(message);
            }

            return line;
        }
    }

    /**
     * Returns a visual indication of a character position in a line.
     *
     * @param position the position
     * @return a visual indication of a character position in a line
     */
    public static String pointTo(final TextualPosition position) {
        final var pos = position.position();
        final var prefix = pos == 0 ? "" : " ".repeat(position.position() - 1);
        return prefix + "^";
    }

    /**
     * Returns an underlined range of characters in a line.
     *
     * @param range the range of characters
     * @return an underlined range of characters
     */
    public static String underline(final TextualRange range) {
        final var start = range.start();
        final var end = range.end();

        if (start.line() != end.line()) {
            // no point in underlining multiple lines; use pointTo instead
            return pointTo(start);
        }

        final var prefix = start.position() == 0 ? "" : " ".repeat(start.position() - 1);

        final var underline = "=".repeat(end.position() - start.position() + 1);

        return prefix + underline;
    }
}
