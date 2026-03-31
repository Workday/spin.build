package build.spin.testing;

/*-
 * #%L
 * Spin Testing Support
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

import build.spin.Workspace;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;

/**
 * Specifies the relative {@link Path} of a {@link Workspace} for testing with the {@link WorkspaceDiscovery}
 * JUnit Extension, the {@link Path} being relative to the test {@code resources/workspaces} {@link Path}.
 *
 * @see WorkspaceDiscovery
 *
 * @author brian.oliver
 * @since Jun-2020
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WorkspacePath {

    /**
     * The relative {@link Workspace} {@link Path}.
     *
     * @return the relative {@link Workspace} {@link Path}
     */
    String value();
}
