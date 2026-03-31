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

import build.spin.Project;
import build.spin.Task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a {@link Task} with in the current {@link Project} is a codependency which performs pre-processing of
 * information, prior to the execution of another {@link Task}.
 * <p>
 * The {@link PreProcess} annotation is typically used by {@link Task}s to specify that they are a codependency that
 * must be executed prior to another {@link Task}.  For example, the following specifies that {@code CopyResources}
 * {@link Task} is a pre-processing codependency of the {@code Compile} {@link Task}.
 * <pre>{@code @PreProcess(Compile.class)
 * public static class CopyResources implements Task<PathSet> { ... } }</pre>
 *
 * @see PostProcess
 *
 * @author brian.oliver
 * @since Oct-2019
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface PreProcess {

    /**
     * Obtains the {@link Class} of the {@link Task} for which the annotated {@link Task} is a pre-processing
     * codependency.
     *
     * @return the {@link Class} of the {@link Task}
     */
    Class<? extends Task<?>> value();
}
