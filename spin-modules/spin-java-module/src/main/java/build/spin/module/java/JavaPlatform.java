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

import build.base.foundation.Strings;
import build.base.foundation.stream.Streams;
import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.jdk.JDK;
import build.spawn.platform.local.LocalMachine;
import build.spawn.platform.local.jdk.JDKDetector;
import build.spin.Service;
import build.spin.option.OperatingSystem;
import jakarta.inject.Inject;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * A {@link Service} providing access to the available {@link JDK}s.
 *
 * @author brian.oliver
 * @since Nov-2019
 */
public class JavaPlatform
    implements Service {

    /**
     * The available {@link JDK}s ordered by {@link JDKVersion}.
     */
    private final SortedSet<JDK> javaDevelopmentKits;

    /**
     * Whether JDK discovery has been initiated.
     */
    private final AtomicBoolean discovered;

    /**
     * The {@link TelemetryRecorder} for the {@link JavaPlatform}.
     */
    @Inject
    private TelemetryRecorder recorder;

    /**
     * The {@link JavaPlatform.MetaClass} provides access to the {@link JDK} {@link Path}s.
     */
    @Inject
    private JavaPlatform.MetaClass metaClass;

    /**
     * The {@link LocalMachine} on which to launch {@link JDK}s.
     */
    @Inject
    private LocalMachine localMachine;

    /**
     * The {@link OperatingSystem} of the {@link JavaPlatform}.
     */
    @Inject
    private OperatingSystem operatingSystem;

    /**
     * Constructs a {@link JavaPlatform}.
     */
    public JavaPlatform() {
        // store the discovered JDKs in reverse order
        // (this is to ensure the "first" major version is always the latest of that version)
        this.javaDevelopmentKits = new ConcurrentSkipListSet<>(
            (jdk1, jdk2) -> jdk2.version().compareTo(jdk1.version()));

        // discovery of JDKs is deferred until it's actually required
        this.discovered = new AtomicBoolean(false);
    }

    /**
     * Obtains a {@link Stream} of the available {@link JDK}s for the {@link JavaPlatform}.
     *
     * @return a {@link Stream} of {@link JDK}s
     */
    public Stream<JDK> stream() {

        // attempt to detect the Java Development Kits once and only once!
        if (this.discovered.compareAndSet(false, true)) {
            JDKDetector.stream()
                .flatMap(JDKDetector::detect)
                .peek(jdk -> this.recorder.info("Discovered Java Development Kit %s at %s",
                    jdk.version().get(), jdk.home().get()))
                .forEach(this.javaDevelopmentKits::add);
        }

        return this.javaDevelopmentKits.stream();
    }

    /**
     * Obtains the highest available {@link JDK} with the specified major version.
     *
     * @param major the major version
     * @return {@link Optional} {@link JDK}
     */
    public Optional<JDK> getVersion(final int major) {
        return stream()
            .filter(jdk -> jdk.version().major() == major)
            .findFirst();
    }

    /**
     * Obtains the earliest (lowest version) {@link JDK} available.
     *
     * @return {@link Optional} {@link JDK}
     */
    public Optional<JDK> getEarliest() {
        return Streams.reverse(stream())
            .findFirst();
    }

    /**
     * Obtains the latest (highest version) {@link JDK} available.
     *
     * @return {@link Optional} {@link JDK}
     */
    public Optional<JDK> getLatest() {
        return stream().findFirst();
    }

    /**
     * Determines if the specified module name is for a {@link JavaPlatform} module.
     *
     * @param moduleName the module name
     * @return {@code true} if the specified module name is a {@link JavaPlatform} module, {@code false} otherwise
     */
    public static boolean isJavaPlatformModule(final String moduleName) {
        return !Strings.isEmpty(moduleName) && (moduleName.startsWith("java.") || moduleName.startsWith("jdk."));
    }

    /**
     * The {@link Service.MetaClass} for {@link JavaPlatform}.
     */
    public static class MetaClass
        implements Service.MetaClass {

        /**
         * The {@link TelemetryRecorder} for the {@link JavaPlatform.MetaClass}.
         */
        @Inject
        private TelemetryRecorder recorder;

        /**
         * The {@link OperatingSystem} of the {@link JavaPlatform}.
         */
        @Inject
        private OperatingSystem operatingSystem;

        @Override
        public boolean isDetectedIn(final FileSystem fileSystem) {
            final var detected = JDKDetector.stream()
                .flatMap(JDKDetector::paths)
                .findAny()
                .isPresent();

            if (!detected) {
                this.recorder.warn("No Java Development Kits were discovered!");
            }

            return detected;
        }
    }
}
