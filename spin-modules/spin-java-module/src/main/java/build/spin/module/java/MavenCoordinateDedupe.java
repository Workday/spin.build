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

import build.base.version.Version;
import build.base.version.VersionOrder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Shared Maven-coordinate dedupe (keep-highest-version) logic for {@link AbstractDetectResolution}
 * and {@link AbstractJavaDependencyAnalysis}. Both build a candidate set that can contain two
 * different versions of the same Maven artifact, pulled in independently via two different
 * {@code requires} paths — one from a direct {@code requires}, the other transitively through a
 * dependency's own pom — with no cross-call reconciliation. Left alone, both versions land on the
 * classpath/module-path together and the JDK non-deterministically picks the wrong one. The two
 * callers build different candidate types (resolved artifact {@link Path}s vs
 * {@link build.spin.module.modulesystem.ArtifactDescriptor}s), so this is generic over the item
 * type rather than a literal merge.
 */
final class MavenCoordinateDedupe {

    private MavenCoordinateDedupe() {
    }

    /**
     * Dedupes {@code items} by Maven coordinate, keyed by the resolved-artifact directory — the
     * {@link Path} parent above the version segment (i.e. everything except the version and
     * filename segments of the standard {@code <groupId-path>/<artifactId>/<version>/<artifactId>-
     * <version>[-classifier].<ext>} local-repository layout). Two items share a coordinate iff
     * {@code pathOf} resolves them to paths sharing that parent path, which is true for the same
     * (groupId, artifactId) and never true otherwise.
     *
     * <p>Versions are compared with {@link VersionOrder#MAVEN}, not {@link Version#compareTo}, so
     * that Maven qualifiers (e.g. {@code rc}, {@code beta}, {@code snapshot}) rank the way Maven
     * itself ranks them rather than lexicographically.
     *
     * <p>An item whose {@code pathOf} is empty, whose path has no version-segment parent, or whose
     * version-segment directory name doesn't parse as a {@link Version}, can't be assigned a
     * coordinate — it's kept as its own unique entry rather than being dropped or merged with an
     * unrelated item.
     *
     * @param items       the items to dedupe; iteration order is preserved for the kept items
     * @param pathOf      resolves an item to the resolved artifact {@link Path} used to derive its
     *                    Maven coordinate, if known
     * @param onDuplicate invoked as {@code (kept, dropped)} for each duplicate pair
     * @param <T>         the candidate item type
     * @return the deduped items, in first-seen order
     */
    static <T> List<T> dedupeByMavenCoordinate(final List<T> items,
                                               final Function<T, Optional<Path>> pathOf,
                                               final BiConsumer<T, T> onDuplicate) {

        final LinkedHashMap<Object, T> byCoordinate = new LinkedHashMap<>();
        final LinkedHashMap<Path, Version> versionByCoordinate = new LinkedHashMap<>();

        for (final T item : items) {
            final Optional<Path> path = pathOf.apply(item);

            if (path.isEmpty()) {
                // can't determine a coordinate — treat as its own unique entry
                byCoordinate.put(item, item);
                continue;
            }

            final Path versionDir = path.get().getParent();
            final Path artifactDir = versionDir == null ? null : versionDir.getParent();

            if (artifactDir == null) {
                byCoordinate.put(item, item);
                continue;
            }

            final Optional<Version> version = Version.tryParse(versionDir.getFileName().toString());

            if (version.isEmpty()) {
                byCoordinate.put(item, item);
                continue;
            }

            final Version existingVersion = versionByCoordinate.get(artifactDir);
            if (existingVersion == null) {
                byCoordinate.put(artifactDir, item);
                versionByCoordinate.put(artifactDir, version.get());
            } else {
                final T existingItem = byCoordinate.get(artifactDir);
                final int comparison = VersionOrder.MAVEN.compare(version.get(), existingVersion);
                if (comparison > 0) {
                    byCoordinate.put(artifactDir, item);
                    versionByCoordinate.put(artifactDir, version.get());
                    onDuplicate.accept(item, existingItem);
                } else if (comparison < 0) {
                    onDuplicate.accept(existingItem, item);
                }
                // else: same version resolved via a different transitive path - not a real ambiguity
            }
        }

        return new ArrayList<>(byCoordinate.values());
    }
}
