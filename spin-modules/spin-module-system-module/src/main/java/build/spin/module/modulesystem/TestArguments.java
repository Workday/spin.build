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

import java.util.stream.Stream;

/**
 * A {@link Resource} that supplies the JVM argument tokens a {@link Project}'s tests should be
 * launched with. Project-shape-agnostic — implementations exist per build descriptor format
 * (Maven {@code pom.xml}, future Gradle {@code build.gradle}, future native spin config).
 * <p>
 * Returned tokens are <em>resolved</em> with respect to the project's own configuration
 * (property interpolation, parent inheritance, plugin-management merging). Tokens of the form
 * {@code ${groupId:artifactId:type[:classifier]}} (the {@code maven-dependency-plugin:properties}
 * goal output) are returned verbatim — they require a resolved test classpath, which the
 * consumer supplies.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public interface TestArguments
    extends Resource {

    /**
     * Returns the JVM argument tokens for the given {@link Project}'s tests, in the order they
     * should be passed to the test JVM. Returns an empty stream when the project declares no
     * test JVM arguments.
     */
    Stream<String> get(Project project);
}
