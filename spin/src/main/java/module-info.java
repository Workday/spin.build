/*-
 * #%L
 * Spin Application
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

module build.spin.application {
    requires transitive build.spin;
    requires transitive build.spin.common;
    requires transitive build.spin.engine;
    // build.spin.testing is transitive here so that spin-collider (which uses
    // requires transitive build.spin.application) has the testing framework on
    // its module path at compile time, enabling self-hosted integration tests.
    requires transitive build.spin.testing;
    requires transitive build.spin.module.checkstyle;
    requires transitive build.spin.module.clean;
    requires transitive build.spin.module.configuration;
    requires transitive build.spin.module.console;
    requires transitive build.spin.module.git;
    requires transitive build.spin.module.java;
    requires transitive build.spin.module.junit;
    requires transitive build.spin.module.languageserver;
    requires transitive build.spin.module.maven;
    requires transitive build.spin.module.modulesystem;
    requires transitive build.spin.module.reporting;
    requires org.slf4j.simple;

    requires build.base.commandline;
    requires build.base.configuration;
    requires build.base.foundation;
    requires build.base.option;
    requires build.base.table;
    requires org.slf4j;

    exports build.spin.application;

}
