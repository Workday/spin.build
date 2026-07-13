/*-
 * #%L
 * Spin Maven Module
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

module build.spin.module.maven {
    requires build.spin.common;
    requires transitive build.spin.module.java;
    requires transitive build.spin.module.modulesystem;
    // Aether/Maven resolver are implementation details — not exposed in the public API
    requires maven.resolver.provider;
    requires maven.settings.builder;
    requires org.apache.maven.resolver;
    requires org.apache.maven.resolver.supplier;
    requires org.apache.maven.resolver.util;

    requires java.xml;
    // maven-resolver-transport-apache → httpclient → SSLConnectionSocketFactory
    // needs javax.naming for hostname verification; previously pulled in
    // transitively by undertow (now removed in favour of serve.build).
    requires java.naming;
    requires maven.settings;
    requires build.base.archiving;
    requires build.base.configuration;
    requires build.base.expression;
    requires build.base.foundation;
    requires build.base.io;
    requires build.base.option;
    requires build.base.telemetry;
    requires build.codemodel.injection;
    requires build.codemodel.jdk;
    requires build.spin;
    requires build.spin.module.clean;
    requires jakarta.inject;

    opens build.spin.module.maven to build.codemodel.injection;

    exports build.spin.module.maven;

    provides build.spin.Extension.MetaClass with
        build.spin.module.maven.MavenPlugin.MetaClass,
        build.spin.module.maven.MavenRepository.MetaClass;

}
