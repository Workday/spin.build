package build.spin.module.modulesystem.pom;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Reads a {@code pom.xml} into an effective {@link Pom} model: parent inheritance applied,
 * {@code <dependencyManagement>} and {@code <pluginManagement>} merged into dependencies and
 * plugins, and {@code ${...}} property references interpolated against the effective property
 * map plus a small set of Maven built-ins.
 * <p>
 * Parent resolution tries {@code <relativePath>} first (default {@code ../pom.xml}) and falls
 * back to the local repository at
 * {@code <localRepo>/<groupId-as-path>/<artifactId>/<version>/<artifactId>-<version>.pom}.
 * Cycles in the parent chain are guarded against.
 * <p>
 * Coords of the form {@code ${groupId:artifactId:type[:classifier]}} (output of the
 * {@code maven-dependency-plugin:properties} goal) are <strong>not</strong> interpolated here —
 * they require a resolved test classpath, which lives outside the pom.
 * <p>
 * Reads are cached by absolute path per {@code PomReader} instance so parent chains and sibling
 * sub-projects share parsed parents.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public final class PomReader {

    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Matches the dependency-plugin {@code properties}-goal coord syntax
     * {@code ${groupId:artifactId:type[:classifier]}}. Resolved by callers with classpath context.
     */
    private static final Pattern COORD_REF = Pattern.compile("\\$\\{[^:}]+:[^:}]+(?::[^:}]+){1,2}}");

    private final Path localRepository;
    private final TelemetryRecorder recorder;
    private final Map<Path, Pom> cache = new HashMap<>();

    public PomReader(final Path localRepository,
                     final TelemetryRecorder recorder) {
        this.localRepository = localRepository;
        this.recorder = recorder;
    }

    /**
     * Reads and returns the effective pom at the given path. Returns {@link Optional#empty()} if
     * the file does not exist or cannot be parsed.
     */
    public Optional<Pom> read(final Path pomXml) {
        if (!Files.exists(pomXml)) {
            return Optional.empty();
        }
        return Optional.ofNullable(readEffective(pomXml.toAbsolutePath().normalize(), new HashSet<>()));
    }

    private Pom readEffective(final Path pomPath,
                              final Set<Path> visited) {
        final Pom cached = this.cache.get(pomPath);
        if (cached != null) {
            return cached;
        }
        if (!visited.add(pomPath)) {
            // cycle in parent chain — log and treat as no-parent terminus
            this.recorder.warn("Cycle detected in pom parent chain at [%s]", pomPath);
            return null;
        }

        final Raw raw = parseRaw(pomPath);
        if (raw == null) {
            return null;
        }

        final Optional<Pom> parent = resolveParent(pomPath, raw, visited);

        final String groupId = raw.groupId != null ? raw.groupId
            : parent.map(Pom::groupId).orElse("");
        final String version = raw.version != null ? raw.version
            : parent.map(Pom::version).orElse("");

        // effective properties: parent ⊕ own (own wins), plus built-ins for self-referential interpolation
        final Map<String, String> effectiveProps = new LinkedHashMap<>();
        parent.ifPresent(p -> effectiveProps.putAll(p.properties()));
        effectiveProps.putAll(raw.properties);
        effectiveProps.put("project.groupId", groupId);
        effectiveProps.put("project.artifactId", raw.artifactId);
        effectiveProps.put("project.version", version);
        effectiveProps.put("project.basedir", pomPath.getParent().toString());
        effectiveProps.put("settings.localRepository", this.localRepository.toString());

        // effective depMgmt: parent ⊕ own (own wins per GA)
        final Map<GA, Dependency> effectiveDepMgmt = new LinkedHashMap<>();
        parent.ifPresent(p -> effectiveDepMgmt.putAll(p.dependencyManagement()));
        for (final RawDependency rd : raw.dependencyManagement) {
            final Dependency interpolated = toDependency(rd, effectiveProps);
            effectiveDepMgmt.put(interpolated.ga(), interpolated);
        }

        // effective dependencies: own with version/scope/type/classifier filled from effective depMgmt
        final List<Dependency> effectiveDependencies = new ArrayList<>(raw.dependencies.size());
        for (final RawDependency rd : raw.dependencies) {
            effectiveDependencies.add(fillFromMgmt(toDependency(rd, effectiveProps), effectiveDepMgmt));
        }

        // effective pluginMgmt: parent ⊕ own (own wins per GA, configurations deep-merged)
        final Map<GA, Plugin> effectivePluginMgmt = new LinkedHashMap<>();
        parent.ifPresent(p -> effectivePluginMgmt.putAll(p.pluginManagement()));
        for (final RawPlugin rp : raw.pluginManagement) {
            final Plugin own = toPlugin(rp, effectiveProps);
            final Plugin merged = mergePluginInto(effectivePluginMgmt.get(own.ga()), own);
            effectivePluginMgmt.put(merged.ga(), merged);
        }

        // effective plugins: own with config deep-merged from matching effective pluginMgmt
        final List<Plugin> effectivePlugins = new ArrayList<>(raw.plugins.size());
        for (final RawPlugin rp : raw.plugins) {
            final Plugin own = toPlugin(rp, effectiveProps);
            effectivePlugins.add(mergePluginInto(effectivePluginMgmt.get(own.ga()), own));
        }

        final Pom pom = new DefaultPom(
            groupId,
            raw.artifactId,
            version,
            parent,
            Collections.unmodifiableMap(effectiveProps),
            Collections.unmodifiableMap(effectiveDepMgmt),
            Collections.unmodifiableList(effectiveDependencies),
            Collections.unmodifiableMap(effectivePluginMgmt),
            Collections.unmodifiableList(effectivePlugins));

        this.cache.put(pomPath, pom);
        visited.remove(pomPath);
        return pom;
    }

    private Optional<Pom> resolveParent(final Path pomPath,
                                        final Raw raw,
                                        final Set<Path> visited) {
        if (raw.parentArtifactId == null) {
            return Optional.empty();
        }
        // try relativePath first (default ../pom.xml)
        final String relativePath = raw.parentRelativePath != null ? raw.parentRelativePath : "../pom.xml";
        if (!relativePath.isEmpty()) {
            Path candidate = pomPath.getParent().resolve(relativePath).normalize();
            if (Files.isDirectory(candidate)) {
                candidate = candidate.resolve("pom.xml");
            }
            if (Files.exists(candidate)) {
                final Pom parent = readEffective(candidate.toAbsolutePath().normalize(), visited);
                if (parent != null) {
                    return Optional.of(parent);
                }
            }
        }
        // fall back to local repository (requires a resolved version; ${...} unresolved → skip)
        if (raw.parentVersion == null || raw.parentVersion.contains("${")) {
            return Optional.empty();
        }
        final Path repoPath = this.localRepository
            .resolve(raw.parentGroupId.replace('.', '/'))
            .resolve(raw.parentArtifactId)
            .resolve(raw.parentVersion)
            .resolve(raw.parentArtifactId + "-" + raw.parentVersion + ".pom");
        if (!Files.exists(repoPath)) {
            return Optional.empty();
        }
        final Pom parent = readEffective(repoPath.toAbsolutePath().normalize(), visited);
        return Optional.ofNullable(parent);
    }

    // ----- raw parsing -------------------------------------------------------

    private static final class Raw {
        String groupId;
        String artifactId;
        String version;
        String parentGroupId;
        String parentArtifactId;
        String parentVersion;
        String parentRelativePath;
        Map<String, String> properties = Map.of();
        List<RawDependency> dependencies = List.of();
        List<RawDependency> dependencyManagement = List.of();
        List<RawPlugin> plugins = List.of();
        List<RawPlugin> pluginManagement = List.of();
    }

    private record RawDependency(String groupId,
                                 String artifactId,
                                 String version,
                                 String scope,
                                 String type,
                                 String classifier) {
    }

    private record RawPlugin(String groupId,
                             String artifactId,
                             String version,
                             ConfigNode configuration) {
    }

    private Raw parseRaw(final Path pomPath) {
        try {
            final DocumentBuilder builder = newDocumentBuilderFactory().newDocumentBuilder();
            final Document doc = builder.parse(pomPath.toFile());
            final Element root = doc.getDocumentElement();
            final Raw raw = new Raw();

            raw.groupId = directChildText(root, "groupId");
            raw.artifactId = directChildText(root, "artifactId");
            raw.version = directChildText(root, "version");

            for (final Element parentEl : directChildren(root, "parent")) {
                raw.parentGroupId = directChildText(parentEl, "groupId");
                raw.parentArtifactId = directChildText(parentEl, "artifactId");
                raw.parentVersion = directChildText(parentEl, "version");
                raw.parentRelativePath = directChildText(parentEl, "relativePath");
                break;
            }

            for (final Element propsEl : directChildren(root, "properties")) {
                final Map<String, String> props = new LinkedHashMap<>();
                for (final Element child : directChildElements(propsEl)) {
                    final String text = child.getTextContent();
                    props.put(child.getTagName(), text == null ? "" : text.trim());
                }
                raw.properties = props;
                break;
            }

            for (final Element depsEl : directChildren(root, "dependencies")) {
                raw.dependencies = readDependencies(depsEl);
                break;
            }

            for (final Element dmEl : directChildren(root, "dependencyManagement")) {
                for (final Element depsEl : directChildren(dmEl, "dependencies")) {
                    raw.dependencyManagement = readDependencies(depsEl);
                    break;
                }
                break;
            }

            for (final Element buildEl : directChildren(root, "build")) {
                for (final Element pluginsEl : directChildren(buildEl, "plugins")) {
                    raw.plugins = readPlugins(pluginsEl);
                    break;
                }
                for (final Element pmEl : directChildren(buildEl, "pluginManagement")) {
                    for (final Element pluginsEl : directChildren(pmEl, "plugins")) {
                        raw.pluginManagement = readPlugins(pluginsEl);
                        break;
                    }
                    break;
                }
                break;
            }

            return raw;
        } catch (final Exception e) {
            this.recorder.warn(e, "PomReader failed to parse [%s]", pomPath);
            return null;
        }
    }

    private static List<RawDependency> readDependencies(final Element parent) {
        final List<RawDependency> out = new ArrayList<>();
        for (final Element dep : directChildren(parent, "dependency")) {
            final String groupId = directChildText(dep, "groupId");
            final String artifactId = directChildText(dep, "artifactId");
            if (groupId == null || artifactId == null) {
                continue;
            }
            out.add(new RawDependency(
                groupId, artifactId,
                directChildText(dep, "version"),
                directChildText(dep, "scope"),
                directChildText(dep, "type"),
                directChildText(dep, "classifier")));
        }
        return out;
    }

    private static List<RawPlugin> readPlugins(final Element parent) {
        final List<RawPlugin> out = new ArrayList<>();
        for (final Element plugin : directChildren(parent, "plugin")) {
            final String artifactId = directChildText(plugin, "artifactId");
            if (artifactId == null) {
                continue;
            }
            final String groupId = directChildText(plugin, "groupId");
            // Maven default plugin groupId
            final String effectiveGroupId = groupId != null ? groupId : "org.apache.maven.plugins";
            ConfigNode configuration = ConfigNode.empty();
            for (final Element configEl : directChildren(plugin, "configuration")) {
                configuration = toConfigNode(configEl);
                break;
            }
            out.add(new RawPlugin(effectiveGroupId,
                artifactId,
                directChildText(plugin, "version"),
                configuration));
        }
        return out;
    }

    private static ConfigNode toConfigNode(final Element element) {
        final Map<String, String> attrs = new LinkedHashMap<>();
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            final Node attr = element.getAttributes().item(i);
            attrs.put(attr.getNodeName(), attr.getNodeValue());
        }
        final List<Element> childElements = directChildElements(element);
        if (childElements.isEmpty()) {
            final String text = element.getTextContent();
            final Optional<String> textOpt = (text == null || text.isBlank())
                ? Optional.empty()
                : Optional.of(text.trim());
            return new DefaultConfigNode(element.getTagName(), attrs, textOpt, List.of());
        }
        final List<ConfigNode> children = new ArrayList<>(childElements.size());
        for (final Element child : childElements) {
            children.add(toConfigNode(child));
        }
        return new DefaultConfigNode(element.getTagName(), attrs, Optional.empty(), children);
    }

    // ----- effective construction --------------------------------------------

    private static Dependency toDependency(final RawDependency rd,
                                           final Map<String, String> props) {
        final String groupId = interpolate(rd.groupId(), props);
        final String artifactId = interpolate(rd.artifactId(), props);
        final Optional<String> version = optInterpolated(rd.version(), props);
        final String scope = orDefault(interpolate(rd.scope(), props), "compile");
        final String type = orDefault(interpolate(rd.type(), props), "jar");
        final Optional<String> classifier = optInterpolated(rd.classifier(), props);
        return new DefaultDependency(groupId, artifactId, version, scope, type, classifier);
    }

    private static Dependency fillFromMgmt(final Dependency dep,
                                           final Map<GA, Dependency> mgmt) {
        final Dependency managed = mgmt.get(dep.ga());
        if (managed == null) {
            return dep;
        }
        return new DefaultDependency(
            dep.groupId(),
            dep.artifactId(),
            dep.version().or(managed::version),
            // own scope/type win iff non-default; absent original defaults are filled by toDependency
            "compile".equals(dep.scope()) ? managed.scope() : dep.scope(),
            "jar".equals(dep.type()) ? managed.type() : dep.type(),
            dep.classifier().or(managed::classifier));
    }

    private static Plugin toPlugin(final RawPlugin rp,
                                   final Map<String, String> props) {
        final String groupId = interpolate(rp.groupId(), props);
        final String artifactId = interpolate(rp.artifactId(), props);
        final Optional<String> version = optInterpolated(rp.version(), props);
        final ConfigNode interpolated = interpolateConfig(rp.configuration(), props);
        return new DefaultPlugin(groupId, artifactId, version, interpolated);
    }

    /**
     * Merges an own plugin (or pluginMgmt entry) over a parent/mgmt entry, deep-merging
     * configuration. Returns own if mgmt is null (no parent or mgmt match).
     */
    private static Plugin mergePluginInto(final Plugin parent,
                                          final Plugin own) {
        if (parent == null) {
            return own;
        }
        return new DefaultPlugin(
            own.groupId(),
            own.artifactId(),
            own.version().or(parent::version),
            mergeConfig(parent.configuration(), own.configuration()));
    }

    /**
     * Deep-merges two configuration nodes: own wins per child element name, recursively. If own
     * is empty, parent is returned. If parent is empty, own is returned. Attributes and text are
     * own-wins-when-present.
     */
    private static ConfigNode mergeConfig(final ConfigNode parent,
                                          final ConfigNode own) {
        if (parent == ConfigNode.empty() || parent.name().isEmpty()) {
            return own;
        }
        if (own == ConfigNode.empty() || own.name().isEmpty()) {
            return parent;
        }
        final Map<String, String> attrs = new LinkedHashMap<>(parent.attributes());
        attrs.putAll(own.attributes());
        final Optional<String> text = own.text().or(parent::text);
        // child merge by name: own takes precedence; same-named children are recursively merged
        final Map<String, ConfigNode> byName = new LinkedHashMap<>();
        for (final ConfigNode pc : parent.children()) {
            byName.put(pc.name(), pc);
        }
        for (final ConfigNode oc : own.children()) {
            final ConfigNode existing = byName.get(oc.name());
            byName.put(oc.name(), existing == null ? oc : mergeConfig(existing, oc));
        }
        return new DefaultConfigNode(own.name(), attrs, text, List.copyOf(byName.values()));
    }

    // ----- interpolation -----------------------------------------------------

    /**
     * Replaces {@code ${prop}} references with their resolved values. Coord-style references
     * matching {@link #COORD_REF} are passed through unchanged. Unresolved property references
     * are also passed through unchanged.
     */
    static String interpolate(final String value,
                              final Map<String, String> props) {
        if (value == null || value.indexOf('$') < 0) {
            return value;
        }
        final Matcher m = PROPERTY_REF.matcher(value);
        final StringBuilder out = new StringBuilder();
        while (m.find()) {
            final String full = m.group(0);
            // Skip coord-style refs; they belong to the consumer, not pom-property interpolation.
            if (COORD_REF.matcher(full).matches()) {
                m.appendReplacement(out, Matcher.quoteReplacement(full));
                continue;
            }
            final String key = m.group(1);
            final String resolved = props.get(key);
            m.appendReplacement(out, Matcher.quoteReplacement(resolved != null ? resolved : full));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Optional<String> optInterpolated(final String value,
                                                    final Map<String, String> props) {
        if (value == null) {
            return Optional.empty();
        }
        final String resolved = interpolate(value, props);
        return (resolved == null || resolved.isEmpty()) ? Optional.empty() : Optional.of(resolved);
    }

    private static ConfigNode interpolateConfig(final ConfigNode node,
                                                final Map<String, String> props) {
        if (node == ConfigNode.empty() || node.name().isEmpty()) {
            return node;
        }
        final Optional<String> text = node.text().map(t -> interpolate(t, props));
        final List<ConfigNode> children = node.children().stream()
            .map(c -> interpolateConfig(c, props))
            .toList();
        return new DefaultConfigNode(node.name(), node.attributes(), text, children);
    }

    // ----- xml helpers (local to keep this class self-contained) -------------

    private static DocumentBuilderFactory newDocumentBuilderFactory() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static List<Element> directChildren(final Element parent,
                                                final String tagName) {
        final List<Element> out = new ArrayList<>();
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && child.getTagName().equals(tagName)) {
                out.add(child);
            }
        }
        return out;
    }

    private static List<Element> directChildElements(final Element parent) {
        final List<Element> out = new ArrayList<>();
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                out.add(child);
            }
        }
        return out;
    }

    private static String directChildText(final Element parent,
                                          final String tagName) {
        for (final Element child : directChildren(parent, tagName)) {
            final String text = child.getTextContent();
            return (text == null || text.isBlank()) ? null : text.trim();
        }
        return null;
    }

    private static String orDefault(final String value,
                                    final String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }
}
