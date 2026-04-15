package build.spin.module.modulesystem;

import build.base.version.VersionConstraint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Artifact.Constraint}s.
 *
 * @since Dec-2020
 */
class ArtifactConstraintTests {

    @Test
    void shouldParseAVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse("com.workday.quark:quark-core:1.0");

        assertThat(constraint.groupId()).isEqualTo("com.workday.quark");
        assertThat(constraint.artifactId()).isEqualTo("quark-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isFalse();
        assertThat(constraint.range()).isEqualTo(VersionConstraint.parse("1.0"));
    }

    @Test
    void shouldParseASnapshotVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse(
            "com.workday.quark:quark-core:[1.0-SNAPSHOT,)");

        assertThat(constraint.groupId()).isEqualTo("com.workday.quark");
        assertThat(constraint.artifactId()).isEqualTo("quark-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isFalse();
        assertThat(constraint.range()).isEqualTo(VersionConstraint.parse("[1.0-SNAPSHOT,)"));
    }

    @Test
    void shouldParseARangeBasedConstraint() {
        final Artifact.Constraint constraint = Artifact.Constraint.parse(
            "com.workday.quark:quark-core:jar:source:[1.0,)");

        assertThat(constraint.groupId()).isEqualTo("com.workday.quark");
        assertThat(constraint.artifactId()).isEqualTo("quark-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isTrue();
        assertThat(constraint.classifier().get()).isEqualTo("source");
        assertThat(constraint.range()).isEqualTo(VersionConstraint.parse("[1.0,)"));
    }

    @Test
    void shouldParseAnUndertowVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse("io.undertow:undertow-core:2.3.2.Final");

        assertThat(constraint.groupId()).isEqualTo("io.undertow");
        assertThat(constraint.artifactId()).isEqualTo("undertow-core");
        assertThat(constraint.type()).isEqualTo("jar");
        assertThat(constraint.classifier().isPresent()).isFalse();
        assertThat(constraint.range()).isEqualTo(VersionConstraint.parse("2.3.2.Final"));
    }
}
