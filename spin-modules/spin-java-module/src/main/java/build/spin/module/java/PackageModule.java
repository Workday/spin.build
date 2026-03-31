package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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
import build.spin.Task;
import build.spin.module.modulesystem.ArtifactDescriptor;

/**
 * Creates a Java Archive (jar) containing the compiled byte code and resources for the {@link Project}, returning
 * an {@link ArtifactDescriptor} describing the packaged {@link ArtifactDescriptor}.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public interface PackageModule
    extends Task<ArtifactDescriptor> {

}
