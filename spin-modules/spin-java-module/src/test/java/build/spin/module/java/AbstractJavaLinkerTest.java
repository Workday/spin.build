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

import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractJavaLinkerTest {

    @TempDir
    Path tempDir;

    // --- nativeOsArch ---

    @Test
    void nativeOsArch_returnsNullForEntryWithNoNativeSegment() {
        assertThat(AbstractJavaLinker.nativeOsArch("com/example/Foo.class")).isNull();
    }

    @Test
    void nativeOsArch_returnsNullWhenNativeIsAtRoot() {
        assertThat(AbstractJavaLinker.nativeOsArch("native/Linux/x86_64/libfoo.so")).isNull();
    }

    @Test
    void nativeOsArch_returnsNullWhenTooShallow() {
        assertThat(AbstractJavaLinker.nativeOsArch("com/example/native/Linux/libfoo.so")).isNull();
    }

    @Test
    void nativeOsArch_returnsNullWhenTooDeep() {
        assertThat(AbstractJavaLinker.nativeOsArch("com/example/native/Linux/x86_64/extra/libfoo.so")).isNull();
    }

    @Test
    void nativeOsArch_parsesStandardEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("com/example/native/Linux/x86_64/libfoo.so");
        assertThat(parts).containsExactly("Linux", "x86_64");
    }

    @Test
    void nativeOsArch_parsesMetaInfEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("META-INF/native/Mac/aarch64/libbar.jnilib");
        assertThat(parts).containsExactly("Mac", "aarch64");
    }

    @Test
    void nativeOsArch_parsesWindowsEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("org/sqlite/native/Windows/x86_64/sqlite.dll");
        assertThat(parts).containsExactly("Windows", "x86_64");
    }

    // --- stripForeignNatives ---

    private Path buildJar(final String... entryNames) throws Exception {
        final var jar = tempDir.resolve("test.jar");
        try (var zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(jar)))) {
            for (final var name : entryNames) {
                zout.putNextEntry(new ZipEntry(name));
                zout.write(new byte[]{0x42});
                zout.closeEntry();
            }
        }
        return jar;
    }

    private java.util.List<String> jarEntryNames(final Path jar) throws Exception {
        try (var zf = new ZipFile(jar.toFile())) {
            final var names = new java.util.ArrayList<String>();
            for (final var e = zf.entries(); e.hasMoreElements(); ) {
                names.add(e.nextElement().getName());
            }
            return names;
        }
    }

    @Test
    void stripForeignNatives_returnsFalseForJarWithNoNativeEntries() throws Exception {
        final var jar = buildJar("com/example/Foo.class", "com/example/Bar.class");
        assertThat(AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64")).isFalse();
    }

    @Test
    void stripForeignNatives_returnsFalseWhenAllNativesMatchOs() throws Exception {
        final var jar = buildJar(
            "com/example/Foo.class",
            "com/example/native/Linux/x86_64/libfoo.so",
            "com/example/native/Linux/aarch64/libfoo.so");
        assertThat(AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64")).isFalse();
    }

    @Test
    void stripForeignNatives_returnsTrueAndDropsForeignOsEntries() throws Exception {
        final var jar = buildJar(
            "com/example/Foo.class",
            "com/example/native/Linux/x86_64/libfoo.so",
            "com/example/native/Mac/x86_64/libfoo.jnilib",
            "com/example/native/Windows/x86_64/foo.dll");
        assertThat(AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64")).isTrue();
        assertThat(jarEntryNames(jar)).containsExactlyInAnyOrder(
            "com/example/Foo.class",
            "com/example/native/Linux/x86_64/libfoo.so");
    }

    @Test
    void stripForeignNatives_keepsAllArchVariantsForMatchingOs() throws Exception {
        final var jar = buildJar(
            "com/example/native/Linux/x86_64/libfoo.so",
            "com/example/native/Linux/aarch64/libfoo.so",
            "com/example/native/Mac/x86_64/libfoo.jnilib");
        AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64");
        assertThat(jarEntryNames(jar)).containsExactlyInAnyOrder(
            "com/example/native/Linux/x86_64/libfoo.so",
            "com/example/native/Linux/aarch64/libfoo.so");
    }

    @Test
    void stripForeignNatives_doesNotModifyJarWhenNothingToStrip() throws Exception {
        final var jar = buildJar("com/example/Foo.class");
        final var sizeBefore = Files.size(jar);
        AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64");
        assertThat(Files.size(jar)).isEqualTo(sizeBefore);
    }
}
