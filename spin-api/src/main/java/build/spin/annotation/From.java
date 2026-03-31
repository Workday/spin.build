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
 * Specifies that the result of a specific {@link Class} of {@link Task} with in the current {@link Project},
 * must be injected as a parameter of another {@link Task}.
 * <p>
 * The {@link From} annotation is typically used by {@link Task}s to specify a prerequisite and thus dependency on
 * the result of another {@link Task}.  For example, the following specifies that {@code PathSet}
 * produced by the {@code SourceAnalysis} {@link Class} is required for executing the {@code ClassPathAnalysis}
 * {@code analyze} method.
 *
 * <pre><code>public class ClassPathAnalysis implements Task&lt;ClassPath&gt; {
 *
 *    ...
 *
 *    public ClassPath analyze(&#64;From(SourceAnalysis.class) PathSet sourceCode) {
 *
 *    ...
 *
 *    }
 *
 * }</code></pre>
 *
 *
 * @author brian.oliver
 * @since Jun-2019
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PARAMETER })
public @interface From {

    /**
     * Obtains the {@link Class} of the prerequisite {@link Task}, from which a result may be acquired for injection.
     *
     * @return the {@link Class} of the prerequisite {@link Task}
     */
    Class<? extends Task<?>> value();
}
