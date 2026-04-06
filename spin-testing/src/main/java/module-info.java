/*-
 * #%L
 * Spin Testing
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

module build.spin.testing {
    requires transitive build.spin.engine;
    // build.spin.module.git is transitive as a convenience so integration tests that use
    // WorkspaceDiscovery (which discovers git-based workspaces) can resolve git types without
    // each test module needing its own explicit requires. Not part of the exported API.
    requires transitive build.spin.module.git;

    requires build.base.configuration;
    requires build.base.foundation;
    requires build.base.option;
    requires build.base.telemetry;
    requires build.codemodel.injection;
    requires build.spin;
    requires build.spin.common;
    requires org.assertj.core;
    requires org.junit.jupiter.api;

    exports build.spin.testing;

}
