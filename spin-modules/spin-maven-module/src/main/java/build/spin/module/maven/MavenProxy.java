package build.spin.module.maven;

/*-
 * #%L
 * Spin Maven Module
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

import java.util.Optional;

/**
 * An active {@code <proxy>} declared in {@code ~/.m2/settings.xml}.
 */
record MavenProxy(String protocol, String host, int port, Optional<String> username, Optional<String> password,
                   Optional<String> nonProxyHosts) {

    /**
     * Whether requests to the given host should bypass this proxy, per Maven's
     * {@code <nonProxyHosts>} syntax: {@code |}-separated patterns where {@code *} matches any
     * run of characters.
     */
    boolean bypasses(final String targetHost) {
        if (this.nonProxyHosts.isEmpty() || this.nonProxyHosts.get().isBlank()) {
            return false;
        }
        for (final String rawPattern : this.nonProxyHosts.get().split("\\|")) {
            final String pattern = rawPattern.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            final String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
            if (targetHost.matches(regex)) {
                return true;
            }
        }
        return false;
    }
}
