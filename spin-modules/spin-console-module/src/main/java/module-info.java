/*-
 * #%L
 * Spin Console Module
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
module build.spin.module.console {
    requires transitive build.spin.engine;

    // graphql-java, undertow, and kickstart are implementation details — not part of the exported API
    requires com.graphqljava;
    requires undertow.core;
    requires undertow.servlet;
    requires graphql.java.kickstart;
    requires graphql.java.servlet;

    requires build.base.foundation;
    requires build.base.telemetry;
    requires build.codemodel.injection;
    requires build.spin;
    requires jakarta.inject;
    requires jakarta.servlet;
    requires java.security.sasl;
    requires jdk.unsupported;

    opens build.spin.module.console to build.codemodel.injection;

    exports build.spin.module.console;
    provides build.spin.Extension.MetaClass with
        build.spin.module.console.WorkspaceConsole.MetaClass;

}
