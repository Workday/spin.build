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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Targeted unit tests for {@link PomXmlUtils}. Most behavior is exercised at the walker level
 * by {@link PomWorkspaceWalkerTests}; this file only covers things that are hard or impossible
 * to reach from there:
 * <ul>
 *   <li>security: XXE rejection in the document builder factory</li>
 *   <li>the {@link PomXmlUtils#directChildText direct-child} vs descendant distinction, which is
 *       subtle and easy to regress</li>
 *   <li>negative cases of the module-name derivation heuristics — the positive cases fire on every
 *       realistic pom, so the walker tests cover them; the negative "returns empty" branches do not</li>
 *   <li>null-handling edge cases in {@link PomXmlUtils#resolveProperty}</li>
 * </ul>
 */
class PomXmlUtilsTests {

    // -------------------------------------------------------------------------
    // security
    // -------------------------------------------------------------------------

    @Test
    void newDocumentBuilderFactory_rejectsXmlWithDoctype() throws Exception {
        final DocumentBuilder builder = PomXmlUtils.newDocumentBuilderFactory().newDocumentBuilder();
        final String malicious = "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
            + "<root>&xxe;</root>";
        assertThrows(SAXParseException.class, () ->
            builder.parse(new ByteArrayInputStream(malicious.getBytes(StandardCharsets.UTF_8))));
    }

    // -------------------------------------------------------------------------
    // directChildText — the distinction from textContent matters for parent-inheriting self-artifacts
    // -------------------------------------------------------------------------

    @Test
    void directChildText_returnsDirectChildAndIgnoresNestedMatchUnderParent() throws Exception {
        // a child pom's own <groupId> appears directly under <project>, while an inherited one
        // appears under <parent>. directChildText must NOT confuse the two.
        final Element root = parseDocument(
            "<root><parent><groupId>inherited</groupId></parent><groupId>direct</groupId></root>");
        assertThat(PomXmlUtils.directChildText(root, "groupId")).isEqualTo("direct");

        final Element onlyNested = parseDocument(
            "<root><parent><groupId>inherited</groupId></parent></root>");
        assertThat(PomXmlUtils.directChildText(onlyNested, "groupId")).isNull();
    }

    // -------------------------------------------------------------------------
    // resolveProperty — null-handling branches not reached by realistic walker tests
    // -------------------------------------------------------------------------

    @Test
    void resolveProperty_returnsNullForUnresolvableReference() {
        assertThat(PomXmlUtils.resolveProperty("${missing}", Map.of())).isNull();
    }

    // -------------------------------------------------------------------------
    // derivation heuristics — negative cases
    //
    // Positive cases are exercised by PomWorkspaceWalkerTests against realistic poms.
    // These empty-return branches fire only on specific groupId/artifactId shapes that no
    // walker test exercises end-to-end, so they need direct unit coverage.
    // -------------------------------------------------------------------------

    @Test
    void groupPrefixedModuleName_returnsEmptyWhenPrefixDoesNotMatch() {
        // org.junit.jupiter:junit-jupiter-api — first segment "junit" != last groupId segment "jupiter"
        assertThat(PomXmlUtils.groupPrefixedModuleName("org.junit.jupiter", "junit-jupiter-api"))
            .isEmpty();
    }

    @Test
    void groupPrefixedModuleName_returnsEmptyForSingleSegmentArtifactId() {
        assertThat(PomXmlUtils.groupPrefixedModuleName("build.base", "base")).isEmpty();
    }

    @Test
    void groupSuffixedModuleName_returnsEmptyWhenSuffixDoesNotMatch() {
        // org.junit.jupiter:junit-jupiter-api — last segment "api" != last groupId segment "jupiter"
        assertThat(PomXmlUtils.groupSuffixedModuleName("org.junit.jupiter", "junit-jupiter-api"))
            .isEmpty();
    }

    @Test
    void groupSuffixedModuleName_returnsEmptyForSingleSegmentArtifactId() {
        assertThat(PomXmlUtils.groupSuffixedModuleName("build.codemodel", "codemodel")).isEmpty();
    }

    @Test
    void groupParentWithLastArtifactSegment_returnsEmptyWhenGroupIdHasNoParent() {
        assertThat(PomXmlUtils.groupParentWithLastArtifactSegment("singlegroup", "some-artifact"))
            .isEmpty();
    }

    @Test
    void groupParentWithLastArtifactSegment_returnsEmptyForSingleSegmentArtifactId() {
        assertThat(PomXmlUtils.groupParentWithLastArtifactSegment("com.example.sub", "artifact"))
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // findJarByModuleName
    // -------------------------------------------------------------------------

    @Test
    void findJarByModuleName_returnsEmptyForSingleSegmentModuleName(@TempDir final Path repo) {
        assertThat(PomXmlUtils.findJarByModuleName("singlemodule", "1.0.0", repo)).isEmpty();
    }

    @Test
    void findJarByModuleName_returnsEmptyWhenNoJarExists(@TempDir final Path repo) {
        assertThat(PomXmlUtils.findJarByModuleName("build.spin.module.clean", "0.1.0", repo)).isEmpty();
    }

    @Test
    void findJarByModuleName_findsJarByNamingConvention(@TempDir final Path repo) throws Exception {
        // build.spin.module.clean -> groupId=build.spin.module, candidates include spin-clean-module
        final Path jarDir = repo.resolve("build/spin/module/spin-clean-module/0.1.0");
        Files.createDirectories(jarDir);
        Files.createFile(jarDir.resolve("spin-clean-module-0.1.0.jar"));

        final String[] coord = PomXmlUtils.findJarByModuleName("build.spin.module.clean", "0.1.0", repo).orElseThrow();
        assertThat(coord[0]).isEqualTo("build.spin.module");
        assertThat(coord[1]).isEqualTo("spin-clean-module");
        assertThat(coord[2]).isEqualTo("0.1.0");
    }

    // -------------------------------------------------------------------------
    // isMavenWorkspaceRoot — spin-native workspaces with a pom.xml must be recognized
    // -------------------------------------------------------------------------

    @Test
    void isMavenWorkspaceRoot_returnsTrueForSpinNativeWorkspaceWithPom(@TempDir final Path dir) throws Exception {
        Files.createFile(dir.resolve("pom.xml"));
        Files.writeString(dir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
        Files.createFile(dir.resolve(".spinignore"));
        assertThat(PomXmlUtils.isMavenWorkspaceRoot(dir)).isTrue();
    }

    // -------------------------------------------------------------------------

    private static Element parseDocument(final String xml) throws Exception {
        final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }
}
