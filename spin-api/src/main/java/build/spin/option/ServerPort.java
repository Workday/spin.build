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
import build.base.configuration.AbstractValueOption;
import build.base.configuration.Default;
import build.base.configuration.Option;

/**
 * An {@link Option} to specify <i>Server Listening Port</i> that is required by the {@code spin} CLI.
 *
 * @see build.spin.Server
 *
 * @author bin.chen
 * @since Feb-2023
 */
public class ServerPort
    extends AbstractValueOption<Integer> {

    /**
     * Constructs an {@link ServerPort}.
     *
     * @param port the server port
     */
    public ServerPort(final Integer port) {
        super(port);
    }

    /**
     * The text for the command line option.
     */
    public static final String OPTION = "--port";

    /**
     * A default sever listening port.
     */
    public static final ServerPort DEFAULT = of(8686);

    /**
     * Auto-detects a {@link ServerPort} with default port.
     */
    @Default
    public static ServerPort autodetect() {
        return DEFAULT;
    }

    /**
     * Creates a {@link ServerPort} based on the {@link CommandLine} {@link Option}.
     *
     * @return an {@link ServerPort}
     */
    @CommandLine.Prefix(OPTION)
    @CommandLine.Prefix("-p")
    @CommandLine.Description("Server Listening Port")
    public static ServerPort of(final Integer port) {
        return new ServerPort(port);
    }
}
