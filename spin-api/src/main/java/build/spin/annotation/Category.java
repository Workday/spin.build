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

import build.spin.Program;
import build.spin.Task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that a {@link Task} belongs a category of {@link Task}s.
 * <p>
 * {@link Category}s may be used, in addition to the optionally defined {@link jakarta.inject.Named} name of a
 * {@link Task}, to request execution in a {@link Program}.  When multiple {@link Task}s have the same {@link Category}
 * and the said {@link Category} is requested to be included in a {@link Program}, all of the said {@link Task}s will
 * be included for execution in the {@link Program}.   This allows multiple {@link Task}s to be categorized and
 * executed together.
 * </p>
 * <p>
 * {@link Task}s may belong to, and thus be annotated with multiple {@link Category} annotations.  For example, the
 * following {@link Task} belongs to both the {@code build} and {@code report} categories.  When <i>either</i> of these
 * {@link Category} names are specified for a {@link Program}, this {@link Task} will be included for execution.
 * </p>
 * <pre>
 * <code>{@literal @}Category("report")
 * {@literal @}Category("build")
 * public static class GenerateReport implements Task&lt;FileSet&gt; { ... }
 * </code>
 * </pre>
 *
 * @author brian.oliver
 * @since Mar-2021
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@Repeatable(Categories.class)
public @interface Category {

    /**
     * The name of the {@link Category}.
     *
     * @return the name of the {@link Category}
     */
    String value();
}
