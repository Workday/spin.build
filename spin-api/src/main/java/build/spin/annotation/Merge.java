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

import build.spin.Task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.Stream;

/**
 * Declares that an abstract (interface or abstract class) {@link Task} type can combine the results
 * produced by every one of its concrete implementors into a single result, so a {@code @From}
 * parameter requesting it may be declared as the plain result type instead of {@code Stream<T>}.
 * <p>
 * Without {@link Merge}, an abstract {@code @From} {@link Task} type with multiple concrete
 * implementors in a {@link build.spin.Project} forces every consumer to declare {@code Stream<T>}
 * and combine the individual results itself — correct, but it pushes the same combining logic onto
 * every consumer. A {@link Merge}-annotated type instead declares that logic once, as a
 * {@code public static T merge(Stream<T> results)} method on the annotated type itself:
 *
 * <pre><code>{@literal @}Merge
 * public interface SomeTask extends Task&lt;Set&lt;String&gt;&gt; {
 *
 *     Set&lt;String&gt; compute();
 *
 *     static Set&lt;String&gt; merge(Stream&lt;Set&lt;String&gt;&gt; results) {
 *         ...
 *     }
 * }</code></pre>
 * <p>
 * A consumer may then declare {@code @From(SomeTask.class) Set<String> values} directly: every
 * concrete {@code SomeTask} implementor present in the {@link build.spin.Project} still participates
 * (the same dependency edges are established as for {@code Stream<T>}), but the framework merges
 * their results via the declared {@code merge} method before injection, rather than arbitrarily
 * picking one.
 * <p>
 * The {@code merge} method must be {@code public static}, accept a single {@link Stream} parameter, and
 * return a type assignable to the annotated {@link Task}'s result type.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Merge {

}
