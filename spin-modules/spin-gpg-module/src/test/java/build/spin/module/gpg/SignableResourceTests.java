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

import build.spin.Engine;
import build.spin.Project;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SignableResource}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class SignableResourceTests {

    /**
     * Ensure a {@link Path} included via {@link SignableResource#include(Path)} is later returned by
     * {@link SignableResource#artifacts()}.
     */
    @Test
    void shouldIncludeAndReturnArtifacts() {
        final var resource = new SignableResource();

        final var first = Paths.get("first.jar");
        final var second = Paths.get("second.jar");

        resource.include(first);
        resource.include(second);

        assertThat(resource.artifacts())
            .containsExactly(first, second);
    }

    /**
     * Ensure {@link SignableResource#artifacts()} is empty when nothing has been included.
     */
    @Test
    void shouldReturnNoArtifactsWhenNoneIncluded() {
        final var resource = new SignableResource();

        assertThat(resource.artifacts())
            .isEmpty();
    }

    /**
     * Ensure {@link SignableResource#include(Path)} rejects a {@code null} artifact.
     */
    @Test
    void shouldRejectANullArtifact() {
        final var resource = new SignableResource();

        assertThat(assertThrows(NullPointerException.class, () -> resource.include(null)))
            .hasMessageContaining("artifact");
    }

    /**
     * Ensure the {@link SignableResource.MetaClass} detects a {@link Project} whenever a {@link SigningService}
     * is available.
     */
    @Test
    void shouldDetectWhenSigningServiceIsAvailable() {
        final var engine = mock(Engine.class);
        when(engine.services(SigningService.class))
            .thenReturn(Stream.of(mock(SigningService.class)));

        final var project = mock(Project.class);
        when(project.engine())
            .thenReturn(engine);

        final var metaClass = new SignableResource.MetaClass();

        assertThat(metaClass.isDetectedIn(project))
            .isTrue();
    }

    /**
     * Ensure the {@link SignableResource.MetaClass} doesn't detect a {@link Project} when no {@link SigningService}
     * is available.
     */
    @Test
    void shouldNotDetectWhenSigningServiceIsUnavailable() {
        final var engine = mock(Engine.class);
        when(engine.services(SigningService.class))
            .thenReturn(Stream.empty());

        final var project = mock(Project.class);
        when(project.engine())
            .thenReturn(engine);

        final var metaClass = new SignableResource.MetaClass();

        assertThat(metaClass.isDetectedIn(project))
            .isFalse();
    }
}
