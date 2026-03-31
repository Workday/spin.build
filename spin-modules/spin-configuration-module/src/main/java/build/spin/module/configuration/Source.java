package build.spin.module.configuration;

/*-
 * #%L
 * Spin Configuration Module
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

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the source location from which {@link Configuration} will be obtained and used for injection.
 * <p>
 * Typically, the {@link Source} location is the file name (without an module) of a file or directory that defines
 * content to be used for {@link Configuration}.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
public @interface Source {

    /**
     * Obtains the source location for {@link Configuration}.
     *
     * @return the source location for {@link Configuration}
     */
    String value();
}
