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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the static utilities in {@link AbstractCompile}:
 * {@code deriveBaseName}, {@code getPackages}, {@code isNamedModule},
 * {@code isProperModule}, {@code classifyJars}, {@code resolveConflicts}, and
 * {@code jarVersionDescending}.
 */
class AbstractCompileTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // deriveBaseName
    // -------------------------------------------------------------------------

    @Test
    void deriveBaseName_stripsVersionAndExtension() {
        assertThat(AbstractCompile.deriveBaseName(Path.of("jboss-logging-3.6.1.Final.jar")))
            .isEqualTo("jboss-logging");
    }

    @Test
    void deriveBaseName_stripsMultiDigitVersion() {
        assertThat(AbstractCompile.deriveBaseName(Path.of("wildfly-common-11.0.0.jar")))
            .isEqualTo("wildfly-common");
    }

    @Test
    void deriveBaseName_noVersion() {
        assertThat(AbstractCompile.deriveBaseName(Path.of("mylib.jar")))
            .isEqualTo("mylib");
    }

    @Test
    void deriveBaseName_hyphenatedNameWithVersion() {
        assertThat(AbstractCompile.deriveBaseName(Path.of("guava-33.0.0-jre.jar")))
            .isEqualTo("guava");
    }

    @Test
    void deriveBaseName_null() {
        assertThat(AbstractCompile.deriveBaseName(null)).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // getPackages — jar files
    // -------------------------------------------------------------------------

    @Test
    void getPackages_returnsPackagesFromJar() throws IOException {
        final Path jar = createJar(tempDir.resolve("test.jar"),
            "com/example/Foo.class",
            "com/example/sub/Bar.class");
        assertThat(AbstractCompile.getPackages(jar))
            .containsExactlyInAnyOrder("com.example", "com.example.sub");
    }

    @Test
    void getPackages_ignoresMetaInfEntries() throws IOException {
        final Path jar = createJar(tempDir.resolve("test.jar"),
            "META-INF/services/SomeInterface",
            "com/example/Foo.class");
        assertThat(AbstractCompile.getPackages(jar))
            .containsExactly("com.example");
    }

    @Test
    void getPackages_ignoresRootLevelClasses() throws IOException {
        // classes in the default (unnamed) package have no slash in their path
        final Path jar = createJar(tempDir.resolve("test.jar"),
            "Foo.class",
            "com/example/Bar.class");
        assertThat(AbstractCompile.getPackages(jar))
            .containsExactly("com.example");
    }

    @Test
    void getPackages_returnsEmptyForNonJarPath() {
        assertThat(AbstractCompile.getPackages(tempDir.resolve("foo.txt"))).isEmpty();
    }

    @Test
    void getPackages_returnsEmptyForNull() {
        assertThat(AbstractCompile.getPackages(null)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getPackages — compiled class directories (bug fix: was always empty)
    // -------------------------------------------------------------------------

    @Test
    void getPackages_returnsPackagesFromClassesDirectory() throws IOException {
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        Files.createFile(classesDir.resolve("com/example/Foo.class"));
        assertThat(AbstractCompile.getPackages(classesDir))
            .containsExactly("com.example");
    }

    @Test
    void getPackages_directoryIgnoresModuleInfoClass() throws IOException {
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        Files.createFile(classesDir.resolve("module-info.class"));
        Files.createFile(classesDir.resolve("com/example/Foo.class"));
        // module-info.class is at the root — no package component, so excluded
        assertThat(AbstractCompile.getPackages(classesDir))
            .containsExactly("com.example");
    }

    // -------------------------------------------------------------------------
    // isNamedModule
    // -------------------------------------------------------------------------

    @Test
    void isNamedModule_returnsTrueForProperModuleJar() throws IOException {
        final Path jar = createJar(tempDir.resolve("test.jar"), "module-info.class");
        assertThat(AbstractCompile.isNamedModule(jar)).isTrue();
    }

    @Test
    void isNamedModule_returnsTrueForAutomaticModuleJar() throws IOException {
        final Path jar = createJarWithManifest(tempDir.resolve("test.jar"),
            "Automatic-Module-Name", "com.example");
        assertThat(AbstractCompile.isNamedModule(jar)).isTrue();
    }

    @Test
    void isNamedModule_returnsTrueForDirectoryContainingModuleInfo() throws IOException {
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.createFile(classesDir.resolve("module-info.class"));
        assertThat(AbstractCompile.isNamedModule(classesDir)).isTrue();
    }

    @Test
    void isNamedModule_returnsFalseForDirectoryWithoutModuleInfo() throws IOException {
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        Files.createFile(classesDir.resolve("com/example/Foo.class"));
        assertThat(AbstractCompile.isNamedModule(classesDir)).isFalse();
    }

    @Test
    void isNamedModule_returnsFalseForPlainJar() throws IOException {
        final Path jar = createJar(tempDir.resolve("test.jar"), "com/example/Foo.class");
        assertThat(AbstractCompile.isNamedModule(jar)).isFalse();
    }

    @Test
    void isNamedModule_returnsFalseForNull() {
        assertThat(AbstractCompile.isNamedModule(null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // isProperModule
    // -------------------------------------------------------------------------

    @Test
    void isProperModule_returnsTrueForJarWithModuleInfo() throws IOException {
        final Path jar = createJar(tempDir.resolve("test.jar"), "module-info.class");
        assertThat(AbstractCompile.isProperModule(jar)).isTrue();
    }

    @Test
    void isProperModule_returnsFalseForAutomaticModuleOnly() throws IOException {
        // key distinction: AMN-only is named but NOT proper
        final Path jar = createJarWithManifest(tempDir.resolve("test.jar"),
            "Automatic-Module-Name", "com.example");
        assertThat(AbstractCompile.isProperModule(jar)).isFalse();
    }

    @Test
    void isProperModule_returnsTrueForDirectoryContainingModuleInfo() throws IOException {
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.createFile(classesDir.resolve("module-info.class"));
        assertThat(AbstractCompile.isProperModule(classesDir)).isTrue();
    }

    @Test
    void isProperModule_returnsFalseForNull() {
        assertThat(AbstractCompile.isProperModule(null)).isFalse();
    }

    // -------------------------------------------------------------------------
    // resolveConflicts
    // -------------------------------------------------------------------------

    @Test
    void resolveConflicts_demotesTrueSplitPackageJar() throws IOException {
        // named proper module wins; unnamed jar sharing the same package is demoted
        final Path named = createJar(tempDir.resolve("named.jar"),
            "module-info.class", "com/example/Foo.class");
        final Path unnamed = createJar(tempDir.resolve("unnamed.jar"),
            "com/example/Bar.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(named, unnamed), msg -> {});
        assertThat(resolution.demoted()).containsExactly(unnamed);
        assertThat(resolution.superseded()).isEmpty();
    }

    @Test
    void resolveConflicts_demotesBothUnnamedJarsOnSplitPackage() throws IOException {
        // two unnamed jars sharing a package — neither is a proper module so both are demoted
        final Path jarA = createJar(tempDir.resolve("a.jar"), "com/example/Foo.class");
        final Path jarB = createJar(tempDir.resolve("b.jar"), "com/example/Bar.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(jarA, jarB), msg -> {});
        assertThat(resolution.demoted()).containsExactlyInAnyOrder(jarA, jarB);
        assertThat(resolution.superseded()).isEmpty();
    }

    @Test
    void resolveConflicts_emptyWhenNoConflict() throws IOException {
        final Path named = createJar(tempDir.resolve("named.jar"),
            "module-info.class", "com/foo/A.class");
        final Path unnamed = createJar(tempDir.resolve("unnamed.jar"), "com/bar/B.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(named, unnamed), msg -> {});
        assertThat(resolution.demoted()).isEmpty();
        assertThat(resolution.superseded()).isEmpty();
    }

    @Test
    void resolveConflicts_supersedesOlderVersionDuplicate() throws IOException {
        // same base name, different versions — older is superseded, newer stays
        final Path older = createJar(tempDir.resolve("wildfly-common-2.0.0.jar"), "com/example/Foo.class");
        final Path newer = createJar(tempDir.resolve("wildfly-common-11.0.0.jar"), "com/example/Foo.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(older, newer), msg -> {});
        assertThat(resolution.superseded()).containsExactly(older);
        assertThat(resolution.demoted()).isEmpty();
    }

    @Test
    void resolveConflicts_directoryNamedModuleDetectsConflict() throws IOException {
        // Regression: directory-based named modules were invisible before getPackages scanned dirs
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        Files.createFile(classesDir.resolve("module-info.class"));
        Files.createFile(classesDir.resolve("com/example/Foo.class"));
        final Path unnamed = createJar(tempDir.resolve("unnamed.jar"), "com/example/Bar.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(classesDir, unnamed), msg -> {});
        assertThat(resolution.demoted()).containsExactly(unnamed);
    }

    @Test
    void resolveConflicts_logCallbackReceivesDecisionMessages() throws IOException {
        final Path older = createJar(tempDir.resolve("lib-1.0.jar"), "com/example/Foo.class");
        final Path newer = createJar(tempDir.resolve("lib-2.0.jar"), "com/example/Foo.class");
        final var messages = new ArrayList<String>();
        AbstractCompile.resolveConflicts(List.of(older, newer), messages::add);
        assertThat(messages).anyMatch(msg -> msg.contains("superseded"));
    }

    @Test
    void resolveConflicts_supersededAndDemotedAreDisjoint() throws IOException {
        // A jar that is superseded (version duplicate) must NOT also appear in demoted —
        // AbstractJavaDependencyAnalysis drops superseded jars entirely; putting them on the
        // classpath via demoted would be wrong.
        final Path older = createJar(tempDir.resolve("lib-1.0.jar"), "com/example/Foo.class");
        final Path newer = createJar(tempDir.resolve("lib-2.0.jar"), "com/example/Foo.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(older, newer), msg -> {});
        assertThat(resolution.superseded()).containsExactly(older);
        assertThat(resolution.demoted()).doesNotContain(older);
    }

    @Test
    void resolveConflicts_subsetJarIsSuperseded() throws IOException {
        // A jar whose packages are a strict subset of another jar's is superseded (dropped),
        // not demoted to classpath. Models the jboss-logmanager / wildfly-common pattern where
        // a companion jar leaks packages that are fully owned by the primary module.
        final Path subset = createJar(tempDir.resolve("lib-extra-1.0.jar"),
            "com/example/Foo.class");
        final Path superset = createJar(tempDir.resolve("lib-2.0.jar"),
            "com/example/Foo.class", "com/example/sub/Bar.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(subset, superset), msg -> {});
        assertThat(resolution.superseded()).containsExactly(subset);
        assertThat(resolution.demoted()).isEmpty();
    }

    @Test
    void resolveConflicts_properModuleNotInEitherSet() throws IOException {
        // The proper module (has module-info.class) wins a split-package conflict and must
        // appear in neither superseded nor demoted — it stays on --module-path.
        // AbstractJavaDependencyAnalysis copies it to the module path only when it is absent
        // from both sets.
        final Path proper = createJar(tempDir.resolve("proper.jar"),
            "module-info.class", "com/example/Foo.class");
        final Path automatic = createJar(tempDir.resolve("automatic.jar"),
            "com/example/Bar.class");
        final var resolution = AbstractCompile.resolveConflicts(List.of(proper, automatic), msg -> {});
        assertThat(resolution.superseded()).doesNotContain(proper);
        assertThat(resolution.demoted()).doesNotContain(proper);
        assertThat(resolution.demoted()).containsExactly(automatic);
    }

    @Test
    void resolveConflicts_versionDuplicateAndSplitPackageInSameCandidateList() throws IOException {
        // Regression: a candidate list containing both a version duplicate AND an unrelated
        // split-package pair. The version duplicate should be superseded; the split-package
        // pair should be independently demoted.
        final Path older = createJar(tempDir.resolve("util-1.0.jar"), "com/util/A.class");
        final Path newer = createJar(tempDir.resolve("util-2.0.jar"), "com/util/A.class");
        final Path splitA = createJar(tempDir.resolve("split-a.jar"), "com/shared/X.class");
        final Path splitB = createJar(tempDir.resolve("split-b.jar"), "com/shared/Y.class");
        final var resolution = AbstractCompile.resolveConflicts(
            List.of(older, newer, splitA, splitB), msg -> {});
        assertThat(resolution.superseded()).containsExactly(older);
        assertThat(resolution.demoted()).containsExactlyInAnyOrder(splitA, splitB);
        assertThat(resolution.superseded()).doesNotContainAnyElementsOf(resolution.demoted());
    }

    // -------------------------------------------------------------------------
    // classifyJars
    // -------------------------------------------------------------------------

    @Test
    void classifyJars_allNonConflictingJarsGoToModulePath() throws IOException {
        // Both named and unnamed jars without conflicts are promoted to --module-path so
        // filename-derived automatic modules (e.g. "requires undertow.core") are resolvable.
        final Path named = createJar(tempDir.resolve("named.jar"), "module-info.class", "com/example/Foo.class");
        final Path unnamed = createJar(tempDir.resolve("unnamed.jar"), "com/bar/Bar.class");
        final var result = AbstractCompile.classifyJars(List.of(named, unnamed));
        assertThat(result.namedModules()).containsExactlyInAnyOrder(named, unnamed);
        assertThat(result.unnamedJars()).isEmpty();
    }

    @Test
    void classifyJars_unnamedJarWithNoConflictPromotedToModulePath() throws IOException {
        // Jars without module-info.class or Automatic-Module-Name are still promoted to
        // --module-path so the JPMS module finder can resolve them by filename-derived name.
        // e.g. module-info.java saying "requires undertow.core" relies on this promotion.
        final Path unnamed = createJar(tempDir.resolve("plain.jar"), "com/example/Foo.class");
        final var result = AbstractCompile.classifyJars(List.of(unnamed));
        assertThat(result.namedModules()).containsExactly(unnamed);
        assertThat(result.unnamedJars()).isEmpty();
    }

    @Test
    void classifyJars_demotedJarGoesToClasspath() throws IOException {
        final Path named = createJar(tempDir.resolve("named.jar"), "module-info.class", "com/example/Foo.class");
        final Path conflict = createJar(tempDir.resolve("conflict.jar"), "com/example/Bar.class");
        final var result = AbstractCompile.classifyJars(List.of(named, conflict));
        assertThat(result.namedModules()).containsExactly(named);
        assertThat(result.unnamedJars()).containsExactly(conflict);
    }

    @Test
    void classifyJars_supersededJarGoesToClasspath() throws IOException {
        // Both jars must have overlapping packages so resolveConflicts detects the version
        // duplicate and supersedes the older one.
        final Path older = createJar(tempDir.resolve("lib-1.0.jar"), "com/example/Foo.class");
        final Path newer = createJar(tempDir.resolve("lib-2.0.jar"), "com/example/Bar.class");
        final var result = AbstractCompile.classifyJars(List.of(older, newer));
        assertThat(result.unnamedJars()).contains(older);
        assertThat(result.namedModules()).doesNotContain(older);
    }

    // -------------------------------------------------------------------------
    // jarVersionDescending — semantic version ordering (not lexicographic)
    // -------------------------------------------------------------------------

    @Test
    void jarVersionDescending_prefersHigherNumericMajorVersion() throws IOException {
        // Lexicographic ordering would put "2" before "11" (wrong); semantic ordering must put "11" first.
        final Path v2 = Files.createFile(tempDir.resolve("wildfly-common-2.0.0.jar"));
        final Path v11 = Files.createFile(tempDir.resolve("wildfly-common-11.0.0.jar"));

        final var sorted = new ArrayList<>(List.of(v2, v11));
        sorted.sort(AbstractCompile.jarVersionDescending());

        assertThat(sorted.get(0)).isEqualTo(v11);
    }

    @Test
    void jarVersionDescending_prefersHigherMinorVersion() throws IOException {
        final Path v13 = Files.createFile(tempDir.resolve("wildfly-common-1.3.0.Beta1.jar"));
        final Path v110 = Files.createFile(tempDir.resolve("wildfly-common-1.10.0.jar"));

        final var sorted = new ArrayList<>(List.of(v13, v110));
        sorted.sort(AbstractCompile.jarVersionDescending());

        assertThat(sorted.get(0)).isEqualTo(v110);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path createJar(final Path path, final String... entries) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(path))) {
            for (final String entry : entries) {
                jos.putNextEntry(new JarEntry(entry));
                jos.closeEntry();
            }
        }
        return path;
    }

    private Path createJarWithManifest(final Path path,
                                       final String attrName,
                                       final String attrValue) throws IOException {
        final var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(attrName, attrValue);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            // empty jar with manifest only
        }
        return path;
    }
}
