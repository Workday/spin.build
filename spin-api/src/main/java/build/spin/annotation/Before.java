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
 * on which this annotation is present, is a dependent that must be performed prior to the said {@link Task}.
 * <p>
 * The {@link Before} annotation is typically used by {@link Task}s to specify that they are a dependency of another
 * {@link Task}, and thus they must be executed prior to said {@link Task}.  For example, the following specifies that
 * {@code CopyResources} {@link Task} is a dependency for the {@code Compile} {@link Task}, and thus must be executed
 * before the {@code Compile} {@link Task}.
 * <p>
 * {@link Before} is different from {@link PreProcess} in that:
 * <ol>
 *     <li>{@link Before} creates a dependent {@link Task}, where as
 *         {@link PreProcess} defines a codependent {@link Task}.</li>
 *     <li>The result of a {@link Before} {@link Task} may be used by many other {@link Task}s, where as
 *         {@link PreProcess} {@link Task} results may only be used by its codependent {@link Task}.</li>
 *     <li>{@link Before} {@link Task}s will only be executed if required, where as
 *         {@link PreProcess} {@link Task}s will always be executed with their codependent {@link Task}.</li>
 * </ol>
 * <pre>{@code @Before(Compile.class)
 * public static class CopyResources implements Task<FileSet> { ... } }</pre>
 *
 * @see After
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
public @interface Before {

    /**
     * Obtains the {@link Class} of the {@link Task} for which the annotated {@link Task} is a dependency.
     *
     * @return the {@link Class} of the {@link Task}
     */
    Class<? extends Task<?>> value();
}
