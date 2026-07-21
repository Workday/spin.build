package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import build.base.telemetry.TelemetryRecorder;
import build.base.version.Version;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that {@link ModuleCatalog#getArtifact(ModuleReference, Optional)} keeps the
 * first registered {@link Artifact}, warning only when a genuinely ambiguous registration (two
 * distinct {@link Artifact}s satisfying the same module name and version) is encountered — as
 * opposed to harmless duplicate/overlapping registrations of the same {@link Artifact}.
 */
class ModuleCatalogTests {

    @Test
    void getArtifact_warnsWhenTwoDistinctArtifactsSatisfyTheSameModuleNameAndVersion() {
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("example.module", Artifact.Constraint.parse("com.example:one:1.0.0"))
            .add("example.module", Artifact.Constraint.parse("com.example:two:1.0.0"));

        final ModuleReference reference = ModuleReference.of("example.module", Version.parse("1.0.0"));
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);

        catalog.getArtifact(reference, Optional.of(recorder));

        verify(recorder).warn(any(String.class), any(Object[].class));
    }

    @Test
    void getArtifact_returnsTheFirstRegisteredArtifactWhenAmbiguous() {
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("example.module", Artifact.Constraint.parse("com.example:one:1.0.0"))
            .add("example.module", Artifact.Constraint.parse("com.example:two:1.0.0"));

        final ModuleReference reference = ModuleReference.of("example.module", Version.parse("1.0.0"));
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);

        assertThat(catalog.getArtifact(reference, Optional.of(recorder)))
            .isPresent()
            .get()
            .isEqualTo(Artifact.parse("com.example:one:1.0.0"));
    }

    @Test
    void getArtifact_doesNotWarnWhenDuplicateRegistrationsResolveToTheSameArtifact() {
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("example.module", Artifact.Constraint.parse("com.example:one:1.0.0"))
            .add("example.module", Artifact.Constraint.parse("com.example:one:[1.0.0,2.0.0)"));

        final ModuleReference reference = ModuleReference.of("example.module", Version.parse("1.0.0"));
        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);

        final var artifact = catalog.getArtifact(reference, Optional.of(recorder));

        assertThat(artifact).isPresent().get().isEqualTo(Artifact.parse("com.example:one:1.0.0"));
        verifyNoInteractions(recorder);
    }

    @Test
    void getArtifact_returnsEmmptyWhenNoConstraintMatchesTheRequestedVersion() {
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("example.module", Artifact.Constraint.parse("com.example:one:1.0.0"));

        final ModuleReference reference = ModuleReference.of("example.module", Version.parse("2.0.0"));

        assertThat(catalog.getArtifact(reference)).isEmpty();
    }

    @Test
    void getArtifact_returnsTheSoleMatchingArtifactWhenUnambiguous() {
        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("example.module", Artifact.Constraint.parse("com.example:one:1.0.0"));

        final ModuleReference reference = ModuleReference.of("example.module", Version.parse("1.0.0"));

        assertThat(catalog.getArtifact(reference))
            .isPresent()
            .get()
            .isEqualTo(Artifact.parse("com.example:one:1.0.0"));
    }

    /**
     * Reproduces the snappy-java bug: when a jar carries neither {@code module-info.class} nor an
     * {@code Automatic-Module-Name} attribute, {@code MavenModuleNaming.deriveNames} registers several
     * naming-convention candidates for the same {@link Artifact} — including the bare
     * {@code lastHyphenSegment} heuristic, which for {@code org.xerial:snappy-java} is the misleading
     * {@code "java"}. {@link ModuleCatalog.HeapBased#getModuleReference(Artifact)} must deterministically
     * prefer the JPMS-spec-derived name ({@code "snappy.java"}) over such aliases, regardless of which
     * entry a {@code ConcurrentHashMap} happens to iterate first.
     */
    @Test
    void getModuleReference_prefersTheDerivedNameOverOtherRegisteredAliases() {
        final Artifact snappyJava = Artifact.parse("org.xerial:snappy-java:1.1.10.8");
        final Artifact.Constraint constraint = Artifact.Constraint.of(snappyJava);

        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("java", constraint)
            .add("org.xerial", constraint)
            .add("snappy.java", constraint);

        assertThat(catalog.getModuleReference(snappyJava))
            .isPresent()
            .get()
            .extracting(ModuleReference::name)
            .isEqualTo("snappy.java");
    }

    @Test
    void getModuleReference_fallsBackToAnyRegisteredAliasWhenTheDerivedNameIsNotOneOfThem() {
        final Artifact snappyJava = Artifact.parse("org.xerial:snappy-java:1.1.10.8");
        final Artifact.Constraint constraint = Artifact.Constraint.of(snappyJava);

        final ModuleCatalog catalog = ModuleCatalog.HeapBased.create()
            .add("java", constraint)
            .add("org.xerial", constraint);

        assertThat(catalog.getModuleReference(snappyJava))
            .isPresent()
            .get()
            .extracting(ModuleReference::name)
            .isIn("java", "org.xerial");
    }
}
