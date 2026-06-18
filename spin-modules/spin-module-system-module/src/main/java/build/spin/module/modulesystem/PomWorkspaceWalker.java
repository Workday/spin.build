package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
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

import build.base.telemetry.TelemetryRecorder;
import build.codemodel.foundation.CodeModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;

/**
 * Walks a Maven workspace's {@code pom.xml} files and their transitive dependencies from the
 * local repository, invoking a {@link CoordinateVisitor} for every (module-name list, coordinate)
 * pair that can be derived from the poms.
 * <p>
 * Shared by {@link PomBasedModuleCatalog} and {@link PomBasedModuleVersioning} — the only thing
 * that differs between those two is what they store per visit, which lives in the visitor.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
final class PomWorkspaceWalker {

    private static final String POM_FILENAME = "pom.xml";

    private PomWorkspaceWalker() {
    }

    /**
     * Called once for each (module-name list, coordinate) pair discovered during the walk.
     * {@code moduleNames} contains every JPMS name under which the coordinate should be
     * registered: the groupId, derived names from the artifactId, and optionally the
     * {@code Automatic-Module-Name} from the jar manifest.
     */
    @FunctionalInterface
    interface CoordinateVisitor {
        void accept(List<String> moduleNames, String groupId, String artifactId, String resolvedVersion);
    }

    /**
     * Walks the workspace pom tree, then follows module-info.class requires transitively
     * through the local repository, invoking {@code visitor} for every coordinate found.
     */
    static void walk(final Path workspacePath,
                     final Path localRepo,
                     final TelemetryRecorder recorder,
                     final CodeModel codeModel,
                     final CoordinateVisitor visitor) {
        final Path rootPom = workspacePath.resolve(POM_FILENAME);
        if (!Files.exists(rootPom)) {
            return;
        }

        try {
            final DocumentBuilder builder = PomXmlUtils.newDocumentBuilderFactory().newDocumentBuilder();
            final Map<String, String> rootProperties = PomXmlUtils.readProperties(builder, rootPom);
            final Map<String, String> rootDm = PomXmlUtils.readDependencyManagement(builder, rootPom, rootProperties);

            // visited keyed by "groupId:artifactId" to avoid re-processing
            final Set<String> visited = new HashSet<>();

            // Phase 1: walk the workspace poms, visit each module's own artifact, and seed the
            // dependency BFS queue with their directly declared dependencies.
            final Deque<String[]> moduleQueue = new ArrayDeque<>();

            final List<Path> pomPaths = new ArrayList<>();
            Files.walkFileTree(workspacePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                    final String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (name.equals("target") || name.equals(".build")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(POM_FILENAME)) {
                        pomPaths.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            for (final Path pomPath : pomPaths) {
                walkPom(builder, pomPath, rootProperties, rootPom, recorder, visitor);
                PomXmlUtils.readRawDependencies(builder, pomPath, rootProperties, rootDm)
                    .forEach(d -> {
                        if (visited.add(d[0] + ":" + d[1])) {
                            moduleQueue.add(d);
                        }
                    });
            }

            // Phase 2: BFS transitive poms from the local repository. Each coordinate is visited
            // exactly once — the visited gate fires before enqueue, so visitDependency is called
            // here at dequeue time rather than at the enqueue site.
            while (!moduleQueue.isEmpty()) {
                final String[] coord = moduleQueue.poll();
                visitDependency(coord[0], coord[1], coord[2], localRepo, recorder, codeModel, visitor);
                PomXmlUtils.localRepoPomPath(coord[0], coord[1], coord[2], localRepo)
                    .ifPresent(pomPath -> {
                        try {
                            final Map<String, String> pomProperties =
                                PomXmlUtils.readProperties(builder, pomPath);
                            final Map<String, String> pomDm =
                                PomXmlUtils.readDependencyManagement(builder, pomPath, pomProperties, localRepo);
                            PomXmlUtils.readRawDependencies(builder, pomPath, pomProperties, pomDm)
                                .stream()
                                .filter(d -> !"test".equals(d[3]) && !"provided".equals(d[3]))
                                .forEach(d -> {
                                    if (visited.add(d[0] + ":" + d[1])) {
                                        moduleQueue.add(d);
                                    }
                                });
                        } catch (final Exception e) {
                            recorder.warn(e, "PomWorkspaceWalker failed to read transitive pom [%s]", pomPath);
                        }
                    });
            }

        } catch (final Exception e) {
            recorder.warn(e, "PomWorkspaceWalker failed to walk workspace [%s]", workspacePath);
        }
    }

    /**
     * Visits the self-artifact of a single workspace pom (unless it is the root aggregator pom).
     * Direct dependencies are seeded into the BFS queue by the caller; they are visited in Phase 2.
     */
    private static void walkPom(final DocumentBuilder builder,
                                final Path pomPath,
                                final Map<String, String> properties,
                                final Path rootPomPath,
                                final TelemetryRecorder recorder,
                                final CoordinateVisitor visitor) {
        try {
            final Document doc = builder.parse(pomPath.toFile());
            // Skip self-registration only for root aggregator poms (packaging=pom).
            // Single-module root poms (packaging=jar or absent) must be registered so
            // their own version is in the map.
            final boolean isRootAggregator = pomPath.equals(rootPomPath)
                && "pom".equals(PomXmlUtils.directChildText(doc.getDocumentElement(), "packaging"));
            if (!isRootAggregator) {
                visitSelf(doc, pomPath, properties, recorder, visitor);
            }
        } catch (final Exception e) {
            recorder.warn(e, "PomWorkspaceWalker failed to parse [%s]", pomPath);
        }
    }

    /**
     * Visits the {@code <artifactId>} of a workspace pom itself, inheriting groupId/version from
     * {@code <parent>} when not declared directly. Prefers the JPMS module name from
     * {@code module-info.java} when present; otherwise falls back to the derived-name heuristics.
     */
    private static void visitSelf(final Document doc,
                                  final Path pomPath,
                                  final Map<String, String> properties,
                                  final TelemetryRecorder recorder,
                                  final CoordinateVisitor visitor) {
        try {
            final Element root = doc.getDocumentElement();
            final String artifactId = PomXmlUtils.directChildText(root, "artifactId");
            String groupId = PomXmlUtils.directChildText(root, "groupId");
            String rawVersion = PomXmlUtils.directChildText(root, "version");

            final NodeList parentNodes = doc.getElementsByTagName("parent");
            if (parentNodes.getLength() > 0 && parentNodes.item(0) instanceof Element parent) {
                if (groupId == null) {
                    groupId = PomXmlUtils.directChildText(parent, "groupId");
                }
                if (rawVersion == null) {
                    rawVersion = PomXmlUtils.directChildText(parent, "version");
                }
            }

            if (groupId == null || artifactId == null || rawVersion == null) {
                return;
            }

            final String resolvedVersion = PomXmlUtils.resolveProperty(rawVersion, properties);
            if (resolvedVersion == null || resolvedVersion.contains("${")) {
                return;
            }

            final Optional<String> preferred = PomXmlUtils.readModuleName(pomPath);
            final List<String> names = preferred.isPresent()
                ? List.of(preferred.get())
                : deriveNames(groupId, artifactId);

            visitor.accept(names, groupId, artifactId, resolvedVersion);
        } catch (final Exception e) {
            recorder.warn(e, "PomWorkspaceWalker failed to visit self-artifact for [%s]", pomPath);
        }
    }

    /**
     * Visits a dependency coordinate, registering it under all derived JPMS name conventions
     * plus the {@code Automatic-Module-Name} from the jar manifest when present.
     */
    private static void visitDependency(final String groupId,
                                        final String artifactId,
                                        final String resolvedVersion,
                                        final Path localRepo,
                                        final TelemetryRecorder recorder,
                                        final CodeModel codeModel,
                                        final CoordinateVisitor visitor) {
        try {
            final List<String> names = new ArrayList<>(deriveNames(groupId, artifactId));
            PomXmlUtils.readAutomaticModuleName(groupId, artifactId, resolvedVersion, localRepo)
                .ifPresent(names::add);
            PomXmlUtils.readNamedModuleName(groupId, artifactId, resolvedVersion, localRepo, codeModel)
                .ifPresent(names::add);
            visitor.accept(names, groupId, artifactId, resolvedVersion);
        } catch (final Exception e) {
            recorder.warn(e, "PomWorkspaceWalker failed to visit dependency [%s:%s:%s]",
                groupId, artifactId, resolvedVersion);
        }
    }

    /**
     * Returns the derived JPMS module name candidates for a (groupId, artifactId) pair.
     * The list always includes the groupId and the derived artifactId name; additional candidates
     * are included when the heuristics in {@link PomXmlUtils} produce them.
     */
    private static List<String> deriveNames(final String groupId, final String artifactId) {
        final List<String> names = new ArrayList<>(6);
        names.add(groupId);
        names.add(PomXmlUtils.derivedModuleName(artifactId));
        final String lastSegment = PomXmlUtils.lastHyphenSegment(artifactId);
        if (!lastSegment.isEmpty()) {
            // bare segment matches projects whose directory name is just the last artifact segment
            // (e.g. "processor" directory for artifact "ap-simple-processor")
            names.add(lastSegment);
            names.add(groupId + "." + lastSegment);
        }
        PomXmlUtils.groupPrefixedModuleName(groupId, artifactId).ifPresent(names::add);
        PomXmlUtils.groupSuffixedModuleName(groupId, artifactId).ifPresent(names::add);
        PomXmlUtils.groupParentWithLastArtifactSegment(groupId, artifactId).ifPresent(names::add);
        return names;
    }
}
