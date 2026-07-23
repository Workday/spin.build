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

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link ModuleFinder} over a JDK's {@code jmods/} directory, used to make a target JDK's
 * platform module descriptors visible to {@link java.lang.module.Configuration#resolve} without
 * ever opening a {@code .jmod} at runtime — the JDK rejects reads of that format outside of
 * {@code jlink} ("JMOD format not supported at execution time"), so only each module's
 * descriptor is extracted (from the {@code classes/module-info.class} entry every {@code .jmod}
 * carries) and {@link ModuleReference#open()} is never called.
 *
 * <p>This exists because {@link ModuleFinder#ofSystem()} only reflects the modules of the JDK
 * spin itself is currently running on (the <em>host</em>), not an arbitrary <em>target</em>
 * platform's JDK staged for cross-linking — resolving an application against a target's true
 * module graph requires reading that target JDK's own {@code jmods/}.
 */
final class JmodModuleFinder implements ModuleFinder {

    private final Map<String, ModuleReference> modulesByName;

    private JmodModuleFinder(final Map<String, ModuleReference> modulesByName) {
        this.modulesByName = modulesByName;
    }

    /**
     * Builds a {@link ModuleFinder} over every {@code .jmod} file directly inside {@code jmodsDir}.
     *
     * @param jmodsDir the JDK's {@code jmods/} directory
     * @return a {@link ModuleFinder} exposing one {@link ModuleReference} per readable {@code .jmod}
     * @throws IOException if {@code jmodsDir} can't be listed
     */
    static ModuleFinder of(final Path jmodsDir) throws IOException {
        final Map<String, ModuleReference> modulesByName = new LinkedHashMap<>();
        try (var jmods = Files.list(jmodsDir)) {
            for (final var jmod : jmods.filter(p -> p.toString().endsWith(".jmod")).toList()) {
                readDescriptor(jmod).ifPresent(descriptor -> modulesByName.put(descriptor.name(),
                    newReference(descriptor, jmod)));
            }
        }
        return new JmodModuleFinder(modulesByName);
    }

    private static Optional<ModuleDescriptor> readDescriptor(final Path jmod) {
        try (var zipFile = new ZipFile(jmod.toFile())) {
            final ZipEntry entry = zipFile.getEntry("classes/module-info.class");
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream in = zipFile.getInputStream(entry)) {
                return Optional.of(ModuleDescriptor.read(in));
            }
        } catch (final IOException | RuntimeException e) {
            // unreadable / malformed jmod entry -- treat as absent rather than failing the whole finder
            return Optional.empty();
        }
    }

    // resolution only ever needs the descriptor and location, never the module's actual
    // content, since Configuration.resolve() never calls ModuleReader.open() -- the module
    // content is inaccessible anyway ("JMOD format not supported at execution time")
    private static ModuleReference newReference(final ModuleDescriptor descriptor, final Path jmod) {
        return new ModuleReference(descriptor, jmod.toUri()) {
            @Override
            public ModuleReader open() {
                throw new UnsupportedOperationException(
                    "jmod files are a link-time-only format and cannot be opened at execution time: " + jmod);
            }
        };
    }

    @Override
    public Optional<ModuleReference> find(final String name) {
        return Optional.ofNullable(this.modulesByName.get(name));
    }

    @Override
    public Set<ModuleReference> findAll() {
        return Set.copyOf(this.modulesByName.values());
    }
}
