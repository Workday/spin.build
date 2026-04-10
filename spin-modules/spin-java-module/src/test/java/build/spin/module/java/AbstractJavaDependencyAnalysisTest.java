package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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

import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ArtifactDescriptor;
import build.spin.module.modulesystem.ModuleReference;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the static helpers in {@link AbstractJavaDependencyAnalysis}, in particular
 * {@code dedupeByMavenCoordinates}, which is the dedupe pass that catches the slf4j-api
 * 1.x → 2.x rename
 */
class AbstractJavaDependencyAnalysisTest {

    @Test
    void dedupeByMavenCoordinates_emptyInput_returnsEmpty() {
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(), (kept, dropped) -> { });
        assertThat(result).isEmpty();
    }

    @Test
    void dedupeByMavenCoordinates_distinctCoordinates_keepsAll() {
        final var a = descriptor("com.example", "lib-a", "1.0.0", "lib.a");
        final var b = descriptor("com.example", "lib-b", "1.0.0", "lib.b");
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(a, b), (kept, dropped) -> { });
        assertThat(result.values()).containsExactly(a, b);
    }

    @Test
    void dedupeByMavenCoordinates_sameArtifactDifferentGroupId_bothKept() {
        // Different groupIds with the same artifactId are NOT duplicates — both kept.
        final var a = descriptor("com.example", "lib", "1.0.0", "mod.a");
        final var b = descriptor("org.other", "lib", "1.0.0", "mod.b");
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(a, b), (kept, dropped) -> { });
        assertThat(result.values()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void dedupeByMavenCoordinates_olderThenNewer_keepsNewer() {
        final var older = descriptor("com.example", "lib", "1.0.0", "com.example.lib");
        final var newer = descriptor("com.example", "lib", "2.0.0", "com.example.lib");
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(older, newer), (kept, dropped) -> { });
        assertThat(result.values()).containsExactly(newer);
    }

    @Test
    void dedupeByMavenCoordinates_newerThenOlder_keepsNewer() {
        // Order independence: the higher version wins regardless of insertion order.
        final var newer = descriptor("com.example", "lib", "2.0.0", "com.example.lib");
        final var older = descriptor("com.example", "lib", "1.0.0", "com.example.lib");
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(newer, older), (kept, dropped) -> { });
        assertThat(result.values()).containsExactly(newer);
    }

    @Test
    void dedupeByMavenCoordinates_threeVersions_keepsHighestSemantic() {
        // 11.0.0 must beat both 1.0.0 and 2.0.0 (semantic, not lexicographic, ordering).
        final var v1 = descriptor("com.example", "lib", "1.0.0", "com.example.lib");
        final var v2 = descriptor("com.example", "lib", "2.0.0", "com.example.lib");
        final var v11 = descriptor("com.example", "lib", "11.0.0", "com.example.lib");
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(v1, v2, v11), (kept, dropped) -> { });
        assertThat(result.values()).containsExactly(v11);
    }

    @Test
    void dedupeByMavenCoordinates_slf4jStyleRename_keepsNewerEvenWithDifferentModuleName() {
        // slf4j-api 1.7.25 has the filename-derived automatic module name "slf4j.api"; slf4j-api 2.0.17 is the proper
        // JPMS module "org.slf4j". Both share Maven coordinates org.slf4j:slf4j-api so the
        // coordinate dedupe pass must drop the older one before any classifier sees the
        // org.slf4j package conflict. A naive module-name dedupe (the second pass) cannot
        // catch this because the names are different.
        final var slf4jOld = descriptor("org.slf4j", "slf4j-api", "1.7.25", "slf4j.api");
        final var slf4jNew = descriptor("org.slf4j", "slf4j-api", "2.0.17", "org.slf4j");
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(slf4jOld, slf4jNew), (kept, dropped) -> { });
        assertThat(result.values()).containsExactly(slf4jNew);
    }

    @Test
    void dedupeByMavenCoordinates_callbackInvokedWhenIncomingIsLower() {
        // existing higher, incoming lower → kept = existing, dropped = incoming
        final var newer = descriptor("com.example", "lib", "2.0.0", "lib");
        final var older = descriptor("com.example", "lib", "1.0.0", "lib");
        final var calls = new ArrayList<String>();
        AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(newer, older),
            (kept, dropped) -> calls.add(
                kept.artifact().version() + "/" + dropped.artifact().version()));
        assertThat(calls).containsExactly("2.0.0/1.0.0");
    }

    @Test
    void dedupeByMavenCoordinates_callbackInvokedWhenIncomingDisplacesExisting() {
        // existing lower, incoming higher → kept = incoming, dropped = existing
        final var older = descriptor("com.example", "lib", "1.0.0", "lib");
        final var newer = descriptor("com.example", "lib", "2.0.0", "lib");
        final var calls = new ArrayList<String>();
        AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(older, newer),
            (kept, dropped) -> calls.add(
                kept.artifact().version() + "/" + dropped.artifact().version()));
        assertThat(calls).containsExactly("2.0.0/1.0.0");
    }

    @Test
    void dedupeByMavenCoordinates_callbackNotInvokedWhenNoDuplicates() {
        final var a = descriptor("com.example", "lib-a", "1.0.0", "lib.a");
        final var b = descriptor("com.example", "lib-b", "1.0.0", "lib.b");
        final var calls = new ArrayList<String>();
        AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(a, b),
            (kept, dropped) -> calls.add(kept + "/" + dropped));
        assertThat(calls).isEmpty();
    }

    @Test
    void dedupeByMavenCoordinates_preservesInsertionOrder() {
        final var a = descriptor("com.example", "lib-a", "1.0.0", "lib.a");
        final var b = descriptor("com.example", "lib-b", "1.0.0", "lib.b");
        final var c = descriptor("com.example", "lib-c", "1.0.0", "lib.c");
        final var result = AbstractJavaDependencyAnalysis.dedupeByMavenCoordinates(
            List.of(b, c, a), (kept, dropped) -> { });
        assertThat(result.values()).containsExactly(b, c, a);
    }

    private static ArtifactDescriptor descriptor(final String groupId,
                                                 final String artifactId,
                                                 final String version,
                                                 final String moduleName) {
        final Artifact artifact = Artifact.create(groupId, artifactId, version, "jar");
        final ModuleReference reference = ModuleReference.of(moduleName);
        return ArtifactDescriptor.create(reference, artifact);
    }
}
