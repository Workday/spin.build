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
import build.codemodel.foundation.descriptor.RequiresModuleDescriptor;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.codemodel.jdk.descriptor.ModuleModifier;
import build.codemodel.jdk.descriptor.OpenModule;
import build.spin.Project;
import build.spin.Resource;
import build.spin.Workspace;
import jakarta.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilder;

/**
 * A {@link TestModuleDescriptor} {@link Resource} that derives test module requirements from
 * test-scoped {@code <dependency>} entries in a project's {@code pom.xml}, for use when no
 * {@code src/test/java/module-info.java} is present.
 * <p>
 * This resource is workspace-level. The {@link #get(Project)} method reads the specific
 * sub-project's {@code pom.xml} on each call. Dependencies without an explicit version are still
 * registered; their versions are resolved later via {@code ModuleVersioning}.
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
    private CodeModel codeModel;

    @Override
    public JDKModuleDescriptor get(final Project project) {
        final String name = project.name().replace("-", ".");
        final ModuleName moduleName = this.codeModel.getNameProvider().getModuleName(name).orElseThrow();
        // Use of() rather than createModuleDescriptor() — this is a transient descriptor used only
        // to carry pom-derived requires into the caller's merge; it must not occupy the shared
        // CodeModel registry slot that parse() needs for the real module descriptor.
        final JDKModuleDescriptor descriptor = JDKModuleDescriptor.of(this.codeModel, moduleName);
        descriptor.addTrait(OpenModule.OPEN);
        descriptor.addTrait(ModuleModifier.AUTOMATIC);

        try {
            final DocumentBuilder xmlBuilder = PomXmlUtils.newDocumentBuilderFactory().newDocumentBuilder();

            final Path projectPom = project.path().resolve(POM_FILENAME);
            if (Files.exists(projectPom)) {
                registerTestRequires(xmlBuilder, projectPom, descriptor);
            }

        } catch (final Exception e) {
            this.recorder.warn(e, "PomBasedTestModuleDescriptor failed for [%s]", project.name());
        }

        return descriptor;
    }

    private void registerTestRequires(final DocumentBuilder builder,
                                      final Path pomPath,
                                      final JDKModuleDescriptor descriptor) {
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

                if (groupId == null || artifactId == null) {
                    continue;
                }

                // register under the same three key conventions used by PomBasedModuleCatalog
                final ModuleName groupIdName =
                    this.codeModel.getNameProvider().getModuleName(groupId).orElseThrow();
                descriptor.addTrait(RequiresModuleDescriptor.of(this.codeModel, groupIdName));

                final String derivedName = PomXmlUtils.derivedModuleName(artifactId);
                final ModuleName derivedModuleName =
                    this.codeModel.getNameProvider().getModuleName(derivedName).orElseThrow();
                descriptor.addTrait(RequiresModuleDescriptor.of(this.codeModel, derivedModuleName));

                final String lastSegment = PomXmlUtils.lastHyphenSegment(artifactId);
                if (!lastSegment.isEmpty()) {
                    final String combinedName = groupId + "." + lastSegment;
                    final ModuleName combinedModuleName =
                        this.codeModel.getNameProvider().getModuleName(combinedName).orElseThrow();
                    descriptor.addTrait(RequiresModuleDescriptor.of(this.codeModel, combinedModuleName));
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
