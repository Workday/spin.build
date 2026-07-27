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
import build.codemodel.dependency.injection.Provides;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.spawn.jdk.JDK;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;

/**
 * A {@link Plugin} defining {@link Task}s for a Java-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Jun-2020
 */
public interface JavaPlugin
    extends Plugin {

    /**
     * Obtains the {@link JDKVersion} for which the {@link JavaPlugin} is designed to operate.
     *
     * @return the {@link JDKVersion}
     */
    @Provides
    JDKVersion getJavaVersion();

    /**
     * Obtains the {@link JDK} to be used by {@link Task}s defined by the {@link JavaPlugin}
     * {@link JDKVersion}.
     *
     * @return the {@link JDK}
     */
    @Provides
    JDK getJDK();

    /**
     * Obtains the {@link JDKModuleDescriptor} for the {@link Project}, as defined by the {@code module-info.java} file.
     * <p>
     * Should the {@link Project} not define a {@code module-info.java} file, say because the source is for a
     * non-modular version of Java (pre Java 9), an attempt will be made to create a synthetic one based on the
     * available information.
     * <p>
     * For example, one may be produce using information contained in a Apache Maven POM, Apache Ivy configuration or
     * Gradle Build File.
     *
     * @return the {@link JDKModuleDescriptor}
     */
    @Provides
    JDKModuleDescriptor getModuleDescriptor();
}
