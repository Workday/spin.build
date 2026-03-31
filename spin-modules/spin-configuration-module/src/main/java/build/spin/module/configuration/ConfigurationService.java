package build.spin.module.configuration;

/*-
 * #%L
 * Spin Configuration Module
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
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.injection.Binding;
import build.codemodel.injection.Dependency;
import build.codemodel.injection.Resolver;
import build.spin.Extension;
import build.spin.Project;
import build.spin.Service;
import build.spin.annotation.Bootstrap;
import jakarta.inject.Inject;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link Bootstrap} {@link Service} supporting the resolution of {@link Configuration} for
 * other {@link Extension.MetaClass}es and {@link Service}s, <i>before</i> {@link Project}s are discovered.
 *
 * @author brian.oliver
 * @see Configuration
 * @see ConfigurationResource
 * @since Mar-2021
 */
public class ConfigurationService
    implements Service, Resolver<Object> {

    /**
     * The {@link ConfigurationResolver} for the {@link ConfigurationService}.
     */
    private final ConfigurationResolver resolver;

    /**
     * Constructs a {@link ConfigurationService}.
     */
    @Inject
    public ConfigurationService(final FileSystem fileSystem,
                                final TelemetryRecorder telemetryRecorder) {

        Objects.requireNonNull(fileSystem, "The FileSystem must not be null");
        Objects.requireNonNull(telemetryRecorder, "The TelemetryRecorder must not be null");

        // establish the Path to the spin configuration directory in the user.home directory
        final Path path = fileSystem.getPath(System.getProperty("user.home"), Configuration.DIRECTORY);

        // establish a PathSet for Service configuration
        final PathSetBuilder builder = PathSetBuilder.create();
        if (Files.exists(path) && Files.isDirectory(path)) {
            builder.add(path);
        }
        final PathSet pathSet = builder.build();

        // establish the ConfigurationResolver to allow resolution of Configuration for Services and other
        // Extension MetaClasses from the Engine Configuration Path
        this.resolver = new ConfigurationResolver(
            telemetryRecorder,
            pathSet,
            dependency -> {
                // obtain the source location
                final String sourceValue = dependency.typeUsage()
                    .traits(AnnotationTypeUsage.class)
                    .filter(a -> a.typeName().canonicalName().equals(Source.class.getCanonicalName()))
                    .findFirst()
                    .flatMap(a -> a.values().findFirst())
                    .<String>flatMap(v -> v.as(String.class))
                    .orElse(null);

                final String name = sourceValue != null
                    ? sourceValue
                    : ConfigurationResource.definingClass(dependency).getCanonicalName();

                // drop .MetaClass from the name
                final String metaClass = ".MetaClass";
                return name.endsWith(metaClass)
                    ? name.substring(0, name.length() - metaClass.length())
                    : name;
            });
    }

    @Override
    public Optional<? extends Binding<Object>> resolve(final Dependency dependency) {
        return this.resolver.resolve(dependency);
    }

    /**
     * The {@link Service.MetaClass} for {@link ConfigurationService}
     */
    @Bootstrap
    public static class MetaClass
        implements Service.MetaClass {

        @Override
        public boolean isDetectedIn(final FileSystem fileSystem) {
            // the primordial ConfigurationService is always available, regardless of whether the .spin
            // configuration directory is present, to ensure that we will always attempt to resolve (and warn)
            // when (default) configuration is unavailable
            return true;
        }
    }
}
