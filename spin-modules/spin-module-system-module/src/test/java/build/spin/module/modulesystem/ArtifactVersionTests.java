package build.spin.module.modulesystem;

import build.base.version.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Version} ordering within the spin artifact-resolution context.
 *
 * @since Dec-2020
 */
class ArtifactVersionTests {

    private void assertEqual(final String v1, final String v2) {
        final Version version1 = Version.parse(v1);
        final Version version2 = Version.parse(v2);
        assertThat(version1.compareTo(version2)).isEqualTo(0);
        assertThat(version2.compareTo(version1)).isEqualTo(0);
    }

    private void assertLessThan(final String v1, final String v2) {
        final Version version1 = Version.parse(v1);
        final Version version2 = Version.parse(v2);
        assertThat(version1.compareTo(version2)).isLessThan(0);
        assertThat(version2.compareTo(version1)).isGreaterThan(0);
    }

    @Test
    void shouldCompareEquivalentVersions() {
        assertEqual("1", "1");
        assertEqual("1", "1.0");
        assertEqual("1", "1.0.0");
        assertEqual("1.0", "1.0.0");
    }

    @Test
    void shouldCompareNonEquivalentVersions() {
        assertLessThan("1", "2");
        assertLessThan("1.5", "2");
        assertLessThan("1", "2.5");
        assertLessThan("1.0", "1.1");
        assertLessThan("1.1", "1.2");
        assertLessThan("1.0.0", "1.1");
        assertLessThan("1.0.1", "1.1");
        assertLessThan("1.1", "1.2.0");
    }

    @Test
    void shouldTreatPrereleaseAsLessThanRelease() {
        // no-prerelease > has-prerelease
        assertLessThan("1.0-alpha-1", "1.0");
        assertLessThan("1.0-SNAPSHOT", "1.0");
        assertLessThan("1.0-alpha-1", "1.0-alpha-2");
        assertLessThan("1.0-alpha-1", "1.0-beta-1");
    }
}
