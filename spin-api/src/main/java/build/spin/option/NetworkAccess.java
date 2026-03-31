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
 * An {@link Option} to define when <i>spin</i> {@link build.spin.Extension}s should not attempt to use
 * the available network interfaces.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public enum NetworkAccess
    implements Option {

    /**
     * Network access is offline and should not be attempted by {@link build.spin.Extension}s.
     */
    OFFLINE,

    /**
     * Network access is online, and may be attempted by {@link build.spin.Extension}s.
     */
    @Default
    ONLINE;

    /**
     * Creates a {@link NetworkAccess} based on the {@link CommandLine} {@link Option}.
     *
     * @return an {@link NetworkAccess}
     */
    @CommandLine.Prefix("--offline")
    @CommandLine.Prefix("-o")
    @CommandLine.Description("Configure Network Access to be Offline")
    public static NetworkAccess offline() {
        return NetworkAccess.OFFLINE;
    }
}
