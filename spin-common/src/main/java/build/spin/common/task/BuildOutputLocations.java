package build.spin.common.task;

/*-
 * #%L
 * Spin Common Library
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Optional;

/**
 * Resolves conventional build-tool output locations for a project directory.
 *
 * <p>Knows about spin ({@code .build/main/}), Maven ({@code target/}), and
 * Gradle ({@code build/}) layouts so callers don't have to hardcode paths.
 */
public final class BuildOutputLocations {

    private BuildOutputLocations() {
    }

    /**
     * Returns the spin output path for {@code relPath} under {@code buildDir/main/},
     * if that path exists on disk.
     */
    public static Optional<Path> spin(final Path project, final String buildDir, final String relPath) {
        return existing(project.resolve(buildDir + "/main/" + relPath));
    }

    /**
     * Returns the Maven output path for {@code relPath} under {@code target/},
     * if that path exists on disk.
     */
    public static Optional<Path> maven(final Path project, final String relPath) {
        return existing(project.resolve("target/" + relPath));
    }

    /**
     * Returns the Gradle output path for {@code relPath} under {@code build/},
     * if that path exists on disk.
     */
    public static Optional<Path> gradle(final Path project, final String relPath) {
        return existing(project.resolve("build/" + relPath));
    }

    private static Optional<Path> existing(final Path path) {
        return Files.exists(path) ? Optional.of(path) : Optional.empty();
    }

    /**
     * Returns the most recent last-modified time of any file anywhere under {@code path}, falling
     * back to {@code path} itself if it's empty. Used to pick the freshest of several candidate
     * build-output directories that all happen to exist on disk (e.g. a stale spin {@code .build/}
     * left over from before a project switched to Maven, sitting next to a current {@code target/}).
     */
    public static FileTime latestModifiedTime(final Path path) {
        try (var walk = Files.walk(path)) {
            return walk
                .map(p -> {
                    try {
                        return Files.getLastModifiedTime(p);
                    } catch (final IOException e) {
                        return FileTime.fromMillis(0);
                    }
                })
                .max(Comparator.naturalOrder())
                .orElse(FileTime.fromMillis(0));
        } catch (final IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
