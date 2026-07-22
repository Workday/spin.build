/*-
 * #%L
 * Spin Common
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

module build.spin.common {
    requires transitive build.spin;

    requires build.base.configuration;
    requires build.base.flow;
    requires build.base.graph;
    requires build.base.foundation;
    requires build.base.io;
    requires build.base.telemetry;
    requires build.codemodel.foundation;
    requires build.codemodel.dependency.injection;
    requires build.codemodel.jdk;
    requires jakarta.inject;

    opens build.spin.common to build.codemodel.dependency.injection;
    opens build.spin.common.annotation to build.codemodel.dependency.injection;
    opens build.spin.common.injection to build.codemodel.dependency.injection;
    opens build.spin.common.task to build.codemodel.dependency.injection;

    exports build.spin.common;
    exports build.spin.common.annotation;
    exports build.spin.common.injection;
    exports build.spin.common.reactive;
    exports build.spin.common.task;
    exports build.spin.common.telemetry;
    exports build.spin.common.util;

}
