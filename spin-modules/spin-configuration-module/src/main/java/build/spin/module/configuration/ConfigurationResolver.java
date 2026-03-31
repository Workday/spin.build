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

import build.base.foundation.stream.Streamable;
import build.base.io.PathSet;
import build.base.telemetry.Activity;
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.injection.Binding;
import build.codemodel.injection.Dependency;
import build.codemodel.injection.Resolver;
import build.codemodel.injection.ValueBinding;
import build.codemodel.jdk.TypeUsages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Resolver} of {@link Configuration}s values, to be deserialized from external {@link Path}s using
 * <a href="https://github.com/FasterXML/jackson">Jackson</a>.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class ConfigurationResolver
    implements Resolver<Object> {

    /**
     * The {@link TelemetryRecorder} to output telemetry when {@link Configuration} files are read and parsed.
     */
    private final TelemetryRecorder recorder;

    /**
     * The {@link PathSet} of locations, in order of search, in which to find {@link Configuration}s.
     */
    private final PathSet configurationPaths;

    /**
     * The {@link Function} to produce a suitable {@link Configuration} file name for a {@link Dependency}
     * requiring injection of a {@link Configuration}.
     */
    private final Function<? super Dependency, String> baseFileNameFactory;

    /**
     * A {@link Map} from {@link Configuration} file types (module) to {@link BiFunction}s that produce an
     * {@link Object} of a required {@link Class} from a {@link Configuration} file located at a {@link Path}.
     * <p>
     * Common file types (extensions) are ".properties", ".json", ".xml" and ".yaml"
     * </p>
     */
    private final LinkedHashMap<String, BiFunction<Path, Class<?>, Object>> resolvers;

    /**
     * Constructs a {@link ConfigurationResolver} for the specified {@link FileSystem}.
     *
     * @param recorder            the {@link TelemetryRecorder}
     * @param configurationPaths  the {@link PathSet} defining the {@link Path}s in which to search for
     *                            {@link Configuration} files
     * @param baseFileNameFactory the {@link Function} to produce base {@link Configuration} file names to parse
     */
    public ConfigurationResolver(final TelemetryRecorder recorder,
                                 final PathSet configurationPaths,
                                 final Function<? super Dependency, String> baseFileNameFactory) {

        this.recorder = Objects.requireNonNull(recorder, "The TelemetryRecorder must not be null");
        this.configurationPaths = Objects.requireNonNull(configurationPaths,
            "The Configuration paths (PathSet) must not be null");
        this.baseFileNameFactory = Objects.requireNonNull(baseFileNameFactory,
            "The File Name Factory must not be null");

        // establish the supported types of ObjectMapper-based resolvers
        this.resolvers = new LinkedHashMap<>();

        // include .json support
        this.resolvers.put(".json", (path, requiredClass) -> {
            final ObjectMapper objectMapper = new ObjectMapper();

            try (Activity activity = this.recorder.commence("Reading Configuration File [%s]", path)) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    if (requiredClass.equals(JsonNode.class)) {
                        return objectMapper.readTree(reader);
                    } else {
                        return objectMapper.readValue(reader, requiredClass);
                    }
                } catch (final IOException e) {
                    activity.completeExceptionally(e);
                    return null;
                }
            }
        });

        // include .properties support
        this.resolvers.put(".properties", (path, requiredClass) -> {
            if (requiredClass.equals(Properties.class)) {
                try (Activity activity = this.recorder.commence("Reading Configuration File [%s]", path)) {
                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        final Properties properties = new Properties();
                        properties.load(reader);
                        return properties;
                    } catch (final IOException e) {
                        activity.completeExceptionally(e);
                        return null;
                    }
                }
            } else {
                // TODO: introduce support for reading properties using Jackson
                return null;
            }
        });
    }

    private static Class<?> getRequiredClass(final Dependency dependency) {
        return TypeUsages.getThreadContextClass(dependency.typeUsage()).orElse(null);
    }

    private static Optional<Class<?>> firstTypeArg(final Dependency dep) {
        return TypeUsages.getFirstTypeParameterClass(dep.typeUsage());
    }

    private static <T> Binding<T> bind(final Dependency dep, final T val) {
        return ValueBinding.of(dep, val);
    }

    @Override
    public Optional<? extends Binding<Object>> resolve(final Dependency dependency) {

        // only a @Configuration dependency may be resolved
        final boolean hasConfiguration = dependency.typeUsage()
            .traits(AnnotationTypeUsage.class)
            .anyMatch(a -> a.typeName().canonicalName().equals(Configuration.class.getCanonicalName()));

        if (!hasConfiguration) {
            return Optional.empty();
        }

        // ie: Handle if Optional<T>, Streamable<T>, Iterable<T> was specified
        final Class<?> requiredClass = getRequiredClass(dependency);
        if (requiredClass == null) {
            return Optional.empty();
        }

        if (requiredClass.equals(Optional.class)) {
            // obtain the Optional<T> configuration to be injected
            return firstTypeArg(dependency)
                .map(type -> getValues(dependency, type).stream().findFirst())
                .map(val -> bind(dependency, (Object) val));
        } else if (requiredClass.equals(Hierarchical.class)) {
            // obtain the list of values in the hierarchy
            final List<Object> values = firstTypeArg(dependency)
                .map(type -> getValues(dependency, type).stream())
                .orElse(Stream.empty())
                .collect(Collectors.toList());

            // establish a Hierarchical<T> for the values
            final Hierarchical<Object> hierarchical = new Hierarchical<Object>() {
                @Override
                public boolean isEmpty() {
                    return values.isEmpty();
                }

                @Override
                public Iterator<Object> iterator() {
                    return values.iterator();
                }
            };

            return Optional.of(bind(dependency, hierarchical));
        } else {
            // obtain the T configuration to be injected
            return getValues(dependency, requiredClass).stream()
                .findFirst()
                .map(val -> bind(dependency, val));
        }
    }

    /**
     * Obtains the {@link Object}s of the specified {@link Class} for the provided {@link Dependency},
     * produced by deserializing {@link Configuration} files.
     *
     * @param dependency    the {@link Dependency}
     * @param requiredClass the required {@link Class}
     * @return a {@link Streamable} of {@link Object}s
     */
    private Streamable<Object> getValues(final Dependency dependency,
                                         final Class<?> requiredClass) {

        // obtain the base file name we can use to locate configuration files
        final String baseFileName = this.baseFileNameFactory.apply(dependency);

        return Streamable.of(this.configurationPaths.stream()
            .filter(Files::exists)
            .filter(Files::isDirectory)
            .flatMap(path -> Stream.concat(
                // include a Path for a directory that is the name of the plugin itself (not a Configuration file)
                // (thus allowing a Path to a directory to be injected)
                Stream.of(path.resolve(baseFileName)),

                // include Paths for each type of Configuration files
                Stream.concat(
                    this.resolvers.keySet().stream()
                        .map(extension -> path.resolve(baseFileName + "/").resolve("config" + extension)),
                    this.resolvers.keySet().stream()
                        .map(extension -> path.resolve(baseFileName + extension)))))
            .filter(Files::exists)
            .map(path -> {
                if (requiredClass.equals(Path.class)) {
                    // is only the Path required?
                    return path;
                } else {
                    // attempt to use resolver for each type of file to parse the File
                    final String fileName = path.toString();
                    final String extension = fileName.substring(fileName.lastIndexOf("."));
                    final BiFunction<Path, Class<?>, Object> resolver = this.resolvers.get(extension);
                    return resolver == null ? null : resolver.apply(path, requiredClass);
                }
            })
            .filter(Objects::nonNull));
    }
}
