package build.spin;

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

import build.base.foundation.UniformResource;

import java.net.URI;

/**
 * Creates {@link URI}s for spin {@link Extension}s and other {@link Engine}/{@link Program}
 * telemetry sources, distinguishing them (via a {@code spin-} scheme prefix) from {@link URI}s
 * published by unrelated systems sharing the same process, eg: {@code spawn://jdk-home-detector}.
 *
 * @author brian.oliver
 * @since Aug-2026
 */
public final class SpinURI {

    /**
     * The {@link URI} scheme prefix common to every spin telemetry source.
     */
    private static final String SCHEME_PREFIX = "spin-";

    private SpinURI() {
    }

    /**
     * Creates a {@link URI} for the specified {@code scheme} (eg: {@code "task"}, {@code "plugin"}),
     * automatically qualified with the {@link #SCHEME_PREFIX}, and {@code path}.
     *
     * @param scheme the unqualified scheme, eg: {@code "task"}
     * @param path   the path
     * @return a new {@link URI}
     */
    public static URI create(final String scheme, final String path) {
        return UniformResource.createURI(SCHEME_PREFIX + scheme, path);
    }
}
