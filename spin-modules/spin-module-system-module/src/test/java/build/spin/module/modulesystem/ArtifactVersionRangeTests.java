package build.spin.module.modulesystem;

import build.base.version.Version;
import build.base.version.VersionConstraint;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link VersionConstraint}s.
 *
 * @since Dec-2020
 */
public class ArtifactVersionRangeTests {

    private static final Version v0_0 = Version.parse("0.0");
    private static final Version v1_0_SNAPSHOT = Version.parse("1.0-SNAPSHOT");
    private static final Version v1_0 = Version.parse("1.0");
    private static final Version v2_0 = Version.parse("2.0");
    private static final Version v3_0 = Version.parse("3.0");

    @Test
    void shouldParseRanges() {
        final Stream<String> ranges = Stream.of("1.0", "[1.0]", "[1.2,1.3]", "[1.0,2.0)", "[1.5,)", "(,1.0]",
            "(,1.0],[1.2,)", "(,1.1),(1.1,)", "1.0.0-SNAPSHOT");

        ranges.forEach(VersionConstraint::parse);
    }

    @Test
    void shouldVersionBasedRangeContainsVersion() {

        final VersionConstraint softRange = VersionConstraint.parse("1.0");

        assertThat(softRange.test(v0_0)).isFalse();
        assertThat(softRange.test(v1_0_SNAPSHOT)).isFalse();
        assertThat(softRange.test(v1_0)).isTrue();
        assertThat(softRange.test(v2_0)).isFalse();
        assertThat(softRange.test(v3_0)).isFalse();

        final VersionConstraint hardRange = VersionConstraint.parse("[1.0]");

        assertThat(hardRange.test(v0_0)).isFalse();
        assertThat(hardRange.test(v1_0_SNAPSHOT)).isFalse();
        assertThat(hardRange.test(v1_0)).isTrue();
        assertThat(hardRange.test(v2_0)).isFalse();
        assertThat(hardRange.test(v3_0)).isFalse();
    }

    @Test
    void shouldInclusiveAfterRangeContainAVersion() {

        final VersionConstraint range = VersionConstraint.parse("[1.0,)");

        assertThat(range.test(v0_0)).isFalse();
        assertThat(range.test(v1_0_SNAPSHOT)).isFalse();
        assertThat(range.test(v1_0)).isTrue();
        assertThat(range.test(v2_0)).isTrue();
        assertThat(range.test(v3_0)).isTrue();
    }

    @Test
    void shouldInclusiveBeforeRangeContainAVersion() {

        final VersionConstraint range = VersionConstraint.parse("(,1.0]");

        assertThat(range.test(v0_0)).isTrue();
        assertThat(range.test(v1_0_SNAPSHOT)).isTrue();
        assertThat(range.test(v1_0)).isTrue();
        assertThat(range.test(v2_0)).isFalse();
        assertThat(range.test(v3_0)).isFalse();
    }

    @Test
    void shouldInclusiveAfterAndBeforeRangeContainAVersion() {

        final VersionConstraint range = VersionConstraint.parse("[1.0,2.0]");

        assertThat(range.test(v0_0)).isFalse();
        assertThat(range.test(v1_0_SNAPSHOT)).isFalse();
        assertThat(range.test(v1_0)).isTrue();
        assertThat(range.test(v2_0)).isTrue();
        assertThat(range.test(v3_0)).isFalse();
    }

    @Test
    void shouldInclusiveAfterAndStrictlyBeforeRangeContainAVersion() {

        final VersionConstraint range = VersionConstraint.parse("[1.0,2.0)");

        assertThat(range.test(v0_0)).isFalse();
        assertThat(range.test(v1_0_SNAPSHOT)).isFalse();
        assertThat(range.test(v1_0)).isTrue();
        assertThat(range.test(v2_0)).isFalse();
        assertThat(range.test(v3_0)).isFalse();
    }

    @Test
    void shouldInclusiveBeforeAndAfterRangeContainAVersion() {

        final VersionConstraint range = VersionConstraint.parse("(,1.0],[3.0,)");

        assertThat(range.test(v0_0)).isTrue();
        assertThat(range.test(v1_0_SNAPSHOT)).isTrue();
        assertThat(range.test(v1_0)).isTrue();
        assertThat(range.test(v2_0)).isFalse();
        assertThat(range.test(v3_0)).isTrue();
    }

    @Test
    void shouldStrictlyBeforeAndAfterRangeContainAVersion() {

        final VersionConstraint range = VersionConstraint.parse("(,1.0),(2.0,)");

        assertThat(range.test(v0_0)).isTrue();
        assertThat(range.test(v1_0_SNAPSHOT)).isTrue();
        assertThat(range.test(v1_0)).isFalse();
        assertThat(range.test(v2_0)).isFalse();
        assertThat(range.test(v3_0)).isTrue();
    }
}
