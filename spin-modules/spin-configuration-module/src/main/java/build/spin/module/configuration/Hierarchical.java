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

import build.base.foundation.stream.Streamable;
import build.base.foundation.stream.Streams;
import build.spin.Project;
import build.spin.Workspace;

import java.util.stream.Stream;

/**
 * A mechanism to obtain potentially overridden values for configuration in the {@link Project} hierarchy, the values
 * being returned in <i>search order</i>, the child-most {@link Project} configurations first, and the
 * {@link Workspace} last.
 *
 * @param <T> the type of value
 * @see Configuration
 *
 * @author brian.oliver
 * @since Apr-2021
 */
public interface Hierarchical<T>
    extends Streamable<T> {

    /**
     * Obtains a {@link Stream} of the hierarchical values in reverse order.
     *
     * @return a {@link Stream}
     */
    default Stream<T> reverse() {
        return Streams.reverse(stream());
    }

    /**
     * Determines if there are any values.
     *
     * @return {@code true} if there are values, {@code false} if there are no values
     */
    boolean isEmpty();
}
