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

import build.spin.Project;
import build.spin.Workspace;
import build.spin.module.modulesystem.pom.PomReader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Predicates for detecting Maven-based workspaces and projects, shared by every {@code
 * PomBased*.MetaClass} implementation.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
class PomWorkspaces {

    private PomWorkspaces() {
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
        if (!PomReader.hasDeclaredParent(pom)) {
            return true;
        }
        final Path parentDir = path.getParent();
        return parentDir == null || !Files.exists(parentDir.resolve("pom.xml"));
    }

    /**
     * The canonical {@code Resource.MetaClass.isDetectedIn} predicate for pom-based resources that
     * should also apply in spin-native workspaces (i.e. resources for which spin has no native
     * equivalent): the project is a Workspace and has a {@code pom.xml}, regardless of whether a
     * {@code .spinignore} marker is present.
     */
    static boolean isMavenWorkspaceProject(final Project project) {
        return project instanceof Workspace
            && Files.exists(project.path().resolve("pom.xml"));
    }
}
