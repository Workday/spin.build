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
module build.spin.java.module.tests.test {
    requires build.spawn.platform.local.jdk;
    requires build.base.assertion;
    requires build.base.flow;
    requires build.base.foundation;
    requires build.base.io;
    requires build.base.option;
    requires build.base.telemetry;
    requires build.spawn.application;
    requires build.spawn.jdk;
    requires build.spawn.platform.local;
    requires build.spin;
    requires build.spin.common;
    requires build.spin.module.checkstyle;
    requires build.spin.module.clean;
    requires build.spin.module.java;
    requires build.spin.module.junit;
    requires build.spin.module.maven;
    requires build.spin.testing;
    requires org.junit.jupiter.api;
    requires org.assertj.core;
    uses build.spawn.platform.local.jdk.JDKDetector;
    opens build.spin.module.java.tests to org.junit.platform.commons;
}
