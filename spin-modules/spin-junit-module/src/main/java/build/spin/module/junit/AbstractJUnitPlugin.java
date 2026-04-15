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
import build.codemodel.injection.Provides;
import build.spin.Project;
import build.spin.module.java.AbstractJavaPlugin;
import build.spin.module.java.JavaCompilerPlugin;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.TestModuleDescriptor;

import java.nio.file.Path;

/**
 * An abstract {@link JUnitPlugin} for Java-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public abstract class AbstractJUnitPlugin
    extends AbstractJavaPlugin
    implements JUnitPlugin {

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
    public ModuleDescriptor getModuleDescriptor() {

        // obtain the ModuleDescriptor
        final ModuleDescriptor moduleDescriptor = super.getModuleDescriptor();

        // establish a new ModuleDescriptor to allow patching in the Java Compiler
        // Plugin ModuleDescriptor
        final ModuleDescriptor.Builder builder = ModuleDescriptor.Builder.create(moduleDescriptor.name());
        builder.include(moduleDescriptor);

        // include test-scoped dependencies provided by a TestModuleDescriptor resource, if present
        // (e.g. from pom.xml for Maven projects without a src/test/java/module-info.java)
        this.project.findResource(TestModuleDescriptor.class)
            .ifPresent(res -> builder.include(res.get(this.project)));

        // attempt to patch in the ModuleDescriptor provided by the Java Compiler Plugin
        // (for the same version)
        this.project.plugins(JavaCompilerPlugin.class)
            .filter(plugin -> plugin.getJavaVersion().major() == getJavaVersion().major())
            .findFirst()
            .ifPresent(plugin -> {

                // include the ModuleDescriptor in the builder
                builder.include(plugin.getModuleDescriptor());

                // include the ModuleDescriptor version in the builder
                // (the test module has the same version as the Java module)
                moduleDescriptor.version().ifPresent(builder::setVersion);

                // TODO: warn if the module names are different
            });

        return builder.build();
    }

}
