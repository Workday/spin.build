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

import build.base.io.PathSet;
import build.base.io.PathSetBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;

/**
 * Shared walk logic for source-file detection tasks. Concrete subclasses declare their own
 * task interface ({@link JavaCompilerPlugin.DetectSourceFiles},
 * {@link JavaCompilerPlugin.DetectGeneratedSourceFiles}, etc.).
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public abstract class AbstractDetectSourceFiles {

    public PathSet detect(final PathSet pathSet) {

        // build the PathSet containing the source files
        final PathSetBuilder builder = PathSetBuilder.create();

        pathSet.stream()
            .filter(Files::exists)
            .map(path -> {
                try {
                    return path.toRealPath();
                }
                catch (final IOException e) {
                    return path;
                }
            })
            .forEach(path -> {
                try {
                    Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(p -> {
                            try {
                                return p.toRealPath();
                            }
                            catch (final IOException e) {
                                return p;
                            }
                        })
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .forEach(builder::add);
                }
                catch (final IOException e) {
                    // TODO: log the exception
                }
            });

        return builder.build();
    }
}
