package build.spin.common.telemetry;

/*-
 * #%L
 * Spin Common Library
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

import build.base.telemetry.Diagnostic;
import build.base.telemetry.Telemetry;

/**
 * Shared {@link Telemetry} filtering predicates, used consistently everywhere spin decides what
 * {@link Telemetry} to surface by default.
 *
 * @author reed.vonredwitz
 * @since Aug-2026
 */
public final class TelemetryFiltering {

    private TelemetryFiltering() {
    }

    /**
     * Determines whether the given {@link Telemetry} is a {@link Diagnostic} sourced from spawn (e.g. the
     * launch-command dump emitted for every {@code Application} spawn launches) — internal implementation
     * detail that's noise by default, but should still be visible when verbose output is requested.
     *
     * @param telemetry the {@link Telemetry}
     * @return {@code true} if the {@link Telemetry} is a spawn-sourced {@link Diagnostic}
     */
    public static boolean isSpawnLaunchDiagnostic(final Telemetry telemetry) {
        return telemetry instanceof Diagnostic diagnostic && "spawn".equals(diagnostic.uri().getScheme());
    }
}
