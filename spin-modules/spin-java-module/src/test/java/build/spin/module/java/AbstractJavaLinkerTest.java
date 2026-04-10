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
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the static jar-classification helpers in {@link AbstractJavaLinker}:
 * {@code findSplitPackageConflicts} and {@code classifyForModulePath}.
 *
 * <p>Tests build real on-disk jars (automatic modules via {@code Automatic-Module-Name}
 * manifest entries, and proper modules via {@link ClassFile} API-generated
 * {@code module-info.class}) so that {@code java.lang.module.ModuleFinder} parses them
 * exactly as it would at runtime.
 */
class AbstractJavaLinkerTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // findSplitPackageConflicts
    // -------------------------------------------------------------------------

    @Test
    void findSplitPackageConflicts_emptyCandidates_returnsEmpty() {
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(Set.of(), "any.root"))
            .isEmpty();
    }

    @Test
    void findSplitPackageConflicts_singleJar_returnsEmpty() throws IOException {
        final Path a = automaticModule("a.jar", "mod.a", "com/a/A.class");
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(setOf(a), "mod.a"))
            .isEmpty();
    }

    @Test
    void findSplitPackageConflicts_disjointPackages_returnsEmpty() throws IOException {
        final Path a = automaticModule("a.jar", "mod.a", "com/a/A.class");
        final Path b = automaticModule("b.jar", "mod.b", "com/b/B.class");
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(setOf(a, b), "root"))
            .isEmpty();
    }

    @Test
    void findSplitPackageConflicts_twoJarsShareOnePackage_bothDemoted() throws IOException {
        final Path a = automaticModule("a.jar", "mod.a", "com/shared/X.class");
        final Path b = automaticModule("b.jar", "mod.b", "com/shared/Y.class");
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(setOf(a, b), "root"))
            .containsExactlyInAnyOrder(a, b);
    }

    @Test
    void findSplitPackageConflicts_threeJarsShareOnePackage_allDemoted() throws IOException {
        final Path a = automaticModule("a.jar", "mod.a", "com/shared/A.class");
        final Path b = automaticModule("b.jar", "mod.b", "com/shared/B.class");
        final Path c = automaticModule("c.jar", "mod.c", "com/shared/C.class");
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(setOf(a, b, c), "root"))
            .containsExactlyInAnyOrder(a, b, c);
    }

    @Test
    void findSplitPackageConflicts_independentConflictPairs_allDemoted() throws IOException {
        // Two disjoint split-package conflicts in the same candidate set — every party to
        // every conflict gets demoted in a single pass.
        final Path a = automaticModule("a.jar", "mod.a", "com/x/A.class");
        final Path b = automaticModule("b.jar", "mod.b", "com/x/B.class");
        final Path c = automaticModule("c.jar", "mod.c", "com/y/C.class");
        final Path d = automaticModule("d.jar", "mod.d", "com/y/D.class");
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(setOf(a, b, c, d), "root"))
            .containsExactlyInAnyOrder(a, b, c, d);
    }

    @Test
    void findSplitPackageConflicts_rootInConflict_rootNeverDemoted() throws IOException {
        // The root module is the application being packaged. It is never a candidate for
        // demotion because it must remain on --module-path for `-m <root>/Main` to work.
        final Path root = automaticModule("root.jar", "my.root", "com/shared/R.class");
        final Path other = automaticModule("other.jar", "mod.other", "com/shared/O.class");
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(setOf(root, other), "my.root"))
            .containsExactly(other);
    }

    @Test
    void findSplitPackageConflicts_rootIsBystander_otherPartiesDemoted() throws IOException {
        // Root has its own package and is not party to the conflict; only the actual
        // conflicting jars get demoted.
        final Path root = automaticModule("root.jar", "my.root", "com/root/R.class");
        final Path a = automaticModule("a.jar", "mod.a", "com/shared/A.class");
        final Path b = automaticModule("b.jar", "mod.b", "com/shared/B.class");
        assertThat(AbstractJavaLinker.findSplitPackageConflicts(setOf(root, a, b), "my.root"))
            .containsExactlyInAnyOrder(a, b);
    }

    // -------------------------------------------------------------------------
    // classifyForModulePath
    // -------------------------------------------------------------------------

    @Test
    void classifyForModulePath_singleAutomaticRoot_returnsRoot() throws IOException {
        final Path root = automaticModule("root.jar", "my.root", "com/root/R.class");
        final Set<Path> result = AbstractJavaLinker.classifyForModulePath(
            List.of(root), "my.root");
        assertThat(result).containsExactly(root);
    }

    @Test
    void classifyForModulePath_properRootRequiresAutomaticDep_bothResolved() throws IOException {
        // The proper module declares `requires mod.dep`, so Configuration.resolve walks the
        // requires graph and pulls mod.dep onto the module-path.
        final Path dep = automaticModule("dep.jar", "mod.dep", "com/dep/D.class");
        final Path root = properModule("root.jar", "my.root",
            List.of("mod.dep"),
            "com/root/R.class");
        final Set<Path> result = AbstractJavaLinker.classifyForModulePath(
            List.of(root, dep), "my.root");
        assertThat(result).containsExactlyInAnyOrder(root, dep);
    }

    @Test
    void classifyForModulePath_unrequiredSplitPackageDeps_demotedAndExcluded() throws IOException {
        // Root requires only the clean dep. Two unrelated jars share a package — they should
        // be demoted out of the candidate set and end up in NEITHER the resolved module-path
        // result NOR cause the resolve to fail. The clean dep stays on the module-path.
        final Path clean = automaticModule("clean.jar", "mod.clean", "com/clean/C.class");
        final Path conflictA = automaticModule("a.jar", "mod.a", "com/shared/A.class");
        final Path conflictB = automaticModule("b.jar", "mod.b", "com/shared/B.class");
        final Path root = properModule("root.jar", "my.root",
            List.of("mod.clean"),
            "com/root/R.class");
        final Set<Path> result = AbstractJavaLinker.classifyForModulePath(
            List.of(root, clean, conflictA, conflictB), "my.root");
        assertThat(result).containsExactlyInAnyOrder(root, clean);
        assertThat(result).doesNotContain(conflictA, conflictB);
    }

    @Test
    void classifyForModulePath_demotionRemovesRequiredDep_throwsIllegalState() throws IOException {
        // Root requires mod.a, but mod.a is split-package with mod.b. Both demoted; then
        // Configuration.resolve cannot satisfy `requires mod.a` and the whole classification
        // fails. This is the canary that proves split-package demotion runs BEFORE the
        // resolve step (otherwise resolve would itself raise the split-package error).
        final Path a = automaticModule("a.jar", "mod.a", "com/shared/A.class");
        final Path b = automaticModule("b.jar", "mod.b", "com/shared/B.class");
        final Path root = properModule("root.jar", "my.root",
            List.of("mod.a"),
            "com/root/R.class");

        assertThatThrownBy(() -> AbstractJavaLinker.classifyForModulePath(
                List.of(root, a, b), "my.root"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to resolve JPMS module graph")
            .hasMessageContaining("my.root");
    }

    @Test
    void classifyForModulePath_unresolvableRoot_throwsIllegalState() throws IOException {
        final Path dep = automaticModule("dep.jar", "mod.dep", "com/dep/D.class");
        assertThatThrownBy(() -> AbstractJavaLinker.classifyForModulePath(
                List.of(dep), "nonexistent.root"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to resolve JPMS module graph")
            .hasMessageContaining("nonexistent.root");
    }

    @Test
    void classifyForModulePath_systemModulesNotInResult() throws IOException {
        // Configuration.resolve will internally pull in java.base (jrt: scheme), but the
        // result must contain only paths from the original candidate set — jlink already
        // baked the system modules into the runtime image and copying jrt: paths would fail.
        final Path root = automaticModule("root.jar", "my.root", "com/root/R.class");
        final Set<Path> result = AbstractJavaLinker.classifyForModulePath(
            List.of(root), "my.root");
        assertThat(result).containsExactly(root);
        assertThat(result).allSatisfy(p -> assertThat(p.getFileName().toString()).endsWith(".jar"));
    }

    @Test
    void classifyForModulePath_rootInConflict_rootKeptOtherDemoted() throws IOException {
        // Even when the root is a party to a split-package conflict it must remain on
        // --module-path; the other party is demoted. Mirrors the findSplitPackageConflicts
        // root-protection rule end-to-end.
        final Path other = automaticModule("other.jar", "mod.other", "com/shared/O.class");
        final Path root = properModule("root.jar", "my.root",
            List.of(),
            "com/shared/R.class");
        final Set<Path> result = AbstractJavaLinker.classifyForModulePath(
            List.of(root, other), "my.root");
        assertThat(result).containsExactly(root);
    }

    @Test
    void classifyForModulePath_slf4jStyleConflict_demotesBothVersions() throws IOException {
        // Regression for the slf4j-api 1.7.25 vs 2.0.17 case from jpms-launch-findings.md.
        // Two jars with different module names both owning org.slf4j → both must be demoted.
        // The coordinate-dedupe pass in AbstractJavaDependencyAnalysis is what actually
        // prevents this from reaching the linker in production; this test pins the linker's
        // demotion behavior in case the upstream dedupe regresses.
        final Path slf4jOld = automaticModule("slf4j-api-1.7.25.jar", "slf4j.api",
            "org/slf4j/Logger.class", "org/slf4j/impl/Foo.class");
        final Path slf4jNew = automaticModule("slf4j-api-2.0.17.jar", "org.slf4j",
            "org/slf4j/Logger.class", "org/slf4j/event/Bar.class");
        final Path root = automaticModule("root.jar", "my.root", "com/root/R.class");
        final Set<Path> result = AbstractJavaLinker.classifyForModulePath(
            List.of(root, slf4jOld, slf4jNew), "my.root");
        assertThat(result).containsExactly(root);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Set<Path> setOf(final Path... paths) {
        return new LinkedHashSet<>(List.of(paths));
    }

    private Path automaticModule(final String fileName,
                                 final String moduleName,
                                 final String... classEntries) throws IOException {
        final Path jar = this.tempDir.resolve(fileName);
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", moduleName);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (final String entry : classEntries) {
                jos.putNextEntry(new JarEntry(entry));
                jos.closeEntry();
            }
        }
        return jar;
    }

    private Path properModule(final String fileName,
                              final String moduleName,
                              final List<String> requiresModules,
                              final String... classEntries) throws IOException {
        final byte[] moduleInfoBytes = ClassFile.of().buildModule(
            ModuleAttribute.of(
                ModuleDesc.of(moduleName),
                mb -> {
                    mb.requires(ModuleDesc.of("java.base"), 0, null);
                    for (final String req : requiresModules) {
                        mb.requires(ModuleDesc.of(req), 0, null);
                    }
                    // Declare every class entry's package as exported, which has the side
                    // effect of populating the Module-info Packages attribute so
                    // ModuleFinder reports them via descriptor().packages().
                    for (final String pkg : packagesOf(classEntries)) {
                        mb.exports(PackageDesc.of(pkg), 0);
                    }
                }));

        final Path jar = this.tempDir.resolve(fileName);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            jos.putNextEntry(new JarEntry("module-info.class"));
            jos.write(moduleInfoBytes);
            jos.closeEntry();
            for (final String entry : classEntries) {
                jos.putNextEntry(new JarEntry(entry));
                jos.closeEntry();
            }
        }
        return jar;
    }

    private static Set<String> packagesOf(final String[] classEntries) {
        final Set<String> packages = new LinkedHashSet<>();
        for (final String entry : classEntries) {
            final int slash = entry.lastIndexOf('/');
            if (slash > 0) {
                packages.add(entry.substring(0, slash).replace('/', '.'));
            }
        }
        return packages;
    }
}
