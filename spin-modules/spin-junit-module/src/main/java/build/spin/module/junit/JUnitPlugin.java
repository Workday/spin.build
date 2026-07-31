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

import build.base.io.PathSet;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.Category;
import build.spin.common.annotation.Ordered;
import build.spin.module.java.JavaPlugin;

/**
 * A {@link JavaPlugin} for compiling and executing JUnit-based tests using the JUnit Platform for
 *  * Java-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public interface JUnitPlugin
    extends JavaPlugin, Plugin.Comparable<JUnitPlugin> {

    @Override
    default int compareTo(final JUnitPlugin other) {
        return getJavaVersion().compareTo(other.getJavaVersion());
    }

    /**
     * A {@link Ordered} {@link Task} that compiles Java source files for testing with JUnit.
     */
    @Ordered(plugin = JUnitPlugin.class, task = JUnitPlugin.Compile.class)
    @Category("test-compile")
    interface Compile
        extends Task<PathSet> {

    }
}
