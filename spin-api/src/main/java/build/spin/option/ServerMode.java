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
 * An {@link Option} to indicate that <i>Server Mode</i> is required by the {@code spin} CLI.
 *
 * @see build.spin.Server
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public enum ServerMode
    implements Option {

    /**
     * Server mode is enabled.
     */
    ENABLED,

    /**
     * Server mode is disabled.
     */
    @Default
    DISABLED;

    /**
     * Creates a {@link ServerMode} based on the {@link CommandLine} {@link Option}.
     *
     * @return an {@link ServerMode}
     */
    @CommandLine.Prefix("--server")
    @CommandLine.Prefix("-s")
    @CommandLine.Description("Start in Server Mode")
    public static ServerMode enabled() {
        return ServerMode.ENABLED;
    }
}
