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

import build.percolate.core.ModuleGraphClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JmodModuleFinder}, covering the case that motivated it: making a
 * target JDK's {@code jmods/} platform modules resolvable via {@link java.lang.module.Configuration#resolve}
 * without ever opening a {@code .jmod} at execution time (see {@code AbstractJavaLinker#classifyModulePath}).
 */
class JmodModuleFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void of_findsModuleByName() throws IOException {
        jmod("java.base.jmod", "java.base");
        jmod("java.sql.jmod", "java.sql", "java.base");

        final ModuleFinder finder = JmodModuleFinder.of(this.tempDir);

        assertThat(finder.find("java.sql")).isPresent();
        assertThat(finder.find("java.sql").get().descriptor().name()).isEqualTo("java.sql");
    }

    @Test
    void of_findAllReturnsEveryJmod() throws IOException {
        jmod("java.base.jmod", "java.base");
        jmod("java.sql.jmod", "java.sql", "java.base");
        jmod("java.logging.jmod", "java.logging", "java.base");

        final ModuleFinder finder = JmodModuleFinder.of(this.tempDir);

        assertThat(finder.findAll().stream().map(r -> r.descriptor().name()))
            .containsExactlyInAnyOrder("java.base", "java.sql", "java.logging");
    }

    @Test
    void of_unknownModuleNameIsEmpty() throws IOException {
        jmod("java.base.jmod", "java.base");

        final ModuleFinder finder = JmodModuleFinder.of(this.tempDir);

        assertThat(finder.find("does.not.exist")).isEmpty();
    }

    @Test
    void of_descriptorReflectsRequires() throws IOException {
        jmod("java.base.jmod", "java.base");
        jmod("java.sql.jmod", "java.sql", "java.base");

        final ModuleFinder finder = JmodModuleFinder.of(this.tempDir);

        final var descriptor = finder.find("java.sql").orElseThrow().descriptor();
        assertThat(descriptor.requires().stream().map(r -> r.name())).contains("java.base");
    }

    @Test
    void of_ignoresNonJmodFiles() throws IOException {
        jmod("java.base.jmod", "java.base");
        Files.writeString(this.tempDir.resolve("README.txt"), "not a jmod");

        final ModuleFinder finder = JmodModuleFinder.of(this.tempDir);

        assertThat(finder.findAll()).hasSize(1);
    }

    @Test
    void of_skipsJmodWithoutModuleInfo() throws IOException {
        // a malformed/partial .jmod with no classes/module-info.class entry at all
        final Path malformed = this.tempDir.resolve("broken.jmod");
        try (var zos = new ZipOutputStream(Files.newOutputStream(malformed))) {
            zos.putNextEntry(new ZipEntry("classes/some/Other.class"));
            zos.closeEntry();
        }
        jmod("java.base.jmod", "java.base");

        final ModuleFinder finder = JmodModuleFinder.of(this.tempDir);

        assertThat(finder.findAll().stream().map(r -> r.descriptor().name())).containsExactly("java.base");
    }

    @Test
    void reference_openThrowsInsteadOfReadingTheJmod() throws IOException {
        jmod("java.base.jmod", "java.base");

        final ModuleFinder finder = JmodModuleFinder.of(this.tempDir);
        final ModuleReference reference = finder.find("java.base").orElseThrow();

        assertThatThrownBy(reference::open).isInstanceOf(UnsupportedOperationException.class);
    }

    // =========================================================================
    // classifyAndResolve interplay: these use real java.lang.module.Configuration#resolve,
    // but against hand-built module jars/descriptors, not a real jmods/ directory or the
    // jlink pipeline — see JavaProjectTests#shouldLinkRootModuleThatRequiresAJdkPlatformModule
    // in spin-java-module-tests for the actual end-to-end jlink integration test covering
    // the "Module jdk.compiler not found" failure this was built to fix.
    // =========================================================================

    @Test
    void classifyAndResolve_withoutJmodModuleFinder_reproducesJdkCompilerNotFoundFailure() throws IOException {
        final Path rootJar = properModuleRequiring("root.jar", "the.root.module", "jdk.compiler");

        // Stands in for a running spin image whose own ModuleFinder.ofSystem() was jlink'd
        // without jdk.compiler on board — exactly the reported laptop's failure mode. Still
        // needs java.base, same as any real system module finder would provide.
        final Path jmodsDir = Files.createDirectory(this.tempDir.resolve("jmods-no-compiler"));
        jmod(jmodsDir, "java.base.jmod", "java.base");
        final ModuleFinder noCompilerOnBoard = JmodModuleFinder.of(jmodsDir);

        assertThatThrownBy(() -> ModuleGraphClassifier.classifyAndResolve(
            List.of(rootJar),
            Set.of("the.root.module"),
            "the.root.module",
            Configuration.empty(),
            noCompilerOnBoard,
            msg -> {
            }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Module jdk.compiler not found")
            .hasMessageContaining("the.root.module");
    }

    @Test
    void classifyAndResolve_withJmodModuleFinderOverTargetJmods_resolvesJdkCompiler() throws IOException {
        final Path rootJar = properModuleRequiring("root.jar", "the.root.module", "jdk.compiler");

        // A target JDK's real jmods/ directory, standing in for the one AbstractJavaLinker
        // resolves via JavaPlatform — it carries every standard module, including jdk.compiler,
        // regardless of what's trimmed into the currently-running spin image.
        final Path jmodsDir = Files.createDirectory(this.tempDir.resolve("jmods"));
        jmod(jmodsDir, "java.base.jmod", "java.base");
        jmod(jmodsDir, "jdk.compiler.jmod", "jdk.compiler", "java.base");

        final ModuleFinder supplementalFinder = JmodModuleFinder.of(jmodsDir);

        final var classification = ModuleGraphClassifier.classifyAndResolve(
            List.of(rootJar),
            Set.of("the.root.module"),
            "the.root.module",
            Configuration.empty(),
            supplementalFinder,
            msg -> {
            });

        assertThat(classification.modulePath()).containsExactly(rootJar);
    }

    private Path properModuleRequiring(final String fileName, final String moduleName, final String... requires)
        throws IOException {
        final byte[] moduleInfoBytes = ClassFile.of().buildModule(
            ModuleAttribute.of(
                ModuleDesc.of(moduleName),
                mb -> {
                    mb.requires(ModuleDesc.of("java.base"), 0, null);
                    for (final String req : requires) {
                        mb.requires(ModuleDesc.of(req), 0, null);
                    }
                }));

        final Path jar = this.tempDir.resolve(fileName);
        try (var jos = new JarOutputStream(Files.newOutputStream(jar))) {
            jos.putNextEntry(new JarEntry("module-info.class"));
            jos.write(moduleInfoBytes);
            jos.closeEntry();
        }
        return jar;
    }

    /**
     * Writes a minimal {@code .jmod}-shaped zip: a {@code classes/module-info.class} entry
     * built with the {@link ClassFile} API, same as a real {@code jmod create} output would
     * carry (just without the {@code JM} magic header or the other top-level directories
     * {@code bin/}, {@code lib/}, etc. — {@link JmodModuleFinder} only ever reads the
     * {@code classes/module-info.class} entry, so those are irrelevant to what's under test).
     */
    private Path jmod(final String fileName, final String moduleName, final String... requiresModules)
        throws IOException {
        return jmod(this.tempDir, fileName, moduleName, requiresModules);
    }

    private Path jmod(final Path dir, final String fileName, final String moduleName,
                      final String... requiresModules) throws IOException {
        final byte[] moduleInfoBytes = ClassFile.of().buildModule(
            ModuleAttribute.of(
                ModuleDesc.of(moduleName),
                mb -> {
                    for (final String req : requiresModules) {
                        mb.requires(ModuleDesc.of(req), 0, null);
                    }
                    mb.exports(PackageDesc.of(moduleName), 0);
                }));

        final Path jmod = dir.resolve(fileName);
        try (var zos = new ZipOutputStream(Files.newOutputStream(jmod))) {
            zos.putNextEntry(new ZipEntry("classes/module-info.class"));
            zos.write(moduleInfoBytes);
            zos.closeEntry();
        }
        return jmod;
    }
}
