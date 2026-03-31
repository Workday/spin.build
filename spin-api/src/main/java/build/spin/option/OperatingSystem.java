package build.spin.option;

/*-
 * #%L
 * Spin API
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

import build.base.configuration.Default;
import build.base.configuration.Option;
import build.base.foundation.Exceptional;
import build.base.foundation.Strings;

import java.util.Locale;
import java.util.Objects;

/**
 * An {@link Option} representing operating system information.
 *
 * @author brian.oliver
 * @since Feb-2023
 */
public class OperatingSystem
    implements Option {

    /**
     * The kind of {@link OperatingSystem}.
     */
    public enum Kind {
        /**
         * All Microsoft Windows-based Operating Systems.
         */
        WINDOWS,

        /**
         * All Unix-based Operating Systems, including Linux.
         */
        UNIX,

        /**
         * All Posix-based Operating Systems.
         */
        POSIX,

        /**
         * All Apple Mac OS-based Operating Systems.
         */
        MAC,

        /**
         * An unknown / undetected / unsupported operating system.
         */
        UNKNOWN;
    }

    private final Kind kind;

    private final Exceptional<String> name;

    private final Exceptional<String> version;

    /**
     * Constructs an {@link OperatingSystem}.
     *
     * @param kind the {@link Kind}
     * @param name the name
     * @param version the version
     */
    private OperatingSystem(final Kind kind,
                            final Exceptional<String> name,
                            final Exceptional<String> version) {

        this.kind = Objects.requireNonNull(kind, "The kind must not be null");
        this.name = Objects.requireNonNull(name, "The name must not be null");
        this.version = Objects.requireNonNull(version, "The version must not be null");
    }

    /**
     * Obtains the {@link Kind} of the {@link OperatingSystem}.
     *
     * @return the {@link Kind}
     */
    public Kind kind() {
        return this.kind;
    }

    /**
     * Attempts to obtain the actual name of the {@link OperatingSystem} using {@link System#getProperty(String)}
     * {@code "os.name"}.
     *
     * @return an {@link Exceptional} {@link String}
     */
    public Exceptional<String> name() {
        return this.name;
    }

    /**
     * Attempts to obtain the {@link OperatingSystem} version.
     *
     * @return an {@link Exceptional} {@link String}.
     */
    public Exceptional<String> version() {
        return this.version;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final OperatingSystem that = (OperatingSystem) object;
        return this.kind == that.kind && this.name.equals(that.name) && this.version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.kind, this.name, this.version);
    }

    @Override
    public String toString() {
        return this.kind.name() + " ("
            + this.name().map(String::toString).orElse("")
            + (this.name().isPresent() ? " " : "")
            + this.version().map(String::toString).orElse("")
            + ")";
    }

    /**
     * Detects the {@link OperatingSystem} based on the {@code os.name} system property.
     *
     * @return an {@link OperatingSystem}
     */
    @Default
    public static OperatingSystem detect() {

        // determine the kind and name of the OperatingSystem
        Kind kind;
        Exceptional<String> name;
        try {
            var nativeName = Strings.trim(System.getProperty("os.name"));
            if (nativeName == null) {
                kind = Kind.UNKNOWN;
            }
            else {
                nativeName = nativeName.toLowerCase(Locale.ENGLISH);
                if (nativeName.contains("windows")) {
                    kind = Kind.WINDOWS;
                }
                else if (nativeName.contains("linux")
                    || nativeName.contains("mpe/ix")
                    || nativeName.contains("freebsd")
                    || nativeName.contains("irix")
                    || nativeName.contains("digital unix")
                    || nativeName.contains("unix")) {
                    kind = Kind.UNIX;
                }
                else if (nativeName.contains("mac os")) {
                    kind = Kind.MAC;
                }
                else if (nativeName.contains("sun os")
                    || nativeName.contains("sunos")
                    || nativeName.contains("solaris")) {
                    kind = Kind.POSIX;
                }
                else if (nativeName.contains("hp-ux")
                    || nativeName.contains("aix")) {
                    kind = Kind.POSIX;
                }
                else {
                    kind = Kind.UNKNOWN;
                }
            }

            name = Strings.isEmpty(nativeName) ? Exceptional.empty() : Exceptional.of(nativeName);
        }
        catch (final Exception e) {
            kind = Kind.UNKNOWN;
            name = Exceptional.ofException(e);
        }

        // determine the version of the OperatingSystem
        Exceptional<String> version;
        try {
            final var nativeVersion = Strings.trim(System.getProperty("os.version"));
            version = Strings.isEmpty(nativeVersion) ? Exceptional.empty() : Exceptional.of(nativeVersion);
        }
        catch (final Exception e) {
            version = Exceptional.ofException(e);
        }

        return new OperatingSystem(kind, name, version);
    }
}
