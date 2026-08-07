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

import build.base.commandline.CommandLine;
import build.base.configuration.Default;
import build.base.configuration.Option;

/**
 * An {@link Option} controlling which target platforms a {@code jlink} {@link build.spin.Task} links a
 * runtime image for.
 *
 * @author reed.vonredwitz
 * @since Aug-2026
 */
public enum JlinkTargets
    implements Option {

    /**
     * Link only the host's own platform, skipping any other staged JDK targets.
     */
    HOST_ONLY,

    /**
     * Link every staged JDK target platform.
     */
    @Default
    ALL_STAGED;

    /**
     * Creates a {@link JlinkTargets} based on the {@link CommandLine} {@link Option}.
     *
     * @return a {@link JlinkTargets}
     */
    @CommandLine.Prefix("--jlink-host-only")
    @CommandLine.Description("Restrict jlink to the host's own platform, skipping any other staged JDK targets")
    public static JlinkTargets hostOnly() {
        return JlinkTargets.HOST_ONLY;
    }
}
