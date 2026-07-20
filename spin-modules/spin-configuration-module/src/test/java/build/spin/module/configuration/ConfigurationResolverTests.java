package build.spin.module.configuration;

import build.base.flow.SubscriberRegistry;
import build.base.foundation.UniformResource;
import build.base.io.PathSetBuilder;
import build.base.json.JsonValue;
import build.base.telemetry.Commenced;
import build.base.telemetry.Telemetry;
import build.base.telemetry.TelemetryRecorder;
import build.spin.common.telemetry.TelemetryPublisher;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.injection.Context;
import build.spin.common.util.AnnotationValues;
import build.codemodel.injection.InjectionFramework;
import build.codemodel.injection.UnsatisfiedDependencyException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConfigurationResolver}s.
 *
 * @author brian.oliver
 * @since Apr-2021
 */
class ConfigurationResolverTests {

    /**
     * The {@link Path} to the sample configuration files and directories.
     */
    private Path path;

    /**
     * The {@link SubscriberRegistry} for observing {@link Telemetry}.
     */
    private SubscriberRegistry<Telemetry> observers;

    /**
     * The {@link TelemetryRecorder} for a {@link ConfigurationResolver}.
     */
    private TelemetryRecorder recorder;

    /**
     * The {@link ConfigurationResolver} under test.
     */
    private ConfigurationResolver resolver;

    @BeforeEach
    void onBeforeEach()
        throws URISyntaxException {

        // determine the Path in which the sample configuration files reside
        // (based on a known configuration file)
        final var sourcePath = Paths.get(
            this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
        this.path = sourcePath.resolve("configuration/config.properties").getParent();

        // establish the SubscriberRegistry for observing Telemetry
        this.observers = new SubscriberRegistry<>();

        // establish the TelemetryRecorder
        this.recorder = new TelemetryPublisher(
            UniformResource.createURI("class", this.getClass().getCanonicalName()),
            this.observers::publish);
    }

    /**
     * Creates a {@link Context} configured with a {@link ConfigurationResolver}, including the specified {@link Path}s
     * from which to resolve configuration.
     *
     * @param paths the additional {@link Path}s to include for resolving {@link Configuration} files in search order
     * @return a new {@link Context}
     */
    private Context createContext(final Path... paths) {

        // establish the PathSet of directories in which configuration may be present, in search order
        final var builder = PathSetBuilder.create(paths);

        // include the sample path directory
        builder.add(this.path);

        final var pathSet = builder.build();

        // establish the ConfigurationResolver
        this.resolver = new ConfigurationResolver(
            this.recorder,
            pathSet,
            dependency -> {
                final var sourceValue = dependency.typeUsage()
                    .traits(AnnotationTypeUsage.class)
                    .filter(a -> a.typeName().canonicalName().equals(Source.class.getCanonicalName()))
                    .findFirst()
                    .flatMap(a -> AnnotationValues.firstLiteral(a, String.class))
                    .orElse(null);
                return sourceValue != null ? sourceValue : "config";
            });

        // establish the Context for testing the ConfigurationResolver
        return InjectionFramework.create().newContext(this.resolver);
    }

    /**
     * Ensure {@link Path} can be resolved to a file.
     */
    @Test
    void shouldResolvePathToFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config.properties")
            Path path;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.path).isNotNull();
    }

    /**
     * Ensure {@link Optional} {@link Path} can be resolved to a file.
     */
    @Test
    void shouldResolveOptionalPathToFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config.properties")
            Optional<Path> path;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.path).isNotNull();
        assertThat(example.path.isPresent()).isTrue();
    }

    /**
     * Ensure {@link Optional} {@link Path} can be resolved to a missing file.
     */
    @Test
    void shouldResolveOptionalPathToMissingFile() {

        class Example {

            @Inject
            @Configuration
            @Source("this.source.is.missing")
            Optional<Path> path;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.path).isNotNull();
        assertThat(example.path.isPresent()).isFalse();
    }

    /**
     * Ensure {@link Properties} can be resolved from a {@code .properties} file.
     */
    @Test
    void shouldResolvePropertiesFromPropertiesFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config")
            Properties properties;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.properties).isNotNull();
        assertThat(example.properties.get("message")).isEqualTo("hello world");
    }

    /**
     * Ensure {@link Properties} can be resolved from a {@code .properties} file without specifying a {@link Source}.
     */
    @Test
    void shouldResolvePropertiesFromPropertiesFileWithoutASource() {

        class Example {

            @Inject
            @Configuration
            Properties properties;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.properties).isNotNull();
        assertThat(example.properties.get("message")).isEqualTo("hello world");
    }

    /**
     * Ensure an {@link Optional} {@link Properties} can be resolved from a {@code .properties} file.
     */
    @Test
    void shouldOptionallyResolvePropertiesFromPropertiesFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config")
            Optional<Properties> properties;
        }
        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.properties).isNotNull();
        assertThat(example.properties.isPresent()).isTrue();
        assertThat(example.properties.get().get("message")).isEqualTo("hello world");
    }

    /**
     * Ensure {@link Properties} can't be resolved from a missing {@code .properties} file.
     */
    @Test
    void shouldNotResolvePropertiesFromMissingPropertiesFile() {

        class Example {

            @Inject
            @Configuration
            @Source("this.source.is.missing")
            Properties properties;
        }

        Assertions.assertThrows(UnsatisfiedDependencyException.class, () -> createContext().inject(new Example()));
    }

    /**
     * Ensure {@link Optional} {@link Properties} can be resolved as {@link Optional#empty()} when a
     * {@code .properties} file is missing.
     */
    @Test
    void shouldOptionallyResolvePropertiesFromMissingPropertiesFile() {

        class Example {

            @Inject
            @Configuration
            @Source("this.source.is.missing")
            Optional<Properties> properties;
        }
        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.properties).isNotNull();
        assertThat(example.properties.isPresent()).isFalse();
    }

    /**
     * Ensure {@link JsonValue} can be resolved from a {@code .json} file.
     */
    @Test
    void shouldResolveJsonValueFromJsonFile() {

        class Example {

            @Inject
            @Configuration
            JsonValue node;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.node).isNotNull();
        assertThat(example.node.asObject().get("message").asString().value()).isEqualTo("hello world");
    }

    /**
     * Ensure {@link JsonValue} can't be resolved from a malformed {@code .json} file.
     */
    @Test
    void shouldNotResolveJsonValueFromIllegalJsonFile() {

        class Example {

            @Inject
            @Configuration
            @Source("illegal")
            JsonValue node;
        }

        Assertions.assertThrows(UnsatisfiedDependencyException.class, () -> createContext().inject(new Example()));
    }

    /**
     * Ensure the {@link Hierarchical} {@link Path}s for a file can be resolved.
     */
    @Test
    void shouldResolveHierarchicalPathsToFile() {

        class Example {

            @Inject
            @Configuration
            @Source("hierarchical.properties")
            Hierarchical<Path> paths;
        }

        // establish a Context that uses our hierarchical search paths
        final var context = createContext(
            this.path.resolve("level-1/level-2/level-3/level-4"),
            this.path.resolve("level-1/level-2/level-3"),
            this.path.resolve("level-1/level-2"),
            this.path.resolve("level-1"));

        final var example = context.inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.paths.isEmpty()).isFalse();
        assertThat(example.paths.stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected at least one path in Hierarchical<Path> but it was empty")))
            .isEqualTo(this.path.resolve("level-1/level-2/level-3/hierarchical.properties"));
        assertThat(example.paths.stream()
                .skip(1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected at least two paths in Hierarchical<Path> but only one was present")))
            .isEqualTo(this.path.resolve("level-1/hierarchical.properties"));
    }

    /**
     * Ensure the {@link Hierarchical} {@link Path}s for a file can be resolved.
     */
    @Test
    void shouldResolveHierarchicalPropertiesFile() {

        class Example {

            @Inject
            @Configuration
            @Source("hierarchical.properties")
            Hierarchical<Properties> properties;
        }

        // establish a Context that uses our hierarchical search paths
        final var context = createContext(
            this.path.resolve("level-1/level-2/level-3/level-4"),
            this.path.resolve("level-1/level-2/level-3"),
            this.path.resolve("level-1/level-2"),
            this.path.resolve("level-1"));

        final var example = context.inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.properties.isEmpty()).isFalse();
    }

    /**
     * Ensure the {@link Hierarchical} {@link Path}s for a file can be resolved.
     */
    @Test
    void shouldResolveEmptyHierarchicalForMissingFiles() {

        class Example {

            @Inject
            @Configuration
            @Source("this.source.is.missing")
            Hierarchical<Properties> properties;
        }

        // establish a Context that uses our hierarchical search paths
        final var context = createContext(
            this.path.resolve("level-1/level-2/level-3/level-4"),
            this.path.resolve("level-1/level-2/level-3"),
            this.path.resolve("level-1/level-2"),
            this.path.resolve("level-1"));

        final var example = context.inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.properties.isEmpty()).isTrue();
    }

    /**
     * Ensure a {@code "/"}-separated {@link Named} value is navigated as a path through a {@code .json} file.
     */
    @Test
    void shouldResolveNamedNestedValueFromJsonFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/port")
            Optional<Integer> port;

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/verbose")
            Optional<Boolean> verbose;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.port).contains(8080);
        assertThat(example.verbose).contains(true);
    }

    /**
     * Ensure a {@code "/"}-separated {@link Named} value has its separators replaced with {@code "."} and is
     * resolved as a flat key from a {@code .properties} file.
     */
    @Test
    void shouldResolveNamedNestedValueFromPropertiesFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config.properties")
            @Named("server/port")
            Optional<Integer> port;

            @Inject
            @Configuration
            @Source("config.properties")
            @Named("server/verbose")
            Optional<Boolean> verbose;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.port).contains(8080);
        assertThat(example.verbose).contains(true);
    }

    /**
     * Ensure a required (non-{@link Optional}) {@link Named} value can be resolved and coerced.
     */
    @Test
    void shouldResolveRequiredNamedValue() {

        class Example {

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/port")
            Integer port;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.port).isEqualTo(8080);
    }

    /**
     * Ensure a required (non-{@link Optional}) {@link Named} value can be resolved and coerced to a primitive
     * {@code int} and {@code boolean}, from a {@code .json} file.
     */
    @Test
    void shouldResolveRequiredPrimitiveNamedValueFromJsonFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/port")
            int port;

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/verbose")
            boolean verbose;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.port).isEqualTo(8080);
        assertThat(example.verbose).isTrue();
    }

    /**
     * Ensure a required (non-{@link Optional}) {@link Named} value can be resolved and coerced to a primitive
     * {@code int} and {@code boolean}, from a {@code .properties} file.
     */
    @Test
    void shouldResolveRequiredPrimitiveNamedValueFromPropertiesFile() {

        class Example {

            @Inject
            @Configuration
            @Source("config.properties")
            @Named("server/port")
            int port;

            @Inject
            @Configuration
            @Source("config.properties")
            @Named("server/verbose")
            boolean verbose;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.port).isEqualTo(8080);
        assertThat(example.verbose).isTrue();
    }

    /**
     * Ensure an {@link Optional} {@link Named} value resolves to {@link Optional#empty()} when the named value
     * doesn't exist.
     */
    @Test
    void shouldResolveOptionalNamedValueAsEmptyWhenMissing() {

        class Example {

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/missing")
            Optional<String> missing;
        }

        final var example = createContext().inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.missing).isEmpty();
    }

    /**
     * Ensure a required (non-{@link Optional}) {@link Named} value throws when it doesn't exist.
     */
    @Test
    void shouldNotResolveRequiredNamedValueWhenMissing() {

        class Example {

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/missing")
            String missing;
        }

        Assertions.assertThrows(UnsatisfiedDependencyException.class, () -> createContext().inject(new Example()));
    }

    /**
     * Ensure a {@code .json} {@link Configuration} file is only read/parsed once, even when multiple
     * {@code @Named} values (and a whole-file value) are resolved from it, ie: {@link ConfigurationResolver}
     * memoizes previously parsed files.
     */
    @Test
    void shouldOnlyReadJsonFileOnceForMultipleValues() {

        class Example {

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/port")
            Optional<Integer> port;

            @Inject
            @Configuration
            @Source("config.json")
            @Named("server/verbose")
            Optional<Boolean> verbose;

            @Inject
            @Configuration
            @Source("config.json")
            Optional<JsonValue> node;
        }

        final var reads = new AtomicInteger();
        this.observers.subscribe(telemetry -> {
            if (telemetry instanceof Commenced) {
                reads.incrementAndGet();
            }
        });

        final var context = createContext();
        final var example = context.inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.port).contains(8080);
        assertThat(example.verbose).contains(true);
        assertThat(example.node).isPresent();
        assertThat(reads.get()).isEqualTo(1);
    }

    /**
     * Ensure a {@code .properties} {@link Configuration} file is only read/parsed once, even when multiple
     * {@code @Named} values (and a whole-file value) are resolved from it, ie: {@link ConfigurationResolver}
     * memoizes previously parsed files.
     */
    @Test
    void shouldOnlyReadPropertiesFileOnceForMultipleValues() {

        class Example {

            @Inject
            @Configuration
            @Source("config.properties")
            @Named("server/port")
            Optional<Integer> port;

            @Inject
            @Configuration
            @Source("config.properties")
            @Named("server/verbose")
            Optional<Boolean> verbose;

            @Inject
            @Configuration
            @Source("config.properties")
            Optional<Properties> properties;
        }

        final var reads = new AtomicInteger();
        this.observers.subscribe(telemetry -> {
            if (telemetry instanceof Commenced) {
                reads.incrementAndGet();
            }
        });

        final var context = createContext();
        final var example = context.inject(new Example());

        assertThat(example).isNotNull();
        assertThat(example.port).contains(8080);
        assertThat(example.verbose).contains(true);
        assertThat(example.properties).isPresent();
        assertThat(reads.get()).isEqualTo(1);
    }
}
