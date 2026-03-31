package build.spin.common.util;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper methods for working with Folders.
 *
 * @author brian.oliver
 * @since Dec-2022
 */
public class Folders {

    /**
     * Private constructor to prevent instantiation.
     */
    private Folders() {
        // empty constructor
    }

    /**
     * Determines if the specified {@link Path} is to a non-hidden folder.
     *
     * @param path the {@link Path}
     * @return {@code true} if the {@link Path} is to a non-hidden folder, {@code false} otherwise
     */
    public static boolean isFolder(final Path path) {
        try {
            return path != null
                && Files.exists(path)
                && Files.isDirectory(path)
                && !Files.isHidden(path);
        }
        catch (final IOException e) {
            return false;
        }
    }
}
