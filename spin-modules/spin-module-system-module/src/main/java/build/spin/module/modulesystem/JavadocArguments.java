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
 * A {@link Resource} that supplies the {@code javadoc} argument tokens a {@link Project} should
 * have its documentation generated with. Project-shape-agnostic — implementations exist per build
 * descriptor format.
 * <p>
 * Returned tokens are <em>resolved</em> with respect to the project's own configuration and ready
 * to be passed to javadoc verbatim.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public interface JavadocArguments
    extends Resource {

    /**
     * Returns the {@code javadoc} argument tokens for the given {@link Project}, in the order they
     * should be passed to the tool. Returns an empty stream when the project declares no javadoc
     * arguments.
     */
    Stream<String> get(Project project);
}
