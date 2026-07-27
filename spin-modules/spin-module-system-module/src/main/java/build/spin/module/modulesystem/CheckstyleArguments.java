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
import build.spin.Resource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link Resource} that supplies Checkstyle configuration derived from a {@link Project}'s own
 * build descriptor. Project-shape-agnostic — implementations exist per build descriptor format.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public interface CheckstyleArguments
    extends Resource {

    /**
     * Returns the path to the Checkstyle configuration file the project declares, if any,
     * resolved to an absolute path. Returns empty when the project declares no configuration.
     */
    Optional<Path> configurationPath(Project project);

    /**
     * Returns Maven coordinate tokens ({@code groupId:artifactId:version}) for extra artifacts
     * (e.g. custom check jars) the project's Checkstyle configuration should be launched with, in
     * addition to Checkstyle itself. Returns an empty stream when the project declares none.
     */
    Stream<String> additionalCheckArtifacts(Project project);

    /**
     * Returns whether the project's Checkstyle configuration declares
     * {@code <includeTestSourceDirectory>true</includeTestSourceDirectory>}. Defaults to
     * {@code false}, matching {@code maven-checkstyle-plugin}'s own default.
     */
    boolean includeTestSourceDirectory(Project project);
}
