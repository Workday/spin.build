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

import build.codemodel.foundation.CodeModel;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Shared XML utilities for parsing Maven POM files, used by the {@code PomBased*} resource
 * implementations.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
class PomXmlUtils {

    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private static final Pattern MODULE_NAME_PATTERN =
        Pattern.compile("(?:open\\s+)?module\\s+(\\S+)\\s*\\{");

    private PomXmlUtils() {
    }

    /**
     * Returns {@code true} when the directory at the given path looks like the root of a Maven
     * workspace rather than a sub-module of a larger reactor.
     * <p>
     * A path is a workspace root when it contains a {@code pom.xml} and is <strong>not</strong> a
     * sub-module. Sub-module detection requires two signals together: the pom declares a
     * {@code <parent>} element, <em>and</em> the parent directory itself contains a {@code pom.xml}.
     * This combination avoids false negatives for projects that inherit from a corporate parent
     * resolved from the Maven repository (e.g. {@code spring-boot-starter-parent}) — those poms
     * declare a {@code <parent>} but their filesystem parent directory has no pom.
     */
    static boolean isMavenWorkspaceRoot(final Path path) {
        final Path pom = path.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return false;
        }
        if (!hasDirectParentElement(pom)) {
            return true;
        }
        final Path parentDir = path.getParent();
        return parentDir == null || !Files.exists(parentDir.resolve("pom.xml"));
    }

    private static boolean hasDirectParentElement(final Path pomPath) {
        try {
            final Document doc = newDocumentBuilderFactory().newDocumentBuilder().parse(pomPath.toFile());
            final NodeList children = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element e && e.getTagName().equals("parent")) {
                    return true;
                }
            }
            return false;
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * The canonical {@code Resource.MetaClass.isDetectedIn} predicate for pom-based resources that
     * should also apply in spin-native workspaces (i.e. resources for which spin has no native
     * equivalent): the project is a Workspace and has a {@code pom.xml}, regardless of whether a
     * {@code .spinignore} marker is present.
     */
    static boolean isMavenWorkspaceProject(final build.spin.Project project) {
        return project instanceof build.spin.Workspace
            && Files.exists(project.path().resolve("pom.xml"));
    }

    /**
     * Creates a new {@link DocumentBuilderFactory} hardened against XML External Entity (XXE) attacks.
     * Disables DOCTYPE declarations and all external entity/parameter resolution.
     *
     * @return a hardened {@link DocumentBuilderFactory}
     * @throws ParserConfigurationException if the factory cannot be configured
     */
    static DocumentBuilderFactory newDocumentBuilderFactory() throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    /**
     * Returns the text content of the first element with the given tag name anywhere within the
     * parent element's subtree, or {@code null} if not found or empty.
     */
    static String textContent(final Element parent, final String tagName) {
        final NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        final String text = nodes.item(0).getTextContent().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Returns the text content of the first <em>direct</em> child element with the given tag name,
     * or {@code null} if not found or empty.
     * <p>
     * Unlike {@link #textContent}, this method does not search nested descendants, making it safe
     * for cases where the same tag appears in both the element and a nested {@code <parent>} block.
     */
    static String directChildText(final Element element, final String tagName) {
        final NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && child.getTagName().equals(tagName)) {
                final String text = child.getTextContent().trim();
                return text.isEmpty() ? null : text;
            }
        }
        return null;
    }

    /**
     * Returns the last hyphen-delimited segment of a Maven artifactId.
     * <p>
     * Examples: {@code assertj-core} → {@code core}, {@code junit-jupiter-api} → {@code api}.
     * Returns an empty string if the artifactId contains no hyphen.
     */
    static String lastHyphenSegment(final String artifactId) {
        final int idx = artifactId.lastIndexOf('-');
        return idx >= 0 ? artifactId.substring(idx + 1) : "";
    }

    /**
     * Derives a JPMS module name from a Maven artifactId, following the Java Platform specification.
     * Non-alphanumeric characters are replaced with {@code .}, and leading/trailing/duplicate dots
     * are stripped.
     */
    static String derivedModuleName(final String artifactId) {
        return artifactId
            .replaceAll("[^A-Za-z0-9]", ".")
            .replaceAll("^\\.*|\\.*$", "")
            .replaceAll("\\.{2,}", ".");
    }

    /**
     * Resolves a Maven property reference (e.g. {@code ${junit.version}}) against the given
     * properties map. Returns the value unchanged if it is not a {@code ${...}} reference.
     * Returns {@code null} if the referenced property is not present in the map.
     */
    static String resolveProperty(final String value, final Map<String, String> properties) {
        if (value == null) {
            return null;
        }
        final Matcher matcher = PROPERTY_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        return properties.get(matcher.group(1));
    }

    /**
     * Derives a JPMS module name using the "group-prefix" convention, where the first
     * hyphen-segment of the artifactId mirrors the last dot-segment of the groupId and is stripped.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code build.base:base-telemetry-foundation} → {@code build.base.telemetry.foundation}</li>
     *   <li>{@code build.codemodel:codemodel-framework} → {@code build.codemodel.framework}</li>
     * </ul>
     * Returns empty if the prefix does not match or the artifactId has no hyphen.
     */
    static Optional<String> groupPrefixedModuleName(final String groupId, final String artifactId) {
        final String groupLastSegment = groupId.contains(".")
            ? groupId.substring(groupId.lastIndexOf('.') + 1)
            : groupId;
        final int firstHyphen = artifactId.indexOf('-');
        if (firstHyphen < 0) {
            return Optional.empty();
        }
        final String firstArtifactSegment = artifactId.substring(0, firstHyphen);
        if (!groupLastSegment.equals(firstArtifactSegment)) {
            return Optional.empty();
        }
        final String remainder = derivedModuleName(artifactId.substring(firstHyphen + 1));
        return remainder.isEmpty() ? Optional.empty() : Optional.of(groupId + "." + remainder);
    }

    /**
     * Derives a JPMS module name using the "group-suffix" convention, where the last
     * hyphen-segment of the artifactId mirrors the last dot-segment of the groupId and is stripped,
     * leaving the first part as a suffix on the groupId.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code build.codemodel:jdk-codemodel} → {@code build.codemodel.jdk}</li>
     * </ul>
     * Returns empty if the suffix does not match or the artifactId has no hyphen.
     */
    static Optional<String> groupSuffixedModuleName(final String groupId, final String artifactId) {
        final String groupLastSegment = groupId.contains(".")
            ? groupId.substring(groupId.lastIndexOf('.') + 1)
            : groupId;
        final int lastHyphen = artifactId.lastIndexOf('-');
        if (lastHyphen < 0) {
            return Optional.empty();
        }
        final String lastArtifactSegment = artifactId.substring(lastHyphen + 1);
        if (!groupLastSegment.equals(lastArtifactSegment)) {
            return Optional.empty();
        }
        final String remainder = derivedModuleName(artifactId.substring(0, lastHyphen));
        return remainder.isEmpty() ? Optional.empty() : Optional.of(groupId + "." + remainder);
    }

    /**
     * Derives a JPMS module name using the "parent group + last artifact segment" convention,
     * where a sub-group suffix in the groupId (e.g. {@code core}) is replaced by the last
     * hyphen-segment of the artifactId.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code com.fasterxml.jackson.core:jackson-databind} → {@code com.fasterxml.jackson.databind}</li>
     * </ul>
     * Returns empty if the groupId has no parent (no dot) or the artifactId has no hyphen.
     */
    static Optional<String> groupParentWithLastArtifactSegment(final String groupId, final String artifactId) {
        final int lastDot = groupId.lastIndexOf('.');
        if (lastDot < 0) {
            return Optional.empty();
        }
        final String lastArtifactSegment = lastHyphenSegment(artifactId);
        if (lastArtifactSegment.isEmpty()) {
            return Optional.empty();
        }
        final String parentGroupId = groupId.substring(0, lastDot);
        return Optional.of(parentGroupId + "." + lastArtifactSegment);
    }

    /**
     * Extracts the JPMS module name from the {@code module-info.java} file located at
     * {@code src/main/java/module-info.java} relative to the directory containing the given pom.xml.
     *
     * @param pomPath path to the {@code pom.xml}
     * @return the module name, or empty if no {@code module-info.java} is present or parseable
     */
    static Optional<String> readModuleName(final Path pomPath) {
        final Path moduleInfo = pomPath.getParent().resolve("src/main/java/module-info.java");
        if (!Files.exists(moduleInfo)) {
            return Optional.empty();
        }
        try {
            for (final String line : Files.readAllLines(moduleInfo)) {
                final Matcher matcher = MODULE_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
            }
        } catch (final Exception ignored) {
        }
        return Optional.empty();
    }

    /**
     * Returns the path to the {@code .pom} file for the given artifact coordinates in the specified
     * Maven local repository, or empty if the file does not exist.
     * <p>
     * The standard Maven local repo layout is used:
     * {@code <localRepo>/<groupId>/<artifactId>/<version>/<artifactId>-<version>.pom}
     * where {@code groupId} dots are replaced by path separators.
     */
    static Optional<Path> localRepoPomPath(final String groupId,
                                           final String artifactId,
                                           final String version,
                                           final Path localRepo) {
        final Path pomPath = localRepo
            .resolve(groupId.replace('.', '/'))
            .resolve(artifactId)
            .resolve(version)
            .resolve(artifactId + "-" + version + ".pom");
        return Files.exists(pomPath) ? Optional.of(pomPath) : Optional.empty();
    }

    /**
     * Reads the {@code <dependencies>} section of the given pom.xml and returns a list of
     * {@code [groupId, artifactId, resolvedVersion, scope]} arrays. Entries whose version cannot be
     * resolved against the supplied properties are omitted. Scope defaults to {@code "compile"} when
     * absent from the pom.
     */
    static List<String[]> readRawDependencies(final DocumentBuilder builder,
                                              final Path pomPath,
                                              final Map<String, String> properties,
                                              final Map<String, String> rootDm) {
        final List<String[]> result = new ArrayList<>();
        try {
            final Document doc = builder.parse(pomPath.toFile());

            // Determine the pom's own groupId and version for same-group sibling fallback.
            final Element root = doc.getDocumentElement();
            String pomGroupId = directChildText(root, "groupId");
            String pomVersion = directChildText(root, "version");
            final NodeList parentNodes = doc.getElementsByTagName("parent");
            if (parentNodes.getLength() > 0 && parentNodes.item(0) instanceof Element parent) {
                if (pomGroupId == null) {
                    pomGroupId = directChildText(parent, "groupId");
                }
                if (pomVersion == null) {
                    pomVersion = directChildText(parent, "version");
                }
            }
            final String resolvedPomVersion = resolveProperty(pomVersion, properties);

            final NodeList deps = doc.getElementsByTagName("dependency");
            for (int i = 0; i < deps.getLength(); i++) {
                if (!(deps.item(i) instanceof Element dep)) {
                    continue;
                }
                final String groupId = textContent(dep, "groupId");
                final String artifactId = textContent(dep, "artifactId");
                if (groupId == null || artifactId == null) {
                    continue;
                }
                final String rawVersion = textContent(dep, "version");
                String resolvedVersion = resolveProperty(rawVersion, properties);
                if (resolvedVersion == null || resolvedVersion.contains("${")) {
                    resolvedVersion = rootDm.get(groupId + ":" + artifactId);
                }
                // Maven convention: same-groupId sibling deps with no version use ${project.version}.
                if (resolvedVersion == null && groupId.equals(pomGroupId) && resolvedPomVersion != null) {
                    resolvedVersion = resolvedPomVersion;
                }
                if (resolvedVersion == null) {
                    continue;
                }
                final String scope = textContent(dep, "scope");
                result.add(new String[]{groupId, artifactId, resolvedVersion, scope != null ? scope : "compile"});
            }
        } catch (final Exception ignored) {
        }
        return result;
    }

    /**
     * Reads the {@code Automatic-Module-Name} attribute from the {@code META-INF/MANIFEST.MF} of
     * the jar for the given Maven coordinates in the local repository.
     *
     * @return the automatic module name, or empty if the jar is absent, has no manifest, or
     * carries no {@code Automatic-Module-Name} attribute
     */
    static Optional<String> readAutomaticModuleName(final String groupId,
                                                    final String artifactId,
                                                    final String version,
                                                    final Path localRepo) {
        final Path jarPath = localRepo
            .resolve(groupId.replace('.', '/'))
            .resolve(artifactId)
            .resolve(version)
            .resolve(artifactId + "-" + version + ".jar");
        if (!Files.exists(jarPath)) {
            return Optional.empty();
        }
        try (var jar = new JarFile(jarPath.toFile())) {
            final var manifest = jar.getManifest();
            if (manifest == null) {
                return Optional.empty();
            }
            final String name = manifest.getMainAttributes().getValue("Automatic-Module-Name");
            return Optional.ofNullable(name == null || name.isBlank() ? null : name.trim());
        } catch (final Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * Reads the JPMS module name from the {@code module-info.class} entry inside the jar at the
     * computed local-repository path. Returns empty if the jar doesn't exist, has no
     * {@code module-info.class}, or cannot be read.
     */
    static Optional<String> readNamedModuleName(final String groupId,
                                                final String artifactId,
                                                final String version,
                                                final Path localRepo,
                                                final CodeModel codeModel) {
        final Path jarPath = localRepo
            .resolve(groupId.replace('.', '/'))
            .resolve(artifactId)
            .resolve(version)
            .resolve(artifactId + "-" + version + ".jar");
        if (!Files.exists(jarPath)) {
            return Optional.empty();
        }
        return JDKModuleDescriptor.extractFresh(codeModel, jarPath)
            .map(d -> d.moduleName().toString());
    }

    /**
     * Parses the {@code <properties>} section of the given pom.xml and returns a map of property
     * name to value.
     *
     * @param builder the {@link DocumentBuilder} to use for parsing
     * @param pomPath path to the {@code pom.xml}
     * @return a mutable map of property name → value
     * @throws Exception if the file cannot be read or parsed
     */
    static Map<String, String> readProperties(final DocumentBuilder builder, final Path pomPath) throws Exception {
        final Map<String, String> properties = new HashMap<>();
        final Document doc = builder.parse(pomPath.toFile());
        final NodeList propNodes = doc.getElementsByTagName("properties");
        if (propNodes.getLength() > 0) {
            final NodeList children = propNodes.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element element) {
                    properties.put(element.getNodeName(), element.getTextContent().trim());
                }
            }
        }
        return properties;
    }

    /**
     * Attempts to find a jar in the local repository for the given module name and version using
     * the naming-convention heuristic: given a module name like {@code build.spin.module.clean},
     * the groupId is the prefix up to the last dot ({@code build.spin.module}), and the artifactId
     * is constructed as {@code {parentLastSegment}-{extra}-{groupLastSegment}}
     * (e.g. {@code spin-clean-module}). Falls back to the plain {@code {extra}} artifactId form.
     * Returns {@code [groupId, artifactId, version]} or empty.
     */
    static Optional<String[]> findJarByModuleName(final String moduleName,
                                                  final String version,
                                                  final Path localRepo) {
        final int lastDot = moduleName.lastIndexOf('.');
        if (lastDot < 0) {
            return Optional.empty();
        }
        final String groupId = moduleName.substring(0, lastDot);
        final String extra = moduleName.substring(lastDot + 1);
        final String groupLastSegment = groupId.contains(".")
            ? groupId.substring(groupId.lastIndexOf('.') + 1)
            : groupId;
        final String parentGroupId = groupId.contains(".")
            ? groupId.substring(0, groupId.lastIndexOf('.'))
            : null;
        final String parentLastSegment = parentGroupId != null && parentGroupId.contains(".")
            ? parentGroupId.substring(parentGroupId.lastIndexOf('.') + 1)
            : parentGroupId;

        final List<String> candidates = new ArrayList<>();
        if (parentLastSegment != null) {
            candidates.add(parentLastSegment + "-" + extra + "-" + groupLastSegment);
        }
        candidates.add(groupLastSegment + "-" + extra);
        candidates.add(extra + "-" + groupLastSegment);
        candidates.add(extra);

        for (final String artifactId : candidates) {
            final Path jarPath = localRepo
                .resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar");
            if (Files.exists(jarPath)) {
                return Optional.of(new String[]{groupId, artifactId, version});
            }
        }
        return Optional.empty();
    }

    /**
     * Reads the {@code <dependencyManagement>} section of the given pom and returns a map of
     * {@code groupId:artifactId} to resolved version string. Property references are resolved
     * against the supplied {@code properties} map. Entries whose version cannot be resolved are
     * omitted.
     */
    static Map<String, String> readDependencyManagement(final DocumentBuilder builder,
                                                        final Path pomPath,
                                                        final Map<String, String> properties) {
        return readDependencyManagement(builder, pomPath, properties, Optional.empty(), 0);
    }

    static Map<String, String> readDependencyManagement(final DocumentBuilder builder,
                                                        final Path pomPath,
                                                        final Map<String, String> properties,
                                                        final Path localRepo) {
        return readDependencyManagement(builder, pomPath, properties, Optional.of(localRepo), 0);
    }

    private static Map<String, String> readDependencyManagement(final DocumentBuilder builder,
                                                                final Path pomPath,
                                                                final Map<String, String> properties,
                                                                final Optional<Path> localRepo,
                                                                final int depth) {
        final Map<String, String> dm = new HashMap<>();
        if (depth > 8) {
            return dm;
        }
        try {
            final Document doc = builder.parse(pomPath.toFile());

            // Merge parent DM first (lower priority than own DM)
            localRepo.ifPresent(repo -> {
                final NodeList parentNodes = doc.getElementsByTagName("parent");
                if (parentNodes.getLength() > 0 && parentNodes.item(0) instanceof Element parent) {
                    final String pg = directChildText(parent, "groupId");
                    final String pa = directChildText(parent, "artifactId");
                    final String pv = resolveProperty(directChildText(parent, "version"), properties);
                    if (pg != null && pa != null && pv != null && !pv.contains("${")) {
                        localRepoPomPath(pg, pa, pv, repo).ifPresent(parentPom -> {
                            try {
                                final Map<String, String> parentProps = readProperties(builder, parentPom);
                                dm.putAll(readDependencyManagement(builder, parentPom, parentProps, localRepo, depth + 1));
                            } catch (final Exception ignored) {
                            }
                        });
                    }
                }
            });

            final NodeList dmNodes = doc.getElementsByTagName("dependencyManagement");
            if (dmNodes.getLength() == 0) {
                return dm;
            }
            final NodeList deps = ((Element) dmNodes.item(0)).getElementsByTagName("dependency");
            for (int i = 0; i < deps.getLength(); i++) {
                if (!(deps.item(i) instanceof Element dep)) {
                    continue;
                }
                final String groupId = textContent(dep, "groupId");
                final String artifactId = textContent(dep, "artifactId");
                final String rawVersion = textContent(dep, "version");
                if (groupId == null || artifactId == null || rawVersion == null) {
                    continue;
                }
                final String resolved = resolveProperty(rawVersion, properties);
                if (resolved != null && !resolved.contains("${")) {
                    dm.put(groupId + ":" + artifactId, resolved);
                }
            }
        } catch (final Exception ignored) {
        }
        return dm;
    }
}
