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
import build.spin.Project;
import build.spin.Task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that the results for a {@link Task} must <strong>not be cached</strong> for reuse and injection into
 * other {@link Task}s across {@link Program} executions.  This ensures a {@link Task} is executed for each
 * {@link Project} in a {@link Program}, regardless of previous executions in other {@link Program}s.
 * <p>
 * By default, all {@link Task} execution results are cached.
 * <p>
 * For example, the following {@link Task} must be executed in each {@link Program}, regardless of how many times it
 * is executed of other {@link Program}s.
 * </p>
 * <pre>{@code @NoCache}
 * public static class GenerateReport implements Task&lt;FileSet&gt; { ... } }</pre>
 *
 * @author brian.oliver
 * @since Jan-2021
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface NoCache {

}
