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
 * Specifies the {@link Class} of {@link Task} with in the current {@link Project}, for which the {@link Class}
 * on which this annotation is present, is a dependency that must be performed after to the said {@link Task}.
 * <p>
 * The {@link After} annotation is typically used by {@link Task}s to specify that they have a dependency on another
 * {@link Task}, for which they must be executed after the said {@link Task}.  For example, the following specifies that
 * {@code CopyResources} {@link Task} has a dependency on the {@code Compile} {@link Task}, and thus must be executed
 * after the {@code Compile} {@link Task}.
 * <p>
 * {@link After} is different from {@link PostProcess} in that:
 * <ol>
 *     <li>{@link After} creates a dependency {@link Task}, where as
 *         {@link PostProcess} defines a codependent {@link Task}.</li>
 *     <li>The result of a {@link After} {@link Task} may be used by many other {@link Task}s, where as
 *         {@link PostProcess} {@link Task} results may only be used by its codependent {@link Task}.</li>
 *     <li>{@link After} {@link Task}s will only be executed if required, where as
 *         {@link PostProcess} {@link Task}s will always be executed with their codependent {@link Task}.</li>
 * </ol>
 * <pre>{@code @After(Compile.class)
 * public static class CopyResources implements Task<FileSet> { ... } }</pre>
 *
 * @see Before
 * @see PreProcess
 * @see PostProcess
 *
 * @author brian.oliver
 * @since May-2020
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface After {

    /**
     * Obtains the {@link Class} of the {@link Task} for which the annotated {@link Task} is a dependent.
     *
     * @return the {@link Class} of the {@link Task}
     */
    Class<? extends Task<?>> value();
}
