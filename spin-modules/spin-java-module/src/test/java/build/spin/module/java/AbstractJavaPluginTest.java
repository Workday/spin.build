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

import build.base.version.Version;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies {@link AbstractJavaPlugin#stampVersion(JDKModuleDescriptor, Optional)}, which attaches a
 * {@link Version} to a workspace project's source-parsed {@link JDKModuleDescriptor} — {@code module-info.java}
 * has no version syntax, so a parsed descriptor never carries one on its own.
 */
class AbstractJavaPluginTest {

    private static final CodeModel CODE_MODEL = new JDKCodeModel(new NonCachingNameProvider());

    private static JDKModuleDescriptor descriptor(final String moduleName) {
        return JDKModuleDescriptor.of(CODE_MODEL, CODE_MODEL.getNameProvider().getModuleName(moduleName).orElseThrow());
    }

    @Test
    void stampVersion_addsTheVersionWhenPresent() {
        final JDKModuleDescriptor descriptor = descriptor("example.module.one");

        AbstractJavaPlugin.stampVersion(descriptor, Optional.of(Version.parse("1.2.3")));

        assertThat(descriptor.version()).contains(Version.parse("1.2.3"));
    }

    @Test
    void stampVersion_doesNothingWhenVersionIsEmpty() {
        final JDKModuleDescriptor descriptor = descriptor("example.module.two");

        AbstractJavaPlugin.stampVersion(descriptor, Optional.empty());

        assertThat(descriptor.version()).isEmpty();
    }

    /**
     * Reproduces the crash seen when two sibling plugins for the same project each call
     * {@code getModuleDescriptor()}: both obtain the same descriptor instance from the shared
     * CodeModel registry and both attempt to stamp it, which previously threw
     * {@code IllegalArgumentException: Trait [VersionTrait] ... already exists}.
     */
    @Test
    void stampVersion_reapplyingToTheSameSharedDescriptorDoesNotThrow() {
        final JDKModuleDescriptor descriptor = descriptor("example.module.three");

        AbstractJavaPlugin.stampVersion(descriptor, Optional.of(Version.parse("1.0.0")));

        assertThatCode(() -> AbstractJavaPlugin.stampVersion(descriptor, Optional.of(Version.parse("1.0.0"))))
            .doesNotThrowAnyException();
        assertThat(descriptor.version()).contains(Version.parse("1.0.0"));
    }
}
