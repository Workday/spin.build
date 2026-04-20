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

module build.spin.module.java {
    requires transitive build.spin.engine;
    requires transitive build.spin.module.clean;
    requires transitive build.spin.module.configuration;
    requires transitive build.spin.module.modulesystem;

    requires build.base.expression;
    requires build.base.flow;
    requires build.base.foundation;
    requires build.base.io;
    requires build.base.option;
    requires build.base.table;
    requires build.base.telemetry;
    requires build.codemodel.injection;
    requires build.codemodel.jdk;
    requires build.spawn.application;
    requires build.spawn.jdk;
    requires build.spawn.platform.local;
    requires build.spawn.platform.local.jdk;
    requires build.spin;
    requires build.spin.common;
    requires freemarker;
    requires jakarta.inject;

    uses build.spawn.platform.local.jdk.JDKDetector;

    opens build.spin.module.java to build.codemodel.injection;

    exports build.spin.module.java;

    provides build.spin.Extension.MetaClass with
        build.spin.module.java.JavaPlatform.MetaClass,
        build.spin.module.java.CustomizationPlugin.MetaClass,
        build.spin.module.java.Java8CompilerPlugin.MetaClass,
        build.spin.module.java.Java25CompilerPlugin.MetaClass,
        build.spin.module.java.ResourcePlugin.MetaClass;

}
