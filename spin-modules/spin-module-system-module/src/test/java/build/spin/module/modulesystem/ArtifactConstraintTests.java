package build.spin.module.modulesystem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Artifact.Constraint}s.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
class ArtifactConstraintTests {

    /**
     * Ensure a simple Version-based Maven Artifact Constraint can be parsed.
     */
    @Test
    void shouldParseAVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse("com.workday.quark:quark-core:1.0");

        assertThat(constraint.groupId()).isEqualTo("com.workday.quark");
        assertThat(constraint.artifactId()).isEqualTo("quark-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isFalse();
        assertThat(constraint.range()).isEqualTo(Artifact.Version.Range.parse("1.0"));
    }

    /**
     * Ensure a SNAPSHOT Version-based Maven Artifact Constraint can be parsed.
     */
    @Test
    void shouldParseASnapshotVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse(
            "com.workday.quark:quark-core:[1.0-SNAPSHOT,)");

        assertThat(constraint.groupId()).isEqualTo("com.workday.quark");
        assertThat(constraint.artifactId()).isEqualTo("quark-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isFalse();
        assertThat(constraint.range()).isEqualTo(Artifact.Version.Range.parse("[1.0-SNAPSHOT,)"));

    }

    /**
     * Ensure a Range-based Maven Artifact, with a type and classifier can be parsed.
     */
    @Test
    void shouldParseARangeBasedConstraint() {
        final Artifact.Constraint constraint = Artifact.Constraint.parse(
            "com.workday.quark:quark-core:jar:source:[1.0,)");

        assertThat(constraint.groupId()).isEqualTo("com.workday.quark");
        assertThat(constraint.artifactId()).isEqualTo("quark-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isTrue();
        assertThat(constraint.classifier().get()).isEqualTo("source");
        assertThat(constraint.range()).isEqualTo(Artifact.Version.Range.parse("[1.0,)"));
    }

    /**
     * Ensure a Version-based Maven Artifact Constraint can be parsed containing additional subsequent text.
     */
    @Test
    void shouldParseAnUndertowVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse("io.undertow:undertow-core:2.3.2.Final");

        assertThat(constraint.groupId()).isEqualTo("io.undertow");
        assertThat(constraint.artifactId()).isEqualTo("undertow-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isFalse();
        assertThat(constraint.range()).isEqualTo(Artifact.Version.Range.parse("2.3.2.Final"));
    }
}
