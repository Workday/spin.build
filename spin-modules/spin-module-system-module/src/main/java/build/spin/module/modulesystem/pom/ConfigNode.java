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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A node in a Maven plugin {@code <configuration>} tree.
 * <p>
 * Each node carries an element name, attributes, optional text content (for leaf elements),
 * and child nodes (for container elements). Real Maven configuration elements always have one
 * of: text content, or children — never both meaningfully — but the model permits both.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public sealed interface ConfigNode
    permits DefaultConfigNode {

    /**
     * The element name (e.g. {@code "argLine"}, {@code "configuration"}).
     */
    String name();

    /**
     * The element's XML attributes, keyed by attribute name. Empty if the element has no attributes.
     */
    Map<String, String> attributes();

    /**
     * The element's text content, if it is a leaf element with text. Empty for container elements.
     */
    Optional<String> text();

    /**
     * The element's direct child elements, in document order. Empty for leaf elements.
     */
    List<ConfigNode> children();

    /**
     * Returns the first direct child with the given element name, if any.
     */
    default Optional<ConfigNode> child(final String childName) {
        return children().stream().filter(c -> c.name().equals(childName)).findFirst();
    }

    /**
     * Returns all direct children with the given element name, in document order.
     */
    default Stream<ConfigNode> children(final String childName) {
        return children().stream().filter(c -> c.name().equals(childName));
    }

    /**
     * Returns the text content of the first direct child with the given name, if both the child
     * and its text are present.
     */
    default Optional<String> textChild(final String childName) {
        return child(childName).flatMap(ConfigNode::text);
    }

    /**
     * If a direct child with {@code childName} exists and has text, emits the stream
     * {@code [flagName, <text>]}. Otherwise emits an empty stream. Convenience for translating
     * {@code <release>25</release>} → {@code --release 25}-style CLI flag pairs.
     */
    default Stream<String> flagIfPresent(final String childName, final String flagName) {
        return textChild(childName).stream().flatMap(v -> Stream.of(flagName, v));
    }

    /**
     * If a direct child with {@code childName} exists and its text is (case-insensitively)
     * {@code "true"}, emits the single-token stream {@code [flagName]}. Otherwise emits empty.
     * Convenience for translating {@code <enablePreview>true</enablePreview>} →
     * {@code --enable-preview}-style boolean CLI flags.
     */
    default Stream<String> booleanFlag(final String childName, final String flagName) {
        return textChild(childName)
            .filter("true"::equalsIgnoreCase)
            .stream()
            .map(__ -> flagName);
    }

    /**
     * Returns a sentinel empty configuration node — no name, no attributes, no text, no children.
     * Used by {@link Plugin#configuration()} when a plugin declares no {@code <configuration>} block.
     */
    static ConfigNode empty() {
        return DefaultConfigNode.EMPTY;
    }
}
