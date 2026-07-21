package build.spin.module.gpg;

/*-
 * #%L
 * Spin GPG Module
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

import build.base.foundation.stream.Streamable;
import build.spin.Project;
import build.spin.Resource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A {@link Resource}, present for a {@link Project} whenever a {@link SigningService} is detected, allowing other
 * {@link build.spin.Plugin}s and {@link Resource}s to include {@link Path}s of artifacts to be GPG-signed, without
 * necessarily performing the signing itself.
 * <p>
 * Signing only occurs on-demand, when the {@code sign} {@link build.spin.Task} (see {@link GpgPlugin.Sign}) is
 * explicitly requested.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
public class SignableResource
    implements Resource {

    /**
     * The {@link Path}s of the artifacts included for signing.
     */
    private final ConcurrentLinkedQueue<Path> artifacts;

    /**
     * Constructs a new {@link SignableResource}.
     */
    public SignableResource() {
        this.artifacts = new ConcurrentLinkedQueue<>();
    }

    /**
     * Includes the specified {@link Path} as an artifact to be GPG-signed, the next time signing occurs.
     *
     * @param artifact the {@link Path} of the artifact to include for signing
     *
     * @return this {@link SignableResource} (to permit fluent-style method invocation)
     */
    public SignableResource include(final Path artifact) {
        this.artifacts.add(Objects.requireNonNull(artifact, "The artifact Path must not be null"));
        return this;
    }

    /**
     * Obtains the {@link Streamable} of {@link Path}s of artifacts included for signing.
     *
     * @return the {@link Streamable} of {@link Path}s
     */
    public Streamable<Path> artifacts() {
        return Streamable.of(this.artifacts);
    }

    /**
     * The {@link Resource.MetaClass} for {@link SignableResource}.
     */
    public static class MetaClass
        implements Resource.MetaClass {

        @Override
        public boolean isDetectedIn(final Project project) {
            return project.engine().services(SigningService.class).findAny().isPresent();
        }
    }
}
