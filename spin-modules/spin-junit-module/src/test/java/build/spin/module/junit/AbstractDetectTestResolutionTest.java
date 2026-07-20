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

import build.base.version.Version;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractDetectTestResolutionTest {

    @Test
    void jupiterVersion_versioningHasNoEntry_fallsBackToFiveDotSixDotZero() {
        assertThat(AbstractDetectTestResolution.jupiterVersion(moduleName -> Optional.empty()))
            .isEqualTo("5.6.0");
    }

    // this is the regression this test exists to catch: PomDependencyGraphWalker registers
    // dependency versions under the real JPMS module name read from module-info.class
    // (org.junit.jupiter.api), never the bare groupId (org.junit.jupiter) — so the lookup
    // key here must match the real module name or it silently falls back to 5.6.0 forever.
    @Test
    void jupiterVersion_looksUpByRealModuleNameNotGroupId() {
        final String jupiterVersion = AbstractDetectTestResolution.jupiterVersion(
            moduleName -> "org.junit.jupiter.api".equals(moduleName)
                ? Optional.of(Version.parse("6.0.3"))
                : Optional.empty());

        assertThat(jupiterVersion).isEqualTo("6.0.3");
    }

    @Test
    void jupiterVersion_versioningOnlyHasBareGroupIdEntry_isNotFound() {
        // guards against silently "fixing" the lookup key back to the groupId — a versioning
        // implementation that (incorrectly) only registers the groupId must still miss.
        final String jupiterVersion = AbstractDetectTestResolution.jupiterVersion(
            moduleName -> "org.junit.jupiter".equals(moduleName)
                ? Optional.of(Version.parse("6.0.3"))
                : Optional.empty());

        assertThat(jupiterVersion).isEqualTo("5.6.0");
    }

    @Test
    void jupiterMajorVersion_parsesMajorFromDottedVersion() {
        assertThat(AbstractDetectTestResolution.jupiterMajorVersion("6.0.3")).isEqualTo(6);
        assertThat(AbstractDetectTestResolution.jupiterMajorVersion("5.6.0")).isEqualTo(5);
    }

    @Test
    void jupiterMajorVersion_unparsableVersion_returnsZero() {
        assertThat(AbstractDetectTestResolution.jupiterMajorVersion("not-a-version")).isEqualTo(0);
    }

    @Test
    void derivePlatformVersion_junit5_prefixesMajorWithOne() {
        assertThat(AbstractDetectTestResolution.derivePlatformVersion("5.6.0")).isEqualTo("1.6.0");
    }

    @Test
    void derivePlatformVersion_junit6_matchesJupiterVersionExactly() {
        assertThat(AbstractDetectTestResolution.derivePlatformVersion("6.0.3")).isEqualTo("6.0.3");
    }
}
