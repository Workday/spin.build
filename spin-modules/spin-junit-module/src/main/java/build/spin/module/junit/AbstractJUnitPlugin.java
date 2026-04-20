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

import build.base.option.JDKVersion;
import build.codemodel.foundation.CodeModel;
import build.codemodel.injection.Provides;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spin.Project;
import build.spin.module.java.AbstractJavaPlugin;
import build.spin.module.java.JavaCompilerPlugin;
import build.spin.module.modulesystem.TestModuleDescriptor;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An abstract {@link JUnitPlugin} for Java-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public abstract class AbstractJUnitPlugin
    extends AbstractJavaPlugin
    implements JUnitPlugin {

    @Inject
    private CodeModel codeModel;

    private final AtomicReference<JDKModuleDescriptor> testModuleDescriptor = new AtomicReference<>();

    /**
     * The {@link JDKVersion} for the {@link JUnitPlugin}.
     */
    private final JDKVersion javaVersion;

    /**
     * Constructs an {@link AbstractJUnitPlugin}.
     *
     * @param javaVersion the {@link JDKVersion}
     */
    protected AbstractJUnitPlugin(final JDKVersion javaVersion) {
        this.javaVersion = javaVersion;
    }

    @Override
    public JDKVersion getJavaVersion() {
        return this.javaVersion;
    }

    @Override
    protected Path getSourceRootPath() {
        return this.project.path().resolve("src/test/");
    }

    @Override
    @Provides
    public JDKModuleDescriptor getModuleDescriptor() {
        return this.testModuleDescriptor.updateAndGet(descriptor -> {
            if (descriptor == null) {
                final JDKModuleDescriptor base = super.getModuleDescriptor();
                // Use of() rather than createModuleDescriptor() so this test-augmented view does not
                // overwrite the canonical descriptor already registered by the compiler plugin's parse().
                final JDKModuleDescriptor merged = JDKModuleDescriptor.of(this.codeModel, base.moduleName());
                merged.include(base);

                this.project.findResource(TestModuleDescriptor.class)
                    .ifPresent(res -> merged.include(res.get(this.project)));

                this.project.plugins(JavaCompilerPlugin.class)
                    .filter(plugin -> plugin.getJavaVersion().major() == getJavaVersion().major())
                    .findFirst()
                    .ifPresent(plugin -> merged.include(plugin.getModuleDescriptor()));

                return merged;
            }
            return descriptor;
        });
    }

}
