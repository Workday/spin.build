package build.spin.common.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Globs}s.
 *
 * @author brian.oliver
 * @since 2022
 */
class GlobsTests {

    /**
     * Ensure Glob-pattern matching is successful for specific {@link Path} patterns.
     */
    @Test
    void shouldMatchGlobs() {
        final String glob = "**/project/**";

        final Pattern pattern = Globs.toPattern(glob);

        final Path path = Paths.get("/user/fred/code/spin/src/main/resource/project/foo/bar");

        assertThat(pattern.asMatchPredicate().test(path.toString())).isTrue();
    }

    /**
     * Ensure Glob-pattern matching is not successful for specific {@link Path} patterns.
     */
    @Test
    void shouldNotMatchGlobs() {
        final String glob = "project/";

        final Pattern pattern = Globs.toPattern(glob);

        final Path path = Paths.get("/user/fred/code/spin/src/main/resource/project/foo/bar");

        assertThat(pattern.asMatchPredicate().test(path.toString())).isFalse();
    }
}
