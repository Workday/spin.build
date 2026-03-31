package build.spin.module.modulesystem;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

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

        assertThat(constraint.groupId(), is("com.workday.quark"));
        assertThat(constraint.artifactId(), is("quark-core"));
        assertThat(constraint.type(), is("jar"));
        assertThat(constraint.classifier().isPresent(), is(false));
        assertThat(constraint.range(), is(Artifact.Version.Range.parse("1.0")));
    }

    /**
     * Ensure a SNAPSHOT Version-based Maven Artifact Constraint can be parsed.
     */
    @Test
    void shouldParseASnapshotVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse(
            "com.workday.quark:quark-core:[1.0-SNAPSHOT,)");

        assertThat(constraint.groupId(), is("com.workday.quark"));
        assertThat(constraint.artifactId(), is("quark-core"));
        assertThat(constraint.type(), is("jar"));
        assertThat(constraint.classifier().isPresent(), is(false));
        assertThat(constraint.range(), is(Artifact.Version.Range.parse("[1.0-SNAPSHOT,)")));

    }

    /**
     * Ensure a Range-based Maven Artifact, with a type and classifier can be parsed.
     */
    @Test
    void shouldParseARangeBasedConstraint() {
        final Artifact.Constraint constraint = Artifact.Constraint.parse(
            "com.workday.quark:quark-core:jar:source:[1.0,)");

        assertThat(constraint.groupId(), is("com.workday.quark"));
        assertThat(constraint.artifactId(), is("quark-core"));
        assertThat(constraint.type(), is("jar"));
        assertThat(constraint.classifier().isPresent(), is(true));
        assertThat(constraint.classifier().get(), is("source"));
        assertThat(constraint.range(), is(Artifact.Version.Range.parse("[1.0,)")));
    }

    /**
     * Ensure a Version-based Maven Artifact Constraint can be parsed containing additional subsequent text.
     */
    @Test
    void shouldParseAnUndertowVersionBasedArtifactConstraint() {

        final Artifact.Constraint constraint = Artifact.Constraint.parse("io.undertow:undertow-core:2.3.2.Final");

        assertThat(constraint.groupId(), is("io.undertow"));
        assertThat(constraint.artifactId(), is("undertow-core"));
        assertThat(constraint.type(), is("jar"));
        assertThat(constraint.classifier().isPresent(), is(false));
        assertThat(constraint.range(), is(Artifact.Version.Range.parse("2.3.2.Final")));
    }
}
