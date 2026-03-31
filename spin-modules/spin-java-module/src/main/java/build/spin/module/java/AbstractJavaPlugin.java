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

import build.base.telemetry.TelemetryRecorder;
import build.codemodel.injection.Provides;
import build.spawn.jdk.JDK;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleVersioning;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An abstract {@link Plugin} for Java-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public abstract class AbstractJavaPlugin
    implements JavaPlugin {

    @Inject
    protected TelemetryRecorder recorder;

    @Inject
    protected Project project;

    @Inject
    protected ModuleVersioning versioning;

    @Inject
    protected JavaPlatform platform;

    /**
     * The cached {@link ModuleDescriptor} determined for the {@link Project}.
     */
    private final AtomicReference<ModuleDescriptor> moduleDescriptor;

    /**
     * Constructs a {@link AbstractJavaPlugin}.
     */
    public AbstractJavaPlugin() {
        this.moduleDescriptor = new AtomicReference<>();
    }

    /**
     * Obtains the root {@link Path} for source code (including resources) for the {@link JavaPlugin}.
     *
     * @return the root {@link Path}
     */
    protected Path getSourceRootPath() {
        return this.project.path().resolve("src/main/");
    }

    @Override
    @Provides
    public JDK getJDK() {
        return this.platform
            .getVersion(getJavaVersion().major())
            .orElseThrow(() -> {
                final var jdks = this.platform.stream().toList();
                return new RuntimeException("Failed to obtain Java Development Kit for Java " + getJavaVersion().major() + ". Available Java Development Kits: " + jdks);
            });
    }

    @Override
    @Provides
    public ModuleDescriptor getModuleDescriptor() {

        // determine the ModuleDescriptor only once
        return this.moduleDescriptor.updateAndGet(descriptor -> {
            if (descriptor == null) {
                // attempt to locate the module-info.java for the project

                // create a list of places to look for module-info.java (based on priority)
                final List<Path> moduleInfoPaths = new ArrayList<>();

                // include the unversioned Module Info (from the root of the project source)
                final Path unversionedModuleInfoPath = getSourceRootPath()
                    .resolve("java/")
                    .resolve(ModuleDescriptor.SOURCE_FILENAME);

                if (Files.exists(unversionedModuleInfoPath)) {
                    moduleInfoPaths.add(unversionedModuleInfoPath);
                }

                // include the versioned Module Info (from the root of the project source)
                final Path versionedModuleInfoPath = getSourceRootPath()
                    .resolve("java" + getJavaVersion().major() + "/")
                    .resolve(ModuleDescriptor.SOURCE_FILENAME);

                if (Files.exists(versionedModuleInfoPath)) {
                    moduleInfoPaths.add(versionedModuleInfoPath);
                }

                // include the versioned module-info.java to the list (in reverse version order)
                final Path versions = getSourceRootPath()
                    .resolve("resources/META-INF/versions/");
                if (Files.exists(versions)) {
                    try {
                        Files.list(versions)
                            .filter(path -> Files.exists(path.resolve(ModuleDescriptor.SOURCE_FILENAME)))
                            .map(path -> {
                                try {
                                    return Integer.parseInt(path.getFileName().toString());
                                } catch (final NumberFormatException e) {
                                    // ignore directories that aren't numbers
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .sorted(Comparator.reverseOrder())
                            .map(version -> versions.resolve(version.toString())
                                .resolve(ModuleDescriptor.SOURCE_FILENAME))
                            .forEach(moduleInfoPaths::add);
                    } catch (final IOException e) {
                        // we ignore exceptions when we can't read the versions
                    }
                }

                // normalize the project name, so it's a valid module name (just in case we need it)
                final String normalizedProjectName = this.project.name().replace("-", ".");

                // create a ModuleDescriptor using the Module Info
                final ModuleDescriptor.Builder builder = moduleInfoPaths.stream()
                    .findFirst()
                    .map(path -> {
                        try (BufferedReader reader = Files.newBufferedReader(path)) {
                            // the ModuleDescriptor contains the prerequisites
                            return ModuleDescriptor.parse(reader);
                        } catch (final IOException e) {
                            this.recorder
                                .warn(e,
                                    "Failed to read [%s] for [%s]. Defaulting to an empty ModuleDescriptor.",
                                    this.project, this.project.name(), e);

                            return ModuleDescriptor.Builder.create(normalizedProjectName)
                                .noLocation()
                                .setOpen(true)
                                .setAutomatic(true);
                        }
                    })
                    .orElseGet(() -> {
                        this.recorder.warn(
                            "[%s] does not define a ModuleDescriptor. Defaulting to an empty ModuleDescriptor.",
                            this.project.name());

                        return ModuleDescriptor.Builder.create(normalizedProjectName)
                            .noLocation()
                            .setOpen(true)
                            .setAutomatic(true);
                    });

                // attempt to determine the Version of the module
                this.versioning.getVersion(builder.getName())
                    .ifPresent(builder::setVersion);

                return builder.build();
            }

            return descriptor;
        });
    }

    /**
     * Obtains the {@link ModuleDescriptor.Version} for the {@link Project}
     *
     * @return the {@link ModuleDescriptor.Version}
     */
    @Provides
    public ModuleDescriptor.Version getVersion() {
        return this.versioning.getVersion(getModuleDescriptor())
            .orElse(ModuleDescriptor.Version.DEFAULT);
    }
}


