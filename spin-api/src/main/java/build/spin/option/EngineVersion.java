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

import java.util.Optional;

/**
 * The Spin Engine Version.
 *
 * @author brian.oliver
 * @since Jan-2021
 */
public class EngineVersion
    extends AbstractValueOption<String> {

    /**
     * Constructs an {@link EngineVersion}.
     *
     * @param version the version
     */
    private EngineVersion(final String version) {
        super(version);
    }

    /**
     * Auto-detects the {@link EngineVersion} from the jar manifest, falling back to {@code "unknown"}.
     *
     * @return the {@link EngineVersion}
     */
    @Default
    public static EngineVersion autodetect() {
        return Optional.ofNullable(EngineVersion.class.getPackage().getImplementationVersion())
            .map(EngineVersion::of)
            .orElse(of("unknown"));
    }

    /**
     * Create an {@link EngineVersion} using the specified version {@link String}.
     *
     * @param version the version
     * @return an {@link EngineVersion}
     */
    @CommandLine.Prefix("--engine-version")
    @CommandLine.Description("Engine Version")
    public static EngineVersion of(final String version) {
        return new EngineVersion(version);
    }
}
