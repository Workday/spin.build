package build.spin.annotation;

/*-
 * #%L
 * Spin API
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

import build.spin.Invocable;
import build.spin.Task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the {@link Invocable} {@link Class} for a {@link Task}.
 * <p>
 * By default, the {@link Invocable} {@link Class} automatically detected.  However, there may be occasions
 * when the {@link Invocable} for a {@link Task} may need to be customized, in which case it may be
 * annotated using {@link SpecifiedBy}.
 *
 * @author brian.oliver
 * @since July-2020
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SpecifiedBy {

    /**
     * Obtains the {@link Class} of {@link Invocable} for a {@link Task}.
     *
     * @return the {@link Class} of {@link Invocable}
     */
    @SuppressWarnings("rawtypes")
    Class<? extends Invocable> value();
}
