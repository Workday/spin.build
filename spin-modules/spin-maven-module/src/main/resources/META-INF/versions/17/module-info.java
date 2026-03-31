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
    requires transitive build.spin.engine;
    requires transitive build.spin.module.java;
    requires transitive build.spin.module.modulesystem;

    requires transitive maven.core;
    requires transitive maven.settings.builder;
    requires transitive maven.resolver.provider;

    requires transitive org.apache.maven.resolver;
    requires transitive org.apache.maven.resolver.spi;
    requires transitive org.apache.maven.resolver.util;
    requires transitive org.apache.maven.resolver.impl;
    requires transitive org.apache.maven.resolver.connector.basic;
    requires transitive org.apache.maven.resolver.transport.file;
    requires transitive org.apache.maven.resolver.transport.http;
}
