package build.spin.option;

/*-
 * #%L
 * Spin API
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

import build.base.configuration.CollectedOption;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * An {@link build.base.configuration.Option} specifying one additional physical root directory to include in a
 * federated {@link build.spin.Workspace}, allowing a single invocation to span multiple physical repositories.
 * <p>
 * May be specified more than once (each occurrence is retained; see {@link CollectedOption}) to federate more
 * than two roots.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public final class Root
    implements CollectedOption<LinkedHashSet> {

    /**
     * The root directory, as specified on the command-line (potentially relative).
     */
    private final String directory;

    /**
     * Constructs a {@link Root}.
     *
     * @param directory the root directory
     */
    private Root(final String directory) {
        Objects.requireNonNull(directory, "The directory must not be null");
        this.directory = directory;
    }

    /**
     * Obtains a {@link Path} representation of the {@link Root} using the specified {@link FileSystem}.
     *
     * @param fileSystem the {@link FileSystem}
     * @return a {@link Path}
     */
    public Path path(final FileSystem fileSystem) {
        return fileSystem.getPath(this.directory);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof final Root other)) {
            return false;
        }
        return this.directory.equals(other.directory);
    }

    @Override
    public int hashCode() {
        return this.directory.hashCode();
    }

    @Override
    public String toString() {
        return "Root{" + this.directory + '}';
    }

    /**
     * Creates a {@link Root} for the specified directory.
     *
     * @param directory the root directory
     * @return a new {@link Root}
     */
    public static Root of(final String directory) {
        return new Root(directory);
    }
}
