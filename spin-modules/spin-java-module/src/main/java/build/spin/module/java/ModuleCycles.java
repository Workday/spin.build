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

import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;

/**
 * Detects cyclic {@code requires} relationships between {@link JDKModuleDescriptor}s.
 */
final class ModuleCycles {

    private ModuleCycles() {
    }

    /**
     * Throws if {@code candidate} requires {@code thisModuleName} — i.e. {@code thisModuleName} requiring
     * {@code candidate} would form a cycle.
     *
     * @param candidate      the {@link JDKModuleDescriptor} being considered as a dependency
     * @param thisModuleName the name of the module declaring the requirement on {@code candidate}
     * @throws IllegalStateException if {@code candidate} requires {@code thisModuleName}
     */
    static void checkNotCyclic(final JDKModuleDescriptor candidate, final ModuleName thisModuleName) {
        if (candidate.requiresClauses().anyMatch(r -> r.requiresModuleName().equals(thisModuleName))) {
            throw new IllegalStateException(
                "Cyclic module dependency detected: [%s] requires [%s], which itself requires [%s]"
                    .formatted(thisModuleName, candidate.moduleName(), thisModuleName));
        }
    }
}
