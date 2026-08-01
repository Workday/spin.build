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

import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;

import org.junit.jupiter.api.Test;

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link ModuleCycles#checkNotCyclic(JDKModuleDescriptor, ModuleName)}, the shared
 * cycle-detection check used by {@link AbstractCompile}, {@link AbstractJavaDependencyAnalysis}, and
 * {@link CustomizationPlugin} when resolving each of their required modules.
 */
class ModuleCyclesTest {

    private static CodeModel newCodeModel() {
        return new JDKCodeModel(new NonCachingNameProvider());
    }

    private static ModuleName moduleName(final CodeModel codeModel, final String name) {
        return codeModel.getNameProvider().getModuleName(name).orElseThrow();
    }

    private static JDKModuleDescriptor parse(final CodeModel codeModel, final String source) throws ParseException {
        return JDKModuleDescriptor.parse(codeModel, source);
    }

    @Test
    void checkNotCyclic_candidateDoesNotRequireThisModule_doesNotThrow() throws ParseException {
        final CodeModel codeModel = newCodeModel();
        final JDKModuleDescriptor candidate = parse(codeModel, """
            module candidate.module {
                requires some.other.module;
            }
            """);

        assertThatCode(() ->
            ModuleCycles.checkNotCyclic(candidate, moduleName(codeModel, "this.module")))
            .doesNotThrowAnyException();
    }

    @Test
    void checkNotCyclic_candidateHasNoRequires_doesNotThrow() throws ParseException {
        final CodeModel codeModel = newCodeModel();
        final JDKModuleDescriptor candidate = parse(codeModel, """
            module candidate.module {
            }
            """);

        assertThatCode(() ->
            ModuleCycles.checkNotCyclic(candidate, moduleName(codeModel, "this.module")))
            .doesNotThrowAnyException();
    }

    @Test
    void checkNotCyclic_candidateRequiresThisModule_throwsIllegalStateException() throws ParseException {
        final CodeModel codeModel = newCodeModel();
        final JDKModuleDescriptor candidate = parse(codeModel, """
            module candidate.module {
                requires this.module;
            }
            """);

        assertThatThrownBy(() ->
            ModuleCycles.checkNotCyclic(candidate, moduleName(codeModel, "this.module")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("this.module")
            .hasMessageContaining("candidate.module");
    }

    @Test
    void checkNotCyclic_candidateRequiresThisModuleAmongOthers_throwsIllegalStateException() throws ParseException {
        final CodeModel codeModel = newCodeModel();
        final JDKModuleDescriptor candidate = parse(codeModel, """
            module candidate.module {
                requires some.other.module;
                requires this.module;
            }
            """);

        assertThatThrownBy(() ->
            ModuleCycles.checkNotCyclic(candidate, moduleName(codeModel, "this.module")))
            .isInstanceOf(IllegalStateException.class);
    }
}
