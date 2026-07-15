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

import build.base.option.JDKVersion;
import build.spawn.jdk.Architecture;
import build.spawn.jdk.JDK;
import build.spawn.jdk.OperatingSystem;
import build.spawn.jdk.option.JDKHome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaPlatform}, in particular the target-platform-aware {@link JDK} lookups added to
 * support generating a {@code jlink} runtime image per staged target platform.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
class JavaPlatformTest {

    private static JDK jdk(final String version, final OperatingSystem os, final Architecture arch, final String home) {
        return JDK.of(JDKVersion.of(version), JDKHome.of(home), os, arch);
    }

    @Test
    void targets_returnsDistinctPlatformsAcrossJDKsOfTheSameVersion() {
        // same version, different platforms — the historically buggy case where a version-only
        // comparator would treat these as duplicates and silently drop one
        final var linux = jdk("25.0.3", OperatingSystem.LINUX, Architecture.X86_64, "/usr/lib/jvm/zulu25-linux");
        final var mac = jdk("25.0.3", OperatingSystem.MAC, Architecture.AARCH64, "/usr/lib/jvm/zulu25-mac");
        final var platform = new JavaPlatform(List.of(linux, mac));

        assertThat(platform.targets()).containsExactlyInAnyOrder(
            new TargetPlatform(OperatingSystem.LINUX, Architecture.X86_64),
            new TargetPlatform(OperatingSystem.MAC, Architecture.AARCH64));
    }

    @Test
    void stream_retainsBothJDKsWhenVersionsMatchButPlatformsDiffer() {
        final var linux = jdk("25.0.3", OperatingSystem.LINUX, Architecture.X86_64, "/usr/lib/jvm/zulu25-linux");
        final var mac = jdk("25.0.3", OperatingSystem.MAC, Architecture.AARCH64, "/usr/lib/jvm/zulu25-mac");
        final var platform = new JavaPlatform(List.of(linux, mac));

        assertThat(platform.stream()).containsExactlyInAnyOrder(linux, mac);
    }

    @Test
    void getVersion_withTarget_findsTheJDKMatchingBothMajorVersionAndTarget() {
        final var linux25 = jdk("25.0.3", OperatingSystem.LINUX, Architecture.X86_64, "/usr/lib/jvm/zulu25-linux");
        final var mac25 = jdk("25.0.3", OperatingSystem.MAC, Architecture.AARCH64, "/usr/lib/jvm/zulu25-mac");
        final var mac21 = jdk("21.0.1", OperatingSystem.MAC, Architecture.AARCH64, "/usr/lib/jvm/zulu21-mac");
        final var platform = new JavaPlatform(List.of(linux25, mac25, mac21));

        final var target = new TargetPlatform(OperatingSystem.MAC, Architecture.AARCH64);

        assertThat(platform.getVersion(25, target)).contains(mac25);
        assertThat(platform.getVersion(21, target)).contains(mac21);
    }

    @Test
    void getVersion_withTarget_isEmptyWhenNoJDKMatchesTheTarget() {
        final var linux25 = jdk("25.0.3", OperatingSystem.LINUX, Architecture.X86_64, "/usr/lib/jvm/zulu25-linux");
        final var platform = new JavaPlatform(List.of(linux25));

        final var windowsTarget = new TargetPlatform(OperatingSystem.WINDOWS, Architecture.X86_64);

        assertThat(platform.getVersion(25, windowsTarget)).isEmpty();
    }

    @Test
    void getLatest_withTarget_returnsHighestVersionForThatTargetOnly() {
        final var mac21 = jdk("21.0.1", OperatingSystem.MAC, Architecture.AARCH64, "/usr/lib/jvm/zulu21-mac");
        final var mac25 = jdk("25.0.3", OperatingSystem.MAC, Architecture.AARCH64, "/usr/lib/jvm/zulu25-mac");
        final var linux30 = jdk("30.0.0", OperatingSystem.LINUX, Architecture.X86_64, "/usr/lib/jvm/zulu30-linux");
        final var platform = new JavaPlatform(List.of(mac21, mac25, linux30));

        final var target = new TargetPlatform(OperatingSystem.MAC, Architecture.AARCH64);

        assertThat(platform.getLatest(target)).contains(mac25);
    }

    // --- host-platform scoping for getVersion(major)/getLatest()/getEarliest() ---
    //
    // These overloads (unlike their *(..., target) counterparts) return a JDK intended to be executed
    // directly on this host (e.g. to run javac) — a same-version JDK staged only for a foreign target
    // platform must never be returned, since it can't run here. This is the actual bug: a version-only
    // tie-break previously let a foreign-platform JDK win non-deterministically over the host one.

    private static OperatingSystem foreignOperatingSystem() {
        return OperatingSystem.current() == OperatingSystem.LINUX ? OperatingSystem.MAC : OperatingSystem.LINUX;
    }

    private static Architecture foreignArchitecture() {
        return Architecture.current() == Architecture.X86_64 ? Architecture.AARCH64 : Architecture.X86_64;
    }

    @Test
    void getVersion_withoutTarget_neverReturnsAForeignPlatformJDKEvenWhenItSortsFirst() {
        final var host = jdk("25.0.3", OperatingSystem.current(), Architecture.current(), "/usr/lib/jvm/zulu25-host");
        // deliberately given a "AARCH64"-style home/name that would previously have sorted before the
        // host JDK under a naive alphabetical tie-break on OS/arch name
        final var foreign = jdk("25.0.3", foreignOperatingSystem(), foreignArchitecture(), "/usr/lib/jvm/aaa-foreign");
        final var platform = new JavaPlatform(List.of(host, foreign));

        assertThat(platform.getVersion(25)).contains(host);
    }

    @Test
    void getLatest_withoutTarget_onlyConsidersHostPlatformJDKs() {
        final var hostOlder = jdk("21.0.1", OperatingSystem.current(), Architecture.current(), "/usr/lib/jvm/zulu21-host");
        final var foreignNewer = jdk("30.0.0", foreignOperatingSystem(), foreignArchitecture(), "/usr/lib/jvm/zulu30-foreign");
        final var platform = new JavaPlatform(List.of(hostOlder, foreignNewer));

        assertThat(platform.getLatest()).contains(hostOlder);
    }

    @Test
    void getEarliest_withoutTarget_onlyConsidersHostPlatformJDKs() {
        final var hostNewer = jdk("25.0.3", OperatingSystem.current(), Architecture.current(), "/usr/lib/jvm/zulu25-host");
        final var foreignOlder = jdk("17.0.1", foreignOperatingSystem(), foreignArchitecture(), "/usr/lib/jvm/zulu17-foreign");
        final var platform = new JavaPlatform(List.of(hostNewer, foreignOlder));

        assertThat(platform.getEarliest()).contains(hostNewer);
    }

    @Test
    void getVersion_withoutTarget_isEmptyWhenOnlyForeignPlatformJDKsMatchTheVersion() {
        final var foreign = jdk("25.0.3", foreignOperatingSystem(), foreignArchitecture(), "/usr/lib/jvm/zulu25-foreign");
        final var platform = new JavaPlatform(List.of(foreign));

        assertThat(platform.getVersion(25)).isEmpty();
    }

    @Test
    void hostTarget_matchesTheCurrentlyExecutingVirtualMachinesPlatform() {
        assertThat(JavaPlatform.hostTarget())
            .isEqualTo(new TargetPlatform(OperatingSystem.current(), Architecture.current()));
    }
}
