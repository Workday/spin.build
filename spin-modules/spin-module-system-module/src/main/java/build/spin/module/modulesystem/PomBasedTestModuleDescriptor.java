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
import build.spin.Project;
import build.spin.Resource;
import build.spin.Workspace;
import jakarta.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;

/**
 * A {@link TestModuleDescriptor} {@link Resource} that derives test module requirements from
 * test-scoped {@code <dependency>} entries in a project's {@code pom.xml}, for use when no
 * {@code src/test/java/module-info.java} is present.
 * <p>
 * This resource is workspace-level. The {@link #get(Project)} method reads the specific
 * sub-project's {@code pom.xml} on each call, using properties from the workspace root pom.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public class PomBasedTestModuleDescriptor
    implements TestModuleDescriptor, Resource {

    private static final String POM_FILENAME = "pom.xml";

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private Workspace workspace;

    @Override
    public ModuleDescriptor get(final Project project) {
        final ModuleDescriptor.Builder builder =
            ModuleDescriptor.Builder.create(project.name().replace("-", "."))
                .noLocation()
                .setOpen(true)
                .setAutomatic(true);

        final Path rootPom = workspace.path().resolve(POM_FILENAME);
        if (!Files.exists(rootPom)) {
            return builder.build();
        }

        try {
            final DocumentBuilder xmlBuilder = PomXmlUtils.newDocumentBuilderFactory().newDocumentBuilder();

            final Map<String, String> properties = PomXmlUtils.readProperties(xmlBuilder, rootPom);

            final Path projectPom = project.path().resolve(POM_FILENAME);
            if (Files.exists(projectPom)) {
                registerTestRequires(xmlBuilder, projectPom, properties, builder);
            }

        } catch (final Exception e) {
            this.recorder.warn(e, "PomBasedTestModuleDescriptor failed for [%s]", project.name());
        }

        return builder.build();
    }

    private void registerTestRequires(final DocumentBuilder builder,
                                      final Path pomPath,
                                      final Map<String, String> properties,
                                      final ModuleDescriptor.Builder descriptorBuilder) {
        try {
            final Document doc = builder.parse(pomPath.toFile());
            final NodeList deps = doc.getElementsByTagName("dependency");

            for (int i = 0; i < deps.getLength(); i++) {
                if (!(deps.item(i) instanceof Element dep)) {
                    continue;
                }

                // exclude provided and system deps; include compile, runtime, and test
                final String scope = PomXmlUtils.textContent(dep, "scope");
                if ("provided".equals(scope) || "system".equals(scope)) {
                    continue;
                }

                final String groupId = PomXmlUtils.textContent(dep, "groupId");
                final String artifactId = PomXmlUtils.textContent(dep, "artifactId");
                final String rawVersion = PomXmlUtils.textContent(dep, "version");

                if (groupId == null || artifactId == null || rawVersion == null) {
                    continue;
                }

                final String resolvedVersion = PomXmlUtils.resolveProperty(rawVersion, properties);
                if (resolvedVersion == null || resolvedVersion.contains("${")) {
                    continue;
                }

                // register under the same three key conventions used by PomBasedModuleCatalog
                descriptorBuilder.requires(groupId, null, null);

                final String derivedName = PomXmlUtils.derivedModuleName(artifactId);
                descriptorBuilder.requires(derivedName, null, null);

                final String lastSegment = PomXmlUtils.lastHyphenSegment(artifactId);
                if (!lastSegment.isEmpty()) {
                    descriptorBuilder.requires(groupId + "." + lastSegment, null, null);
                }
            }
        } catch (final Exception e) {
            this.recorder.warn(e, "PomBasedTestModuleDescriptor failed to read test deps from [%s]", pomPath);
        }
    }

    /**
     * The {@link Resource.MetaClass} for {@link PomBasedTestModuleDescriptor}.
     */
    public static class MetaClass
        implements Resource.MetaClass {

        @Override
        public boolean isWorkspace(final Path path) {
            return PomXmlUtils.isMavenWorkspaceRoot(path);
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            return project instanceof Workspace
                && Files.exists(project.path().resolve(POM_FILENAME));
        }
    }
}
