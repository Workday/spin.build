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

import build.spawn.jdk.Architecture;
import build.spawn.jdk.OperatingSystem;
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

    // --- nativePlatformFor ---

    @Test
    void nativePlatformFor_mapsKnownOsAndArchToNativeLibDirNames() {
        final var platform = AbstractJavaLinker.nativePlatformFor(
            new TargetPlatform(OperatingSystem.LINUX, Architecture.X86_64));
        assertThat(platform).contains(new AbstractJavaLinker.NativePlatform("Linux", "x86_64"));
    }

    @Test
    void nativePlatformFor_mapsMacAarch64() {
        final var platform = AbstractJavaLinker.nativePlatformFor(
            new TargetPlatform(OperatingSystem.MAC, Architecture.AARCH64));
        assertThat(platform).contains(new AbstractJavaLinker.NativePlatform("Mac", "aarch64"));
    }

    @Test
    void nativePlatformFor_mapsWindowsX86_64() {
        final var platform = AbstractJavaLinker.nativePlatformFor(
            new TargetPlatform(OperatingSystem.WINDOWS, Architecture.X86_64));
        assertThat(platform).contains(new AbstractJavaLinker.NativePlatform("Windows", "x86_64"));
    }

    @Test
    void nativePlatformFor_isEmptyForUnrecognizedOperatingSystem() {
        final var platform = AbstractJavaLinker.nativePlatformFor(
            new TargetPlatform(OperatingSystem.OTHER, Architecture.X86_64));
        assertThat(platform).isEmpty();
    }

    @Test
    void nativePlatformFor_isEmptyForUnrecognizedArchitecture() {
        final var platform = AbstractJavaLinker.nativePlatformFor(
            new TargetPlatform(OperatingSystem.LINUX, Architecture.OTHER));
        assertThat(platform).isEmpty();
    }

    // --- nativeOsArch ---

    @Test
    void nativeOsArch_returnsEmptyForEntryWithNoNativeSegment() {
        assertThat(AbstractJavaLinker.nativeOsArch("com/example/Foo.class")).isEmpty();
    }

    @Test
    void nativeOsArch_returnsEmptyWhenNativeIsAtRoot() {
        assertThat(AbstractJavaLinker.nativeOsArch("native/Linux/x86_64/libfoo.so")).isEmpty();
    }

    @Test
    void nativeOsArch_returnsEmptyWhenTooShallow() {
        assertThat(AbstractJavaLinker.nativeOsArch("com/example/native/Linux/libfoo.so")).isEmpty();
    }

    @Test
    void nativeOsArch_returnsEmptyWhenTooDeep() {
        assertThat(AbstractJavaLinker.nativeOsArch("com/example/native/Linux/x86_64/extra/libfoo.so")).isEmpty();
    }

    @Test
    void nativeOsArch_parsesStandardEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("com/example/native/Linux/x86_64/libfoo.so");
        assertThat(parts).contains(new AbstractJavaLinker.NativeJarEntry("Linux", "x86_64"));
    }

    @Test
    void nativeOsArch_parsesMetaInfEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("META-INF/native/Mac/aarch64/libbar.jnilib");
        assertThat(parts).contains(new AbstractJavaLinker.NativeJarEntry("Mac", "aarch64"));
    }

    @Test
    void nativeOsArch_parsesWindowsEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("org/sqlite/native/Windows/x86_64/sqlite.dll");
        assertThat(parts).contains(new AbstractJavaLinker.NativeJarEntry("Windows", "x86_64"));
    }

    // --- nativeOsArch: root-level convention (e.g. zstd-jni) ---

    @Test
    void nativeOsArch_parsesRootLevelLinuxEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("linux/amd64/libzstd-jni-1.5.7-6.so");
        assertThat(parts).contains(new AbstractJavaLinker.NativeJarEntry("linux", "amd64"));
    }

    @Test
    void nativeOsArch_parsesRootLevelDarwinEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("darwin/aarch64/libzstd-jni-1.5.7-6.dylib");
        assertThat(parts).contains(new AbstractJavaLinker.NativeJarEntry("darwin", "aarch64"));
    }

    @Test
    void nativeOsArch_parsesRootLevelWinEntry() {
        final var parts = AbstractJavaLinker.nativeOsArch("win/amd64/libzstd-jni-1.5.7-6.dll");
        assertThat(parts).contains(new AbstractJavaLinker.NativeJarEntry("win", "amd64"));
    }

    @Test
    void nativeOsArch_parsesRootLevelUnsupportedOsEntry() {
        // aix/freebsd have no canonical NativePlatform mapping, but still parse as native entries
        // so they're correctly treated as foreign (and stripped) rather than ignored.
        assertThat(AbstractJavaLinker.nativeOsArch("aix/ppc64/libzstd-jni-1.5.7-6.so"))
            .contains(new AbstractJavaLinker.NativeJarEntry("aix", "ppc64"));
        assertThat(AbstractJavaLinker.nativeOsArch("freebsd/amd64/libzstd-jni-1.5.7-6.so"))
            .contains(new AbstractJavaLinker.NativeJarEntry("freebsd", "amd64"));
    }

    @Test
    void nativeOsArch_doesNotMatchTwoSegmentPathsWithUnknownOsToken() {
        // exactly two segments, but "org" isn't a recognized OS token — must not false-positive
        // on ordinary two-directory-deep class/resource paths.
        assertThat(AbstractJavaLinker.nativeOsArch("org/example/Foo.class")).isEmpty();
        assertThat(AbstractJavaLinker.nativeOsArch("com/foo/Bar.class")).isEmpty();
    }

    @Test
    void nativeOsArch_preferNativeSlashConventionOverRootConvention() {
        // an entry containing "/native/" is always parsed via that convention, even if it would
        // also happen to look like a root-level two-segment path further down the string
        final var parts = AbstractJavaLinker.nativeOsArch("some/prefix/native/Linux/x86_64/lib.so");
        assertThat(parts).contains(new AbstractJavaLinker.NativeJarEntry("Linux", "x86_64"));
    }

    // --- normalizeEntryOs ---

    @Test
    void normalizeEntryOs_canonicalizesMacAliases() {
        assertThat(AbstractJavaLinker.normalizeEntryOs("darwin")).isEqualTo("Mac");
        assertThat(AbstractJavaLinker.normalizeEntryOs("macos")).isEqualTo("Mac");
        assertThat(AbstractJavaLinker.normalizeEntryOs("osx")).isEqualTo("Mac");
        assertThat(AbstractJavaLinker.normalizeEntryOs("Mac")).isEqualTo("Mac");
    }

    @Test
    void normalizeEntryOs_canonicalizesLinuxAndWindowsAliases() {
        assertThat(AbstractJavaLinker.normalizeEntryOs("linux")).isEqualTo("Linux");
        assertThat(AbstractJavaLinker.normalizeEntryOs("Linux")).isEqualTo("Linux");
        assertThat(AbstractJavaLinker.normalizeEntryOs("win")).isEqualTo("Windows");
        assertThat(AbstractJavaLinker.normalizeEntryOs("windows")).isEqualTo("Windows");
    }

    @Test
    void normalizeEntryOs_passesUnknownOsThrough() {
        assertThat(AbstractJavaLinker.normalizeEntryOs("aix")).isEqualTo("aix");
        assertThat(AbstractJavaLinker.normalizeEntryOs("freebsd")).isEqualTo("freebsd");
    }

    // --- normalizeEntryArch ---

    @Test
    void normalizeEntryArch_canonicalizesX86_64Aliases() {
        assertThat(AbstractJavaLinker.normalizeEntryArch("amd64")).isEqualTo("x86_64");
        assertThat(AbstractJavaLinker.normalizeEntryArch("x64")).isEqualTo("x86_64");
        assertThat(AbstractJavaLinker.normalizeEntryArch("x86_64")).isEqualTo("x86_64");
    }

    @Test
    void normalizeEntryArch_canonicalizesAarch64Aliases() {
        assertThat(AbstractJavaLinker.normalizeEntryArch("arm64")).isEqualTo("aarch64");
        assertThat(AbstractJavaLinker.normalizeEntryArch("aarch_64")).isEqualTo("aarch64");
        assertThat(AbstractJavaLinker.normalizeEntryArch("aarch64")).isEqualTo("aarch64");
    }

    @Test
    void normalizeEntryArch_passesUnknownArchThrough() {
        assertThat(AbstractJavaLinker.normalizeEntryArch("riscv64")).isEqualTo("riscv64");
        assertThat(AbstractJavaLinker.normalizeEntryArch("s390x")).isEqualTo("s390x");
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
    void stripForeignNatives_returnsFalseWhenAllNativesMatchOsAndArch() throws Exception {
        final var jar = buildJar(
            "com/example/Foo.class",
            "com/example/native/Linux/x86_64/libfoo.so");
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
    void stripForeignNatives_dropsForeignArchForMatchingOs() throws Exception {
        final var jar = buildJar(
            "com/example/native/Linux/x86_64/libfoo.so",
            "com/example/native/Linux/aarch64/libfoo.so",
            "com/example/native/Mac/x86_64/libfoo.jnilib");
        AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64");
        assertThat(jarEntryNames(jar)).containsExactlyInAnyOrder(
            "com/example/native/Linux/x86_64/libfoo.so");
    }

    @Test
    void stripForeignNatives_normalizesEntryArchAliases() throws Exception {
        final var jar = buildJar(
            "com/example/native/Linux/amd64/libfoo.so",      // alias for x86_64
            "com/example/native/Linux/aarch_64/libfoo.so",   // Netty alias for aarch64
            "com/example/native/Mac/x86_64/libfoo.jnilib");
        AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64");
        assertThat(jarEntryNames(jar)).containsExactlyInAnyOrder(
            "com/example/native/Linux/amd64/libfoo.so");
    }

    @Test
    void stripForeignNatives_doesNotModifyJarWhenNothingToStrip() throws Exception {
        final var jar = buildJar("com/example/Foo.class");
        final var sizeBefore = Files.size(jar);
        AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64");
        assertThat(Files.size(jar)).isEqualTo(sizeBefore);
    }

    // --- stripForeignNatives: root-level convention (e.g. zstd-jni) ---

    @Test
    void stripForeignNatives_stripsForeignPlatformsFromZstdJniStyleJar() throws Exception {
        final var jar = buildJar(
            "linux/amd64/libzstd-jni-1.5.7-6.so",
            "linux/aarch64/libzstd-jni-1.5.7-6.so",
            "darwin/aarch64/libzstd-jni-1.5.7-6.dylib",
            "darwin/x86_64/libzstd-jni-1.5.7-6.dylib",
            "win/amd64/libzstd-jni-1.5.7-6.dll",
            "aix/ppc64/libzstd-jni-1.5.7-6.so");
        assertThat(AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64")).isTrue();
        assertThat(jarEntryNames(jar)).containsExactly("linux/amd64/libzstd-jni-1.5.7-6.so");
    }

    @Test
    void stripForeignNatives_keepsMatchingZstdJniEntryForMacTarget() throws Exception {
        final var jar = buildJar(
            "linux/amd64/libzstd-jni-1.5.7-6.so",
            "darwin/aarch64/libzstd-jni-1.5.7-6.dylib",
            "win/amd64/libzstd-jni-1.5.7-6.dll");
        assertThat(AbstractJavaLinker.stripForeignNatives(jar, "Mac", "aarch64")).isTrue();
        assertThat(jarEntryNames(jar)).containsExactly("darwin/aarch64/libzstd-jni-1.5.7-6.dylib");
    }

    @Test
    void stripForeignNatives_doesNotStripOrdinaryTwoSegmentClasspathEntries() throws Exception {
        // regression guard: ordinary two-directory-deep resource/class paths must never be
        // mistaken for the root-level native convention just because they have two segments
        final var jar = buildJar("org/example/Foo.class", "com/foo/Bar.class");
        assertThat(AbstractJavaLinker.stripForeignNatives(jar, "Linux", "x86_64")).isFalse();
        assertThat(jarEntryNames(jar)).containsExactlyInAnyOrder("org/example/Foo.class", "com/foo/Bar.class");
    }
}
