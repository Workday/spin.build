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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Unit tests for {@link ModuleGraphClassifier} covering the full conflict-resolution
 * pipeline: version dedupe, subset dedupe, split-package resolution (with all three tiers
 * of the demotion policy — required-module preference, proper-module preference, demote-all
 * fallback), the two public entry points {@link ModuleGraphClassifier#classify} and
 * {@link ModuleGraphClassifier#classifyAndResolve}, and the public utility methods
 * ({@code deriveBaseName}, {@code isNamedModule}, {@code jarVersionDescending}).
 *
 * <p>Tests build real on-disk jars — automatic modules via {@code Automatic-Module-Name}
 * manifest entries and proper modules via {@link ClassFile} API-generated
 * {@code module-info.class} — so {@link ModuleFinder} parses them exactly as it would at
 * runtime. This replaces {@code AbstractCompileTest} and {@code AbstractJavaLinkerTest},
 * which tested three separate hand-rolled copies of this logic that have now been
 * collapsed into this one class.
 */
class ModuleGraphClassifierTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // deriveBaseName
    // =========================================================================

    @Test
    void deriveBaseName_stripsVersionAndExtension() {
        assertThat(ModuleGraphClassifier.deriveBaseName(Path.of("jboss-logging-3.6.1.Final.jar")))
            .isEqualTo("jboss-logging");
    }

    @Test
    void deriveBaseName_stripsMultiDigitVersion() {
        assertThat(ModuleGraphClassifier.deriveBaseName(Path.of("wildfly-common-11.0.0.jar")))
            .isEqualTo("wildfly-common");
    }

    @Test
    void deriveBaseName_noVersion() {
        assertThat(ModuleGraphClassifier.deriveBaseName(Path.of("mylib.jar")))
            .isEqualTo("mylib");
    }

    @Test
    void deriveBaseName_hyphenatedNameWithVersion() {
        assertThat(ModuleGraphClassifier.deriveBaseName(Path.of("guava-33.0.0-jre.jar")))
            .isEqualTo("guava");
    }

    @Test
    void deriveBaseName_null() {
        assertThat(ModuleGraphClassifier.deriveBaseName(null)).isEqualTo("");
    }

    // =========================================================================
    // isNamedModule
    // =========================================================================

    @Test
    void isNamedModule_returnsTrueForProperModuleJar() throws IOException {
        final Path jar = properModule("test.jar", "com.example", List.of(), "com/example/Foo.class");
        assertThat(ModuleGraphClassifier.isNamedModule(jar)).isTrue();
    }

    @Test
    void isNamedModule_returnsTrueForAutomaticModuleJar() throws IOException {
        final Path jar = automaticModule("test.jar", "com.example");
        assertThat(ModuleGraphClassifier.isNamedModule(jar)).isTrue();
    }

    @Test
    void isNamedModule_returnsTrueForFilenameDerivedAutomaticJar() throws IOException {
        // Plain jar with no manifest entry — ModuleFinder still gives it a filename-derived name.
        final Path jar = createJar(tempDir.resolve("plainlib-1.0.jar"), "com/example/Foo.class");
        assertThat(ModuleGraphClassifier.isNamedModule(jar)).isTrue();
    }

    @Test
    void isNamedModule_returnsTrueForDirectoryContainingModuleInfo() throws IOException {
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.write(classesDir.resolve("module-info.class"),
            ClassFile.of().buildModule(ModuleAttribute.of(
                ModuleDesc.of("com.example"),
                mb -> mb.requires(ModuleDesc.of("java.base"), 0, null))));
        assertThat(ModuleGraphClassifier.isNamedModule(classesDir)).isTrue();
    }

    @Test
    void isNamedModule_returnsFalseForNull() {
        assertThat(ModuleGraphClassifier.isNamedModule(null)).isFalse();
    }

    @Test
    void isNamedModule_returnsFalseForNonexistentPath() {
        assertThat(ModuleGraphClassifier.isNamedModule(tempDir.resolve("nope.jar"))).isFalse();
    }

    // =========================================================================
    // jarVersionDescending — semantic ordering, not lexicographic
    // =========================================================================

    @Test
    void jarVersionDescending_prefersHigherNumericMajorVersion() throws IOException {
        // "2" > "1" lexicographically would put v2 first; semantic ordering must pick v11.
        final Path v2 = Files.createFile(tempDir.resolve("wildfly-common-2.0.0.jar"));
        final Path v11 = Files.createFile(tempDir.resolve("wildfly-common-11.0.0.jar"));

        final var sorted = new ArrayList<>(List.of(v2, v11));
        sorted.sort(ModuleGraphClassifier.jarVersionDescending());

        assertThat(sorted.get(0)).isEqualTo(v11);
    }

    @Test
    void jarVersionDescending_prefersHigherMinorVersion() throws IOException {
        final Path v13 = Files.createFile(tempDir.resolve("wildfly-common-1.3.0.Beta1.jar"));
        final Path v110 = Files.createFile(tempDir.resolve("wildfly-common-1.10.0.jar"));

        final var sorted = new ArrayList<>(List.of(v13, v110));
        sorted.sort(ModuleGraphClassifier.jarVersionDescending());

        assertThat(sorted.get(0)).isEqualTo(v110);
    }

    // =========================================================================
    // resolveConflicts — split-package resolution with tier policy
    // =========================================================================

    @Test
    void resolveConflicts_emptyWhenNoConflict() throws IOException {
        final Path named = properModule("named.jar", "mod.a", List.of(), "com/foo/A.class");
        final Path unnamed = createJar(tempDir.resolve("unnamed.jar"), "com/bar/B.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(named, unnamed), Set.of(), msg -> {});
        assertThat(resolution.demoted()).isEmpty();
        assertThat(resolution.superseded()).isEmpty();
    }

    @Test
    void resolveConflicts_properModuleWinsOverAutomatic_tierB() throws IOException {
        // Tier (b): proper module beats automatic on a shared package.
        final Path proper = properModule("proper.jar", "mod.proper",
            List.of(), "com/example/Foo.class");
        final Path automatic = createJar(tempDir.resolve("automatic.jar"), "com/example/Bar.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(proper, automatic), Set.of(), msg -> {});
        assertThat(resolution.demoted()).containsExactly(automatic);
        assertThat(resolution.superseded()).isEmpty();
    }

    @Test
    void resolveConflicts_twoAutomaticsWithNoRequiredHint_bothDemoted_tierC() throws IOException {
        // Tier (c): no proper module, no required-module hint — fall back to demoting all.
        final Path jarA = createJar(tempDir.resolve("jarA-1.0.jar"), "com/example/Foo.class");
        final Path jarB = createJar(tempDir.resolve("jarB-1.0.jar"), "com/example/Bar.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(jarA, jarB), Set.of(), msg -> {});
        assertThat(resolution.demoted()).containsExactlyInAnyOrder(jarA, jarB);
    }

    @Test
    void resolveConflicts_requiredModulePreferredOverNonRequired_tierA() throws IOException {
        // Tier (a): when one of the conflicting owners is transitively required from the
        // seed set, keep it and demote the others. Motivating case: Maven 4's maven-settings
        // vs maven-support both owning org.apache.maven.settings.v4 — we need maven.settings
        // to stay resolvable because the root `requires maven.settings`.
        final Path required = automaticModule("required-1.0.jar", "mod.required",
            "com/shared/Foo.class");
        final Path other = automaticModule("other-1.0.jar", "mod.other",
            "com/shared/Bar.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(required, other), Set.of("mod.required"), msg -> {});
        assertThat(resolution.demoted()).containsExactly(other);
        assertThat(resolution.superseded()).isEmpty();
    }

    @Test
    void resolveConflicts_multipleRequiredInConflict_fallsBackToTierC() throws IOException {
        // If two required modules own the same package, tier (a) can't pick — falls through
        // to tier (b)/(c). Neither is proper, so both end up demoted.
        final Path a = automaticModule("mod-a.jar", "mod.a", "com/shared/A.class");
        final Path b = automaticModule("mod-b.jar", "mod.b", "com/shared/B.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(a, b), Set.of("mod.a", "mod.b"), msg -> {});
        assertThat(resolution.demoted()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void resolveConflicts_supersedesOlderVersionDuplicate() throws IOException {
        // Version dedupe: same base name, different versions → older superseded.
        final Path older = automaticModule("wildfly-common-2.0.0.jar", "wildfly.common",
            "com/example/Foo.class");
        final Path newer = automaticModule("wildfly-common-11.0.0.jar", "wildfly.common",
            "com/example/Foo.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(older, newer), Set.of(), msg -> {});
        assertThat(resolution.superseded()).containsExactly(older);
        assertThat(resolution.demoted()).isEmpty();
    }

    @Test
    void resolveConflicts_subsetJarIsSuperseded() throws IOException {
        // Subset dedupe: A's packages ⊂ B's packages → A is superseded.
        final Path subset = automaticModule("lib-extra-1.0.jar", "lib.extra",
            "com/example/Foo.class");
        final Path superset = automaticModule("lib-2.0.jar", "lib.main",
            "com/example/Foo.class", "com/example/sub/Bar.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(subset, superset), Set.of(), msg -> {});
        assertThat(resolution.superseded()).containsExactly(subset);
        assertThat(resolution.demoted()).isEmpty();
    }

    @Test
    void resolveConflicts_supersededAndDemotedAreDisjoint() throws IOException {
        // A jar in the superseded set must not also appear in demoted — jdeps drops
        // superseded jars entirely, so double-listing as demoted would mis-route it.
        final Path older = automaticModule("lib-1.0.jar", "lib", "com/example/Foo.class");
        final Path newer = automaticModule("lib-2.0.jar", "lib", "com/example/Foo.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(older, newer), Set.of(), msg -> {});
        assertThat(resolution.superseded()).containsExactly(older);
        assertThat(resolution.demoted()).doesNotContain(older);
    }

    @Test
    void resolveConflicts_versionDuplicateAndSplitPackageInSameCandidateList() throws IOException {
        // Regression: candidate list containing both a version duplicate AND an unrelated
        // split-package pair. The version duplicate is superseded; the split-package pair
        // is independently demoted.
        final Path older = automaticModule("util-1.0.jar", "util", "com/util/A.class");
        final Path newer = automaticModule("util-2.0.jar", "util", "com/util/A.class");
        final Path splitA = automaticModule("split-a.jar", "split.a", "com/shared/X.class");
        final Path splitB = automaticModule("split-b.jar", "split.b", "com/shared/Y.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(older, newer, splitA, splitB), Set.of(), msg -> {});
        assertThat(resolution.superseded()).containsExactly(older);
        assertThat(resolution.demoted()).containsExactlyInAnyOrder(splitA, splitB);
        assertThat(resolution.superseded()).doesNotContainAnyElementsOf(resolution.demoted());
    }

    @Test
    void resolveConflicts_logCallbackReceivesDecisionMessages() throws IOException {
        final Path older = automaticModule("lib-1.0.jar", "lib", "com/example/Foo.class");
        final Path newer = automaticModule("lib-2.0.jar", "lib", "com/example/Foo.class");
        final var messages = new ArrayList<String>();
        ModuleGraphClassifier.resolveConflicts(List.of(older, newer), Set.of(), messages::add);
        assertThat(messages).anyMatch(msg -> msg.contains("superseded"));
    }

    @Test
    void resolveConflicts_tierBDemotion_isLogged() throws IOException {
        final Path proper = properModule("proper.jar", "mod.proper",
            List.of(), "com/example/Foo.class");
        final Path automatic = createJar(tempDir.resolve("automatic.jar"), "com/example/Bar.class");
        final var messages = new ArrayList<String>();
        ModuleGraphClassifier.resolveConflicts(List.of(proper, automatic), Set.of(), messages::add);
        assertThat(messages).anyMatch(msg -> msg.contains("automatic.jar"));
    }

    @Test
    void resolveConflicts_tierCDemotion_isLogged() throws IOException {
        final Path jarA = createJar(tempDir.resolve("jarA-1.0.jar"), "com/example/Foo.class");
        final Path jarB = createJar(tempDir.resolve("jarB-1.0.jar"), "com/example/Bar.class");
        final var messages = new ArrayList<String>();
        ModuleGraphClassifier.resolveConflicts(List.of(jarA, jarB), Set.of(), messages::add);
        assertThat(messages).anyMatch(msg -> msg.contains("demoting all"));
    }

    @Test
    void resolveConflicts_directoryExplodedModuleParticipatesInConflict() throws IOException {
        // An exploded module (directory with module-info.class) must be recognised by
        // ModuleFinder and participate in split-package analysis.
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        Files.write(classesDir.resolve("module-info.class"),
            ClassFile.of().buildModule(ModuleAttribute.of(
                ModuleDesc.of("mod.exploded"),
                mb -> {
                    mb.requires(ModuleDesc.of("java.base"), 0, null);
                    mb.exports(PackageDesc.of("com.example"), 0);
                })));
        Files.createFile(classesDir.resolve("com/example/Foo.class"));
        final Path unnamed = createJar(tempDir.resolve("unnamed.jar"), "com/example/Bar.class");
        final var resolution = ModuleGraphClassifier.resolveConflicts(
            List.of(classesDir, unnamed), Set.of(), msg -> {});
        // Proper exploded module wins over automatic via tier (b).
        assertThat(resolution.demoted()).containsExactly(unnamed);
    }

    // =========================================================================
    // classify — compile-time entry point (no Configuration.resolve)
    // =========================================================================

    @Test
    void classify_allNonConflictingJarsGoToModulePath() throws IOException {
        // Both proper and filename-derived-automatic jars without conflicts are promoted
        // to --module-path so javac can resolve them by name.
        final Path named = properModule("named.jar", "mod.a", List.of(), "com/example/Foo.class");
        final Path unnamed = createJar(tempDir.resolve("unnamed.jar"), "com/bar/Bar.class");
        final var result = ModuleGraphClassifier.classify(
            List.of(named, unnamed), Set.of(), msg -> {});
        assertThat(result.modulePath()).containsExactlyInAnyOrder(named, unnamed);
        assertThat(result.classPath()).isEmpty();
    }

    @Test
    void classify_filenameDerivedJarPromotedToModulePath() throws IOException {
        // Jars with no module-info.class and no Automatic-Module-Name still get a
        // filename-derived automatic module name from ModuleFinder; they belong on
        // --module-path so `requires <filename-derived-name>` resolves.
        final Path unnamed = createJar(tempDir.resolve("plainlib-1.0.jar"), "com/example/Foo.class");
        final var result = ModuleGraphClassifier.classify(
            List.of(unnamed), Set.of(), msg -> {});
        assertThat(result.modulePath()).containsExactly(unnamed);
        assertThat(result.classPath()).isEmpty();
    }

    @Test
    void classify_demotedJarGoesToClassPath() throws IOException {
        final Path named = properModule("named.jar", "mod.a", List.of(), "com/example/Foo.class");
        final Path conflict = createJar(tempDir.resolve("conflict.jar"), "com/example/Bar.class");
        final var result = ModuleGraphClassifier.classify(
            List.of(named, conflict), Set.of(), msg -> {});
        assertThat(result.modulePath()).containsExactly(named);
        assertThat(result.classPath()).containsExactly(conflict);
    }

    @Test
    void classify_supersededJarGoesToClassPath() throws IOException {
        // Older version duplicate is routed to classPath at compile time (caller may
        // drop it entirely, but classify surfaces it as "not on module-path").
        final Path older = automaticModule("lib-1.0.jar", "lib", "com/example/Foo.class");
        final Path newer = automaticModule("lib-2.0.jar", "lib", "com/example/Foo.class");
        final var result = ModuleGraphClassifier.classify(
            List.of(older, newer), Set.of(), msg -> {});
        assertThat(result.classPath()).containsExactly(older);
        assertThat(result.modulePath()).containsExactly(newer);
    }

    // =========================================================================
    // classifyAndResolve — launch-time entry point (runs Configuration.resolve)
    // =========================================================================

    @Test
    void classifyAndResolve_singleAutomaticRoot_returnsRoot() throws IOException {
        final Path root = automaticModule("root.jar", "my.root", "com/root/R.class");
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.ofSystem(), msg -> {});
        assertThat(result.modulePath()).containsExactly(root);
        assertThat(result.classPath()).isEmpty();
    }

    @Test
    void classifyAndResolve_properRootRequiresAutomaticDep_bothResolved() throws IOException {
        // Proper root with `requires mod.dep` → resolve walks the graph and pulls the dep
        // onto the module-path.
        final Path dep = automaticModule("dep.jar", "mod.dep", "com/dep/D.class");
        final Path root = properModule("root.jar", "my.root",
            List.of("mod.dep"), "com/root/R.class");
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root, dep), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.ofSystem(), msg -> {});
        assertThat(result.modulePath()).containsExactlyInAnyOrder(root, dep);
    }

    @Test
    void classifyAndResolve_unrequiredSplitPackageDeps_demotedAndExcluded() throws IOException {
        // Root requires only the clean dep. Two unrelated jars share a package — they
        // should be demoted and end up in classPath, not modulePath.
        final Path clean = automaticModule("clean.jar", "mod.clean", "com/clean/C.class");
        final Path conflictA = automaticModule("a.jar", "mod.a", "com/shared/A.class");
        final Path conflictB = automaticModule("b.jar", "mod.b", "com/shared/B.class");
        final Path root = properModule("root.jar", "my.root",
            List.of("mod.clean"), "com/root/R.class");
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root, clean, conflictA, conflictB), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.ofSystem(), msg -> {});
        assertThat(result.modulePath()).containsExactlyInAnyOrder(root, clean);
        assertThat(result.classPath()).containsExactlyInAnyOrder(conflictA, conflictB);
    }

    @Test
    void classifyAndResolve_requiredSplitPackageDep_keptNotDropped() throws IOException {
        // Regression for the Maven 4 maven-settings / maven-support scenario: root requires
        // mod.a which is in a split-package with mod.b. Tier (a) keeps mod.a (required),
        // demotes mod.b, and Configuration.resolve succeeds — the old "demote both" policy
        // would have thrown here.
        final Path a = automaticModule("a.jar", "mod.a", "com/shared/A.class");
        final Path b = automaticModule("b.jar", "mod.b", "com/shared/B.class");
        final Path root = properModule("root.jar", "my.root",
            List.of("mod.a"), "com/root/R.class");
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root, a, b), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.ofSystem(), msg -> {});
        assertThat(result.modulePath()).containsExactlyInAnyOrder(root, a);
        assertThat(result.classPath()).containsExactly(b);
    }

    @Test
    void classifyAndResolve_unresolvableRoot_throwsIllegalState() throws IOException {
        final Path dep = automaticModule("dep.jar", "mod.dep", "com/dep/D.class");
        assertThatThrownBy(() -> ModuleGraphClassifier.classifyAndResolve(
                List.of(dep), Set.of("nonexistent.root"), "nonexistent.root",
                Configuration.empty(), ModuleFinder.ofSystem(), msg -> {}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to resolve JPMS module graph")
            .hasMessageContaining("nonexistent.root");
    }

    @Test
    void classifyAndResolve_systemModulesNotInResult() throws IOException {
        // Configuration.resolve pulls in java.base (jrt: scheme), but the result must
        // contain only paths from the original candidate set — jlink links system modules
        // into the runtime image and copying jrt: paths would fail.
        final Path root = automaticModule("root.jar", "my.root", "com/root/R.class");
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.ofSystem(), msg -> {});
        assertThat(result.modulePath()).containsExactly(root);
        assertThat(result.modulePath())
            .allSatisfy(p -> assertThat(p.getFileName().toString()).endsWith(".jar"));
    }

    @Test
    void classifyAndResolve_unreachableJar_isLoggedAndPrunedToClasspath() throws IOException {
        // "extra" has no package conflict with anything, so resolveConflicts leaves it
        // alone — it's only pruned because the root doesn't transitively require it.
        final Path extra = automaticModule("extra.jar", "mod.extra", "com/extra/E.class");
        final Path root = properModule("root.jar", "my.root", List.of(), "com/root/R.class");
        final var messages = new ArrayList<String>();
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root, extra), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.ofSystem(), messages::add);
        assertThat(result.modulePath()).containsExactly(root);
        assertThat(result.classPath()).containsExactly(extra);
        assertThat(messages).anyMatch(msg -> msg.contains("Unreachable") && msg.contains("extra.jar"));
    }

    @Test
    void classifyAndResolve_rootInConflictWithOther_rootKeptOtherDemoted() throws IOException {
        // The root module is in the required-name set via the seed, so tier (a) keeps
        // the root and demotes the other owner of the shared package.
        final Path other = automaticModule("other.jar", "mod.other", "com/shared/O.class");
        final Path root = properModule("root.jar", "my.root", List.of(), "com/shared/R.class");
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root, other), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.ofSystem(), msg -> {});
        assertThat(result.modulePath()).containsExactly(root);
        assertThat(result.classPath()).containsExactly(other);
    }

    @Test
    void classifyAndResolve_supplementalFinderShadowsCandidate_candidateStillWins() throws IOException {
        // Regression for the self-hosted jlink-image bug: when this code runs from inside a
        // runtime image that jlink already linked the root module into, ModuleFinder.ofSystem()
        // (the supplemental finder) provides its own copy of "my.root" alongside the freshly
        // built candidate jar. Configuration.resolve only keeps one location per module name —
        // the candidate (file:) must win, not the supplemental finder's copy, or every
        // candidate looks "unreachable" and gets pruned to classpath (see
        // classifyAndResolve's javadoc on supplementalFinder).
        final Path root = automaticModule("root.jar", "my.root", "com/root/R.class");
        // A second, distinct jar declaring the same module name simulates the shadowing
        // "before" copy that a jlink image's own ModuleFinder.ofSystem() would provide.
        final Path shadow = automaticModule("root-shadow.jar", "my.root", "com/root/R.class");
        final var result = ModuleGraphClassifier.classifyAndResolve(
            List.of(root), Set.of("my.root"), "my.root",
            Configuration.empty(), ModuleFinder.compose(ModuleFinder.ofSystem(), ModuleFinder.of(shadow)),
            msg -> {});
        assertThat(result.modulePath()).containsExactly(root);
        assertThat(result.classPath()).isEmpty();
    }

    // =========================================================================
    // closeOverRequires — transitive requires walk
    // =========================================================================

    @Test
    void closeOverRequires_emptySeedReturnsEmpty() throws IOException {
        final Path any = automaticModule("any.jar", "mod.any", "com/any/A.class");
        assertThat(ModuleGraphClassifier.closeOverRequires(List.of(any), Set.of()))
            .isEmpty();
    }

    @Test
    void closeOverRequires_walksTransitiveGraph() throws IOException {
        // Root requires mid; mid requires leaf. Seed {root} should walk to all three.
        final Path leaf = automaticModule("leaf.jar", "mod.leaf", "com/leaf/L.class");
        final Path mid = properModule("mid.jar", "mod.mid",
            List.of("mod.leaf"), "com/mid/M.class");
        final Path root = properModule("root.jar", "mod.root",
            List.of("mod.mid"), "com/root/R.class");
        final Set<String> required = ModuleGraphClassifier.closeOverRequires(
            List.of(root, mid, leaf), Set.of("mod.root"));
        assertThat(required).contains("mod.root", "mod.mid", "mod.leaf", "java.base");
    }

    @Test
    void closeOverRequires_unresolvableNameStillRecorded() throws IOException {
        // A name not in the finder is still recorded — that's how the classifier's tier (a)
        // protection works for modules that sit alongside a split-package sibling.
        final Path any = automaticModule("any.jar", "mod.any", "com/any/A.class");
        final Set<String> required = ModuleGraphClassifier.closeOverRequires(
            List.of(any), Set.of("mod.ghost"));
        assertThat(required).contains("mod.ghost");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Path createJar(final Path path, final String... entries) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(path))) {
            for (final String entry : entries) {
                jos.putNextEntry(new JarEntry(entry));
                jos.closeEntry();
            }
        }
        return path;
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
                    // Declare every class entry's package as exported; this populates the
                    // module-info Packages attribute so ModuleFinder reports them.
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
