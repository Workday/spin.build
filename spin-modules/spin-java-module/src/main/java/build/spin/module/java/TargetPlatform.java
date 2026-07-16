package build.spin.module.java;

/*-
 * #%L
 * Spin Java Module
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

import build.spawn.jdk.Architecture;
import build.spawn.jdk.JDK;

/**
 * The operating system and architecture combination a {@link JDK} is built for, i.e. the target of a
 * {@code jlink} runtime image.
 *
 * @param operatingSystem the {@link build.spawn.jdk.OperatingSystem}
 * @param architecture    the {@link Architecture}
 * @author reed.vonredwitz
 * @since Jul-2026
 */
public record TargetPlatform(build.spawn.jdk.OperatingSystem operatingSystem, Architecture architecture) {

    @Override
    public String toString() {
        return this.operatingSystem.name().toLowerCase(java.util.Locale.ROOT) + "-"
            + this.architecture.name().toLowerCase(java.util.Locale.ROOT);
    }
}
