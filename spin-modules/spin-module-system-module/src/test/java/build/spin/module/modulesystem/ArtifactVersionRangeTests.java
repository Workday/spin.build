package build.spin.module.modulesystem;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Tests for {@link Artifact.Version.Range}s.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
public class ArtifactVersionRangeTests {

    /**
     * Some {@link Artifact.Version} constants for testing.
     */
    private static final Artifact.Version v0_0 = Artifact.Version.parse("0.0");
    private static final Artifact.Version v1_0_SNAPSHOT = Artifact.Version.parse("1.0-SNAPSHOT");
    private static final Artifact.Version v1_0 = Artifact.Version.parse("1.0");
    private static final Artifact.Version v2_0 = Artifact.Version.parse("2.0");
    private static final Artifact.Version v3_0 = Artifact.Version.parse("3.0");

    /**
     * Ensure standard Maven Version Ranges can be parsed.
     */
    @Test
    void shouldParseRanges() {
        final Stream<String> ranges = Stream.of("1.0", "[1.0]", "[1.2,1.3]", "[1.0,2.0)", "[1.5,)", "(,1.0]",
            "(,1.0],[1.2,)", "(,1.1),(1.1,)", "1.0.0-SNAPSHOT");

        ranges.forEach(Artifact.Version.Range::parse);
    }

    /**
     * Ensure {@link Artifact.Version.Range}s of the form {@code v} and {@code [v]} contains a {@link Artifact.Version}.
     */
    @Test
    void shouldVersionBasedRangeContainsVersion() {

        final Artifact.Version.Range softRange = Artifact.Version.Range.parse("1.0");

        assertThat(softRange.test(v0_0), is(false));
        assertThat(softRange.test(v1_0_SNAPSHOT), is(false));
        assertThat(softRange.test(v1_0), is(true));
        assertThat(softRange.test(v2_0), is(false));
        assertThat(softRange.test(v3_0), is(false));

        final Artifact.Version.Range hardRange = Artifact.Version.Range.parse("[1.0]");

        assertThat(hardRange.test(v0_0), is(false));
        assertThat(hardRange.test(v1_0_SNAPSHOT), is(false));
        assertThat(hardRange.test(v1_0), is(true));
        assertThat(hardRange.test(v2_0), is(false));
        assertThat(hardRange.test(v3_0), is(false));
    }

    /**
     * Ensure a {@link Artifact.Version.Range} of the form {@code [v,)} contains a {@link Artifact.Version}.
     */
    @Test
    void shouldInclusiveAfterRangeContainAVersion() {

        final Artifact.Version.Range range = Artifact.Version.Range.parse("[1.0,)");

        assertThat(range.test(v0_0), is(false));
        assertThat(range.test(v1_0_SNAPSHOT), is(false));
        assertThat(range.test(v1_0), is(true));
        assertThat(range.test(v2_0), is(true));
        assertThat(range.test(v3_0), is(true));
    }

    /**
     * Ensure a {@link Artifact.Version.Range} of the form {@code (,v]} contains a {@link Artifact.Version}.
     */
    @Test
    void shouldInclusiveBeforeRangeContainAVersion() {

        final Artifact.Version.Range range = Artifact.Version.Range.parse("(,1.0]");

        assertThat(range.test(v0_0), is(true));
        assertThat(range.test(v1_0_SNAPSHOT), is(true));
        assertThat(range.test(v1_0), is(true));
        assertThat(range.test(v2_0), is(false));
        assertThat(range.test(v3_0), is(false));
    }

    /**
     * Ensure a {@link Artifact.Version.Range} of the form {@code [x, y]} contains a {@link Artifact.Version}.
     */
    @Test
    void shouldInclusiveAfterAndBeforeRangeContainAVersion() {

        final Artifact.Version.Range range = Artifact.Version.Range.parse("[1.0,2.0]");

        assertThat(range.test(v0_0), is(false));
        assertThat(range.test(v1_0_SNAPSHOT), is(false));
        assertThat(range.test(v1_0), is(true));
        assertThat(range.test(v2_0), is(true));
        assertThat(range.test(v3_0), is(false));
    }

    /**
     * Ensure a {@link Artifact.Version.Range} of the form {@code [x, y)} contains a {@link Artifact.Version}.
     */
    @Test
    void shouldInclusiveAfterAndStrictlyBeforeRangeContainAVersion() {

        final Artifact.Version.Range range = Artifact.Version.Range.parse("[1.0,2.0)");

        assertThat(range.test(v0_0), is(false));
        assertThat(range.test(v1_0_SNAPSHOT), is(false));
        assertThat(range.test(v1_0), is(true));
        assertThat(range.test(v2_0), is(false));
        assertThat(range.test(v3_0), is(false));
    }

    /**
     * Ensure a {@link Artifact.Version.Range} of the form {@code (,x], [y,)} contains a {@link Artifact.Version}.
     */
    @Test
    void shouldInclusiveBeforeAndAfterRangeContainAVersion() {

        final Artifact.Version.Range range = Artifact.Version.Range.parse("(,1.0],[3.0,)");

        assertThat(range.test(v0_0), is(true));
        assertThat(range.test(v1_0_SNAPSHOT), is(true));
        assertThat(range.test(v1_0), is(true));
        assertThat(range.test(v2_0), is(false));
        assertThat(range.test(v3_0), is(true));
    }

    /**
     * Ensure a {@link Artifact.Version.Range} of the form {@code (,x], [y,)} contains a {@link Artifact.Version}.
     */
    @Test
    void shouldStrictlyBeforeAndAfterRangeContainAVersion() {

        final Artifact.Version.Range range = Artifact.Version.Range.parse("(,1.0),(2.0,)");

        assertThat(range.test(v0_0), is(true));
        assertThat(range.test(v1_0_SNAPSHOT), is(true));
        assertThat(range.test(v1_0), is(false));
        assertThat(range.test(v2_0), is(false));
        assertThat(range.test(v3_0), is(true));
    }
}
