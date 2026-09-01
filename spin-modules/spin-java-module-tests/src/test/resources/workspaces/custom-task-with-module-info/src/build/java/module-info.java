/*-
 * #%L
 * Spin Java Module Tests
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

// build.spin.engine is inherited from Spin's own runtime; org.assertj.core is the one requires
// clause CustomizationPlugin.getDependencies() has to resolve through the ModuleCatalog +
// Artifact.Resolver -- the path where the version-pinning and Exceptional#ifPresent bugs lived.
// Because invocables() runs eagerly at workspace discovery, a regression there fails every test in
// this fixture, not just the one that inspects the classpath.
module build {
    requires build.spin.engine;
    requires org.assertj.core;
}
