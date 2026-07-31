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

import build.base.io.PathSet;
import build.base.io.PathSetBuilder;
import build.base.option.JDKVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The kind of a source root directory returned by {@link DetectSourcePaths}.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public enum SourcePathKind {

    /**
     * Declared, hand-written main source.
     */
    MAIN("src/main/java"),

    /**
     * Declared, hand-written test source.
     */
    TEST("src/test/java"),

    /**
     * Annotation-processor generated source from a prior build. Only ever surfaced for analysis —
     * never fed into an active compilation, since doing so would cause the annotation processor to
     * attempt to recreate files it already owns.
     */
    GENERATED,

    /**
     * Generated source produced by a tool other than an annotation processor (e.g. protobuf, ANTLR,
     * JAXB) from a prior build. Unlike {@link #GENERATED}, this is always fed into compilation as an
     * ordinary source root, since nothing else recompiles this content.
     */
    EXTERNAL_GENERATED;

    /**
     * The location of declared source for this kind, relative to the project path — e.g.
     * {@code "src/main/java"} for {@link #MAIN} or {@code "src/test/java"} for {@link #TEST}.
     * {@code null} for kinds that aren't declared, hand-written source.
     */
    private final String relativeSourcePath;

    SourcePathKind() {
        this(null);
    }

    SourcePathKind(final String relativeSourcePath) {
        this.relativeSourcePath = relativeSourcePath;
    }

    /**
     * Detects the root directories for this {@link SourcePathKind} in a project.
     *
     * @param projectPath        the project path
     * @param defaultJavaVersion the system default {@link JDKVersion}
     * @param javaVersion        the {@link JDKVersion} of the plugin doing the detecting
     * @param buildDirectoryName the name of spin's own build output directory
     * @return the {@link PathSet}
     */
    public PathSet detect(final Path projectPath, final JDKVersion defaultJavaVersion,
                          final JDKVersion javaVersion, final String buildDirectoryName) {
        return switch (this) {
            case MAIN, TEST ->
                detectDeclared(projectPath, defaultJavaVersion, javaVersion, this.relativeSourcePath);
            case GENERATED -> detectGenerated(projectPath, buildDirectoryName);
            case EXTERNAL_GENERATED -> detectExternalGenerated(projectPath);
        };
    }

    // Visible for testing.
    static PathSet detectDeclared(final Path projectPath, final JDKVersion defaultJavaVersion,
                                  final JDKVersion javaVersion, final String relativeSourcePath) {

        final PathSetBuilder builder = PathSetBuilder.create();

        // include the default source path iff this the plugin is for the default version
        final Path defaultPath = projectPath.resolve(relativeSourcePath);

        if (Files.exists(defaultPath) && defaultJavaVersion.major() == javaVersion.major()) {
            builder.add(defaultPath);
        }

        // include the specific source path iff it exists
        final Path specificPath = projectPath.resolve(relativeSourcePath + javaVersion.major());
        if (Files.exists(specificPath)) {
            builder.add(specificPath);
        }

        return builder.build();
    }

    // Visible for testing.
    static PathSet detectGenerated(final Path projectPath, final String buildDirectoryName) {
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
            addMavenGeneratedSourcePaths(projectPath, builder);
        }

        return builder.build();
    }

    // Visible for testing.
    static PathSet detectExternalGenerated(final Path projectPath) {
        final PathSetBuilder builder = PathSetBuilder.create();
        addMavenGeneratedSourcePaths(projectPath, builder);
        return builder.build();
    }

    private static void addMavenGeneratedSourcePaths(final Path projectPath, final PathSetBuilder builder) {
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
}
