package build.spin.testing;

/*-
 * #%L
 * Spin Testing Support
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

import build.base.option.JDKVersion;
import build.spin.Project;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the default version of Java required for testing a {@link Project} when using the
 * {@link WorkspaceDiscovery} JUnit Extension.
 *
 * @see WorkspaceDiscovery
 *
 * @author brian.oliver
 * @since Jun-2020
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireJavaVersion {

    /**
     * The Java Version, to be parsed with {@link JDKVersion#of(String)}.
     *
     * @return the Java Version
     */
    String value();
}
