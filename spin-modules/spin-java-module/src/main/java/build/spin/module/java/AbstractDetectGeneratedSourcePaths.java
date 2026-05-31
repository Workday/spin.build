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

import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.spin.common.task.DetectGeneratedSourcePaths;
import build.spin.option.BuildDirectoryName;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Detects annotation-processor generated source {@link Path}s from any prior build.
 *
 * <p>Checks both spin's own output ({@code .build/main/generated-sources}) and Maven's
 * conventional location ({@code target/generated-sources/*}). Each subdirectory of
 * {@code target/generated-sources} is a separate source root (one per processor, e.g. {@code jt/},
 * {@code annotations/}).
 *
 * <p>Only surfaces directories that already exist on disk. Never used as input to compilation —
 * calling this during an active javac invocation would cause the annotation processor to attempt
 * to recreate files already in the source set.
 *
 * @author reed.vonredwitz
 * @since May-2026
 */
public abstract class AbstractDetectGeneratedSourcePaths
    implements DetectGeneratedSourcePaths {

    @Inject
    private Path projectPath;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    @Override
    public PathSet detect() {
        return detect(this.projectPath, this.buildDirectoryName.get());
    }

    // Visible for testing.
    static PathSet detect(final Path projectPath, final String buildDirectoryName) {
        final PathSetBuilder builder = PathSetBuilder.create();

        final boolean usedSpin = BuildOutputLocations.spin(projectPath, buildDirectoryName, "generated-sources")
            .map(p -> {
                builder.add(p);
                return true;
            })
            .orElse(false);

        // Maven splits generated sources by processor into subdirectories; each is its own source root.
        // Only fall back to Maven if spin hasn't already produced generated sources.
        if (!usedSpin) {
            BuildOutputLocations.maven(projectPath, "generated-sources")
                .filter(Files::isDirectory)
                .ifPresent(dir -> {
                    try (Stream<Path> subdirs = Files.list(dir)) {
                        subdirs.filter(Files::isDirectory).forEach(builder::add);
                    } catch (final IOException e) {
                        // best-effort: skip if unreadable
                    }
                });
        }

        return builder.build();
    }
}
