package build.spin.module.junit;

/*-
 * #%L
 * Spin Junit Module
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

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ModuleDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractTestTest {

    @TempDir
    Path tempDir;

    // this is the regression this test exists to catch: a workspace sibling that hasn't been
    // packaged into a jar yet sits on the module path as an exploded module directory
    // (module-info.class directly at its root) — namedModuleNames must not skip those.
    @Test
    void namedModuleNames_explodedModuleDirectory_isDiscovered() throws Exception {
        final Path moduleDir = tempDir.resolve("exploded");
        Files.createDirectories(moduleDir);
        Files.write(moduleDir.resolve("module-info.class"), moduleInfoBytes("build.base.assertion"));

        assertThat(AbstractTest.namedModuleNames(Stream.of(moduleDir)))
            .containsExactly("build.base.assertion");
    }

    @Test
    void namedModuleNames_packagedModularJar_isDiscovered() throws Exception {
        final Path jar = tempDir.resolve("assertj-core.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            jos.putNextEntry(new JarEntry("module-info.class"));
            jos.write(moduleInfoBytes("org.assertj.core"));
            jos.closeEntry();
        }

        assertThat(AbstractTest.namedModuleNames(Stream.of(jar)))
            .containsExactly("org.assertj.core");
    }

    @Test
    void namedModuleNames_mixOfDirectoryAndJarModules_discoversBoth() throws Exception {
        final Path moduleDir = tempDir.resolve("exploded");
        Files.createDirectories(moduleDir);
        Files.write(moduleDir.resolve("module-info.class"), moduleInfoBytes("build.base.assertion"));

        final Path jar = tempDir.resolve("assertj-core.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            jos.putNextEntry(new JarEntry("module-info.class"));
            jos.write(moduleInfoBytes("org.assertj.core"));
            jos.closeEntry();
        }

        assertThat(AbstractTest.namedModuleNames(Stream.of(moduleDir, jar)))
            .containsExactlyInAnyOrder("build.base.assertion", "org.assertj.core");
    }

    @Test
    void namedModuleNames_directoryWithoutModuleInfo_isSkippedWithoutThrowing() throws Exception {
        final Path plainDir = tempDir.resolve("resources");
        Files.createDirectories(plainDir);
        Files.writeString(plainDir.resolve("some.txt"), "not a module");

        assertThat(AbstractTest.namedModuleNames(Stream.of(plainDir))).isEmpty();
    }

    @Test
    void namedModuleNames_jarWithoutModuleInfo_isDiscoveredAsAutomaticModule() throws Exception {
        // a jar with no module-info.class and no Automatic-Module-Name manifest entry is still
        // a valid (automatic) module to the JDK — ModuleFinder derives its name from the
        // filename, so this is discovered rather than skipped.
        final Path jar = tempDir.resolve("plain.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            // no module-info.class entry
        }

        assertThat(AbstractTest.namedModuleNames(Stream.of(jar))).containsExactly("plain");
    }

    private static byte[] moduleInfoBytes(final String moduleName) {
        return ClassFile.of().buildModule(
            ModuleAttribute.of(ModuleDesc.of(moduleName), mb -> mb.requires(ModuleDesc.of("java.base"), 0, null)));
    }
}
