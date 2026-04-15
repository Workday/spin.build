package build.spin.module.modulesystem;

import build.base.version.Version;
import build.base.version.VersionBound;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link VersionBound}s.
 *
 * @since Dec-2020
 */
class ArtifactVersionBoundTests {

    @Test
    void shouldEvaluateBoundsPredicate() {

        final VersionBound strictlyAfter = VersionBound.lower(Version.parse("2.0"), false);

        assertThat(strictlyAfter.test(Version.parse("1.0"))).isFalse();
        assertThat(strictlyAfter.test(Version.parse("2.0"))).isFalse();
        assertThat(strictlyAfter.test(Version.parse("3.0"))).isTrue();
        assertThat(strictlyAfter.test(Version.parse("2.3.2.Final"))).isTrue();

        final VersionBound inclusivelyAfter = VersionBound.lower(Version.parse("2.0"), true);

        assertThat(inclusivelyAfter.test(Version.parse("1.0"))).isFalse();
        assertThat(inclusivelyAfter.test(Version.parse("2.0"))).isTrue();
        assertThat(inclusivelyAfter.test(Version.parse("3.0"))).isTrue();
        assertThat(inclusivelyAfter.test(Version.parse("2.3.2.Final"))).isTrue();

        final VersionBound strictlyBefore = VersionBound.upper(Version.parse("2.0"), false);

        assertThat(strictlyBefore.test(Version.parse("1.0"))).isTrue();
        assertThat(strictlyBefore.test(Version.parse("2.0"))).isFalse();
        assertThat(strictlyBefore.test(Version.parse("3.0"))).isFalse();
        assertThat(strictlyBefore.test(Version.parse("2.3.2.Final"))).isFalse();

        final VersionBound inclusivelyBefore = VersionBound.upper(Version.parse("2.0"), true);

        assertThat(inclusivelyBefore.test(Version.parse("1.0"))).isTrue();
        assertThat(inclusivelyBefore.test(Version.parse("2.0"))).isTrue();
        assertThat(inclusivelyBefore.test(Version.parse("3.0"))).isFalse();
        assertThat(inclusivelyBefore.test(Version.parse("2.3.2.Final"))).isFalse();
    }
}
