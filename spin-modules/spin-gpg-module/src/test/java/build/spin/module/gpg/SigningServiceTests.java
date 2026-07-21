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

import build.codemodel.injection.InjectionFramework;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SigningService}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class SigningServiceTests {

    /**
     * The original {@code user.home} system property, to be restored after each test.
     */
    private String originalUserHome;

    /**
     * A temporary directory substituted as the {@code user.home} for the duration of each test.
     */
    private Path tempHome;

    @BeforeEach
    void onBeforeEach()
        throws IOException {

        this.originalUserHome = System.getProperty("user.home");
        this.tempHome = Files.createTempDirectory("spin-gpg-module-tests");
        System.setProperty("user.home", this.tempHome.toString());
    }

    @AfterEach
    void onAfterEach()
        throws IOException {

        System.setProperty("user.home", this.originalUserHome);

        try (var paths = Files.walk(this.tempHome)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException e) {
                    // best-effort cleanup
                }
            });
        }
    }

    /**
     * Ensure the {@link SigningService.MetaClass} detects GPG when a {@code .gnupg} directory exists in the
     * user's home directory.
     */
    @Test
    void shouldDetectWhenGnupgDirectoryExists()
        throws IOException {

        Files.createDirectory(this.tempHome.resolve(SigningService.GNUPG_DIRECTORY));

        final var metaClass = new SigningService.MetaClass();

        assertThat(metaClass.isDetectedIn(FileSystems.getDefault()))
            .isTrue();
    }

    /**
     * Ensure the {@link SigningService.MetaClass} doesn't detect GPG when there's no {@code .gnupg} directory in
     * the user's home directory.
     */
    @Test
    void shouldNotDetectWhenGnupgDirectoryIsAbsent() {
        final var metaClass = new SigningService.MetaClass();

        assertThat(metaClass.isDetectedIn(FileSystems.getDefault()))
            .isFalse();
    }

    /**
     * Ensure {@link SigningService#configurationPath()} resolves to the {@code .gnupg} directory in the user's
     * home directory.
     */
    @Test
    void shouldExposeTheGnupgConfigurationPath() {
        final var context = InjectionFramework.create().newContext();
        context.bind(FileSystem.class).to(FileSystems.getDefault());

        final var service = context.create(SigningService.class);

        assertThat(service.configurationPath())
            .isEqualTo(this.tempHome.resolve(SigningService.GNUPG_DIRECTORY));
    }
}
