package build.spin.common;

/*-
 * #%L
 * Spin Common
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

import build.spawn.application.option.Executable;

import java.nio.file.Path;

/**
 * Resolves the path to a named tool (e.g. {@code javac}, {@code javadoc}, {@code jlink},
 * {@code jdeps}, {@code java}) under a JDK or runtime image's {@code bin/} directory, as an
 * {@link Executable} ready to add to a launch {@link build.base.configuration.ConfigurationBuilder}.
 */
public final class JDKTools {

    private JDKTools() {
    }

    /**
     * @param home the JDK or runtime image home directory (e.g. {@code JDKHome#path()})
     * @param tool the tool name, e.g. {@code "javac"}
     * @return an {@link Executable} pointing at {@code home/bin/tool}
     */
    public static Executable executable(final Path home, final String tool) {
        return Executable.of(home.resolve("bin/" + tool).toString());
    }
}
