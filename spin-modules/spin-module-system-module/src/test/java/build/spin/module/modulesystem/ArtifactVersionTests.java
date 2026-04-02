package build.spin.module.modulesystem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Artifact.Version}s.
 * <p>
 * These tests are based on those defined by Apache Maven, in particular the
 * <a href="https://github.com/apache/maven/blob/master/maven-artifact/src/test/java/org/apache/maven/artifact/versioning/ComparableVersionTest.java">Comparable Version Tests</a>,
 * thus ensuring semantic compatibily with Maven Versioning.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
class ArtifactVersionTests {

    /**
     * In order {@link Artifact.Version}s to ensure order compliance.
     */
    private static final String[] VERSIONS =
        { "1-alpha2snapshot", "1-alpha2", "1-alpha-123", "1-beta-2", "1-beta123", "1-m2", "1-m11", "1-rc", "1-cr2",
            "1-rc123", "1-SNAPSHOT", "1", "1-sp", "1-sp2", "1-sp123", "1-abc", "1-def", "1-pom-1", "1-1-snapshot",
            "1-1", "1-2", "1-123", "2.0", "2-1", "2.0.a", "2.0.0.a", "2.0.2", "2.0.123", "2.1.0", "2.1-a", "2.1b",
            "2.1-c", "2.1-1", "2.1.0.1", "2.2", "2.123", "11.a2", "11.a11", "11.b2", "11.b11", "11.m2", "11.m11", "11",
            "11.a", "11b", "11c", "11m" };

    /**
     * Assert that two version strings are equal, reflexively and comparatively.
     *
     * @param v1 the first version
     * @param v2 the second version
     */
    private void assertEqual(final String v1, final String v2) {

        final Artifact.Version version1 = Artifact.Version.parse(v1);
        final Artifact.Version version2 = Artifact.Version.parse(v2);

        assertThat(version1).isEqualTo(version1);
        assertThat(version1).isEqualTo(version2);

        assertThat(version1).isEqualTo(version2);
        assertThat(version2).isEqualTo(version1);

        assertThat(version1.compareTo(version1)).isEqualTo(0);
        assertThat(version2.compareTo(version2)).isEqualTo(0);

        assertThat(version1.compareTo(version2)).isEqualTo(0);
        assertThat(version2.compareTo(version1)).isEqualTo(0);
    }

    /**
     * Assert that the first version is less than the second version
     *
     * @param v1 the first version
     * @param v2 the second version
     */
    private void assertLessThan(final String v1, final String v2) {

        final Artifact.Version version1 = Artifact.Version.parse(v1);
        final Artifact.Version version2 = Artifact.Version.parse(v2);

        assertThat(version1.compareTo(version2)).isLessThan(0);
        assertThat(version2.compareTo(version1)).isGreaterThan(0);
    }

    /**
     * Ensure parsed {@link Artifact.Version}s are equal.
     */
    @Test
    void shouldCompareEquivalentVersions() {
        assertEqual("1", "1");
        assertEqual("1", "1.0");
        assertEqual("1", "1.0.0");
        assertEqual("1.0", "1.0.0");
        assertEqual("1", "1-0");
        assertEqual("1", "1.0-0");
        assertEqual("1.0", "1.0-0");

        // when there's no separator between characters, we must create sub-versions
        assertEqual("1a", "1-a");
        assertEqual("1a", "1.0-a");
        assertEqual("1a", "1.0.0-a");
        assertEqual("1.0a", "1-a");
        assertEqual("1.0.0a", "1-a");
        assertEqual("1x", "1-x");
        assertEqual("1x", "1.0-x");
        assertEqual("1x", "1.0.0-x");
        assertEqual("1.0x", "1-x");
        assertEqual("1.0.0x", "1-x");

        // ensure aliases for releases are equivalent
        assertEqual("1ga", "1");
        assertEqual("1release", "1");
        assertEqual("1final", "1");
        assertEqual("1cr", "1rc");

        // sure aliases for a, b and m are equivalent to alpha, beta and milestone
        assertEqual("1a1", "1-alpha-1");
        assertEqual("1b2", "1-beta-2");
        assertEqual("1m3", "1-milestone-3");

        // ensure cases are ignored
        assertEqual("1X", "1x");
        assertEqual("1A", "1a");
        assertEqual("1B", "1b");
        assertEqual("1M", "1m");
        assertEqual("1Ga", "1");
        assertEqual("1GA", "1");
        assertEqual("1RELEASE", "1");
        assertEqual("1release", "1");
        assertEqual("1RELeaSE", "1");
        assertEqual("1Final", "1");
        assertEqual("1FinaL", "1");
        assertEqual("1FINAL", "1");
        assertEqual("1Cr", "1Rc");
        assertEqual("1cR", "1rC");
        assertEqual("1m3", "1Milestone3");
        assertEqual("1m3", "1MileStone3");
        assertEqual("1m3", "1MILESTONE3");
    }

    /**
     * Ensure non-equivalent parsed {@link Artifact.Version}s are comparable.
     */
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

        assertLessThan("1.0-alpha-1", "1.0");
        assertLessThan("1.0-alpha-1", "1.0-alpha-2");
        assertLessThan("1.0-alpha-1", "1.0-beta-1");

        assertLessThan("1.0-beta-1", "1.0-SNAPSHOT");
        assertLessThan("1.0-SNAPSHOT", "1.0");
        assertLessThan("1.0-alpha-1-SNAPSHOT", "1.0-alpha-1");

        assertLessThan("1.0", "1.0-1");
        assertLessThan("1.0-1", "1.0-2");
        assertLessThan("1.0.0", "1.0-1");

        assertLessThan("2.0-1", "2.0.1");
        assertLessThan("2.0.1-klm", "2.0.1-lmn");
        assertLessThan("2.0.1", "2.0.1-xyz");

        assertLessThan("2.0.1", "2.0.1-123");
        assertLessThan("2.0.1-xyz", "2.0.1-123");

        assertLessThan("1-0.alpha", "1-0.beta");
        assertLessThan("1-0.alpha", "1");
        assertLessThan("1-0.beta", "1");

        assertLessThan("6.1.0rc3", "6.1.0");
        assertLessThan("6.1.0rc3", "6.1H.5-beta");
        assertLessThan("6.1.0", "6.1H.5-beta");

        final String snapshotStyle = "20190126.230843";
        final String massiveStyle = "1234567890.12345";
        final String massiveWithQualifiers = "123456789012345.1H.5-beta";
        final String extraMassiveWithQualifiers = "12345678901234567890.1H.5-beta";

        assertLessThan(snapshotStyle, massiveStyle);
        assertLessThan(massiveStyle, massiveWithQualifiers);
        assertLessThan(snapshotStyle, massiveWithQualifiers);
        assertLessThan(massiveWithQualifiers, extraMassiveWithQualifiers);
        assertLessThan(massiveStyle, extraMassiveWithQualifiers);
        assertLessThan(snapshotStyle, extraMassiveWithQualifiers);
    }

    /**
     * Ensure defined {@link Artifact.Version}s are in order.
     */
    @Test
    void shouldCompareOrderedVersion() {
        for (int i = 0; i < VERSIONS.length - 1; i++) {
            assertLessThan(VERSIONS[i], VERSIONS[i + 1]);
        }
    }
}
