/*-
 * #%L
 * Spin Java Module Tests
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

import build.spin.Project;
import build.spin.Task;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A build customization compiled, loaded and executed by CustomizationPlugin. The external
 * dependency that exercises the plugin's classpath resolution is declared in module-info.java, not
 * used here -- this task just writes a marker so a test can prove it ran.
 */
public class Build {

    @Named("greet")
    public static class Greet
        implements Task<Void> {

        public void greet(final Project project) throws Exception {
            final Path marker = project.path().resolve(".build").resolve("greeting.txt");
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "hello custom task");
        }
    }
}
