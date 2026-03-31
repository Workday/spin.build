package build.spin.option;

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

import build.base.configuration.AbstractValueOption;
import build.base.configuration.Default;
import build.spin.Plugin;

/**
 * Defines the name of the target directory in which to place compiled and other resources produced by
 * {@link Plugin}s.
 *
 * @author brian.oliver
 * @since Sep-2020
 */
public class TargetDirectoryName
    extends AbstractValueOption<String> {

    /**
     * Constructs a {@link TargetDirectoryName}.
     *
     * @param name the name
     */
    private TargetDirectoryName(final String name) {
        super(name);
    }

    @Default
    public static TargetDirectoryName automatic() {
        return new TargetDirectoryName("target");
    }
}
