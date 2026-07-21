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

import build.spin.Service;
import jakarta.inject.Inject;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link Service} indicating that GPG signing is available on the current machine, detected by the presence of
 * a {@code .gnupg} directory in the user's home directory.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
public class SigningService
    implements Service {

    /**
     * The name of the GnuPG home directory, typically located in the user's home directory.
     */
    public static final String GNUPG_DIRECTORY = ".gnupg";

    /**
     * The {@link Path} to the detected GnuPG configuration directory (passed to {@code gpg} as {@code --homedir}).
     */
    private final Path configurationPath;

    /**
     * Constructs a {@link SigningService}.
     *
     * @param fileSystem the {@link FileSystem}
     */
    @Inject
    private SigningService(final FileSystem fileSystem) {
        Objects.requireNonNull(fileSystem, "The FileSystem must not be null");
        this.configurationPath = fileSystem.getPath(System.getProperty("user.home"), GNUPG_DIRECTORY);
    }

    /**
     * Obtains the {@link Path} to the detected GnuPG configuration directory (passed to {@code gpg} as
     * {@code --homedir}).
     *
     * @return the {@link Path} to the GnuPG configuration directory
     */
    public Path configurationPath() {
        return this.configurationPath;
    }

    /**
     * The {@link Service.MetaClass} for {@link SigningService}.
     */
    public static class MetaClass
        implements Service.MetaClass {

        @Override
        public boolean isDetectedIn(final FileSystem fileSystem) {
            // obtain the user home (in which to detect a .gnupg directory)
            final var home = fileSystem.getPath(System.getProperty("user.home"));

            return Files.exists(home.resolve(GNUPG_DIRECTORY));
        }
    }
}
