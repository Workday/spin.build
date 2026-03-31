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

    requires transitive com.graphqljava;
    requires transitive io.undertow;
    requires transitive io.undertow.servlet;
    requires transitive graphql.kickstart.servlet;

    requires java.security.sasl;
    requires jdk.unsupported;

    exports build.spin.module.console;
}
