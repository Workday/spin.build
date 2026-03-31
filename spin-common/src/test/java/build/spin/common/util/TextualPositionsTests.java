package build.spin.common.util;

import build.base.telemetry.TextualPosition;
import build.base.telemetry.TextualRange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TextualPositions}.
 *
 * @author ben.horowitz
 * @since Feb-2023
 */
class TextualPositionsTests {

    @Test
    void shouldFormatPosition() {
        final var position = TextualPosition.create(Paths.get("/foo/bar/baz").toUri(), 2, 3);

        final var result = TextualPositions.format(position, Paths.get("/foo/bar"));

        assertEquals("baz:2:3", result);
    }

    @Test
    void shouldExtract()
        throws IOException {

        final var dir = Files.createTempDirectory(getClass().getSimpleName());

        final var path = dir.resolve("foo");

        final var contents = """
            foo
            bar
            baz""";

        Files.writeString(path, contents, StandardCharsets.UTF_8);

        final var position = TextualPosition.create(path.toUri(), 2, 3);

        final var result = TextualPositions.extractLine(position);

        assertEquals("bar", result);
    }

    @Test
    void shouldPointToPosition() {
        final var position = TextualPosition.create(Paths.get("/foo").toUri(), 2, 3);
        final var result = TextualPositions.pointTo(position);

        assertEquals("  ^", result);
    }

    @Test
    void shouldHUnderlineRange() {
        final Path path = Paths.get("/foo");

        final var start = TextualPosition.create(path.toUri(), 2, 3);
        final var end = TextualPosition.create(path.toUri(), 2, 5);
        final var range = TextualRange.create(start, end);
        final var result = TextualPositions.underline(range);

        assertEquals("  ===", result);

        final var end2 = TextualPosition.create(path.toUri(), 3, 5); // different line number
        final var range2 = TextualRange.create(start, end2);
        final var result2 = TextualPositions.underline(range2);

        assertEquals("  ^", result2);
    }
}
