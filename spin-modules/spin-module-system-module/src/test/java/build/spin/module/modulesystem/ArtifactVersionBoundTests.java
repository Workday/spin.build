package build.spin.module.modulesystem;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Artifact.Version.Bound}s.
 *
 * @author brian.oliver
 * @since Dec-2020
 */
class ArtifactVersionBoundTests {

    /**
     * Ensure {@link Artifact.Version.Bound} {@link Predicate}s are evaluated correctly using a variety of
     * {@link Artifact.Version}s.
     */
    @Test
    void shouldEvaluateBoundsPredicate() {

        final Artifact.Version.Bound strictlyAfter = Artifact.Version.Bound.of(
            Artifact.Version.Bound.Inclusivity.STRICTLY,
            Artifact.Version.Bound.Constraint.AFTER,
            "2.0");

        assertThat(strictlyAfter.test(Artifact.Version.parse("1.0"))).isFalse();
        assertThat(strictlyAfter.test(Artifact.Version.parse("2.0"))).isFalse();
        assertThat(strictlyAfter.test(Artifact.Version.parse("3.0"))).isTrue();
        assertThat(strictlyAfter.test(Artifact.Version.parse("2.3.2.Final"))).isTrue();

        final Artifact.Version.Bound inclusivelyAfter = Artifact.Version.Bound.of(
            Artifact.Version.Bound.Inclusivity.INCLUSIVELY,
            Artifact.Version.Bound.Constraint.AFTER,
            "2.0");

        assertThat(inclusivelyAfter.test(Artifact.Version.parse("1.0"))).isFalse();
        assertThat(inclusivelyAfter.test(Artifact.Version.parse("2.0"))).isTrue();
        assertThat(inclusivelyAfter.test(Artifact.Version.parse("3.0"))).isTrue();
        assertThat(inclusivelyAfter.test(Artifact.Version.parse("2.3.2.Final"))).isTrue();

        final Artifact.Version.Bound strictlyBefore = Artifact.Version.Bound.of(
            Artifact.Version.Bound.Inclusivity.STRICTLY,
            Artifact.Version.Bound.Constraint.BEFORE,
            "2.0");

        assertThat(strictlyBefore.test(Artifact.Version.parse("1.0"))).isTrue();
        assertThat(strictlyBefore.test(Artifact.Version.parse("2.0"))).isFalse();
        assertThat(strictlyBefore.test(Artifact.Version.parse("3.0"))).isFalse();
        assertThat(strictlyBefore.test(Artifact.Version.parse("2.3.2.Final"))).isFalse();

        final Artifact.Version.Bound inclusivelyBefore = Artifact.Version.Bound.of(
            Artifact.Version.Bound.Inclusivity.INCLUSIVELY,
            Artifact.Version.Bound.Constraint.BEFORE,
            "2.0");

        assertThat(inclusivelyBefore.test(Artifact.Version.parse("1.0"))).isTrue();
        assertThat(inclusivelyBefore.test(Artifact.Version.parse("2.0"))).isTrue();
        assertThat(inclusivelyBefore.test(Artifact.Version.parse("3.0"))).isFalse();
        assertThat(inclusivelyBefore.test(Artifact.Version.parse("2.3.2.Final"))).isFalse();
    }
}
