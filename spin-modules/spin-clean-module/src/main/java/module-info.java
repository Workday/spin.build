/*-
 * #%L
 * Spin Clean Module
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

module build.spin.module.clean {
    requires transitive build.spin.engine;

    requires build.spin;
    requires jakarta.inject;

    opens build.spin.module.clean to build.codemodel.injection;

    exports build.spin.module.clean;

    provides build.spin.Extension.MetaClass with
        build.spin.module.clean.CleanPlugin.MetaClass;

}
