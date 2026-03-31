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
 * An {@link Option} to define when <i>diagnostic</i> telemetry is desired.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public enum Verbose
    implements Option {

    /**
     * Diagnostics output is enabled.
     */
    ENABLED,

    /**
     * Diagnostics output is disabled.
     */
    @Default
    DISABLED;

    /**
     * Creates a {@link Verbose} based on the {@link CommandLine} {@link Option}.
     *
     * @return an {@link Verbose}
     */
    @CommandLine.Prefix("--verbose")
    @CommandLine.Prefix("-v")
    @CommandLine.Description("Diagnostics Telemetry is required")
    public static Verbose enabled() {
        return Verbose.ENABLED;
    }
}
