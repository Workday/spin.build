package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
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

import build.base.foundation.Exceptional;
import build.base.foundation.Strings;
import build.base.io.LookaheadReader;
import build.base.parsing.Evaluator;
import build.base.parsing.Filter;
import build.base.parsing.ParseException;
import build.base.parsing.Scanner;
import build.spin.Project;
import build.spin.Task;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specifies information identifying an artifact required by a {@link Project}.
 * <p>
 * An {@link Artifact} identifies a file of some type, required by or produced by a {@link Project} that is possibly
 * made available to or published to an Apache Maven-based repository.
 * <p>
 * Each {@link Artifact} specifies:
 * <ol>
 *     <li>a {@code groupId} which is usually the name of a company or group of products,
 *          often as a reverse domain name (eg: com.workday)</li>
 *     <li>an {@code artifactId} which is usually the name of the artifact (eg: quark)</li>
 *     <li>a {@code version}</li>
 *     <li>a {@code type}, which is usually the file name module</li>
 * </ol>
 * <p>
 * {@link Artifact}s may optionally define a {@code classifier}, which usually signifies environmental constraints
 * (eg: windows or linux operating specific artifact, but these are {@link Optional}).
 *
 * @author brian.oliver
 * @since Sep-2019
 */
public interface Artifact {

    /**
     * Obtains the group for the {@link Artifact}.
     *
     * @return the group for the {@link Artifact}.
     */
    String groupId();

    /**
     * Obtains the name for the {@link Artifact}.
     *
     * @return the name for the {@link Artifact}.
     */
    String artifactId();

    /**
     * Obtains the {@link Version} for the {@link Artifact}.
     *
     * @return the {@link Version} for the {@link Artifact}.
     */
    Version version();

    /**
     * Obtains the type for the {@link Artifact}.
     *
     * @return the type for the {@link Artifact}.
     */
    String type();

    /**
     * Obtains the {@link Optional} classifier for the {@link Artifact}.
     *
     * @return the {@link Optional} classifier for the {@link Artifact}.
     */
    Optional<String> classifier();

    /**
     * Creates an {@link Artifact}.
     *
     * @param groupId the groupId for the {@link Artifact}
     * @param artifactId the artifactId for the {@link Artifact}
     * @param version the version for the {@link Artifact}
     * @param type the type for the {@link Artifact}
     * @param classifier the optional classifier for the {@link Artifact}
     *
     * @return a new {@link Artifact}
     */
    static Artifact create(final String groupId,
                           final String artifactId,
                           final String version,
                           final String type,
                           final String classifier) {

        return new Artifact.Implementation(groupId, artifactId, version, type, classifier);
    }

    /**
     * Creates an {@link Artifact} without a classifier.
     *
     * @param groupId the groupId for the {@link Artifact}
     * @param artifactId the artifactId for the {@link Artifact}
     * @param version the version for the {@link Artifact}
     * @param type the type for the {@link Artifact}
     *
     * @return a new {@link Artifact}
     */
    static Artifact create(final String groupId,
                           final String artifactId,
                           final String version,
                           final String type) {

        return create(groupId, artifactId, version, type, null);
    }

    /**
     * Creates an {@link Artifact} based on the specified Apache Maven Coordinates, which are in the following format
     * {@code <groupId>:<artifactId>[:<module>[:<classifier>]]:<version>}.
     *
     * @param coordinates the coordinates
     *
     * @return a new {@link Artifact}
     * @throws ParseException if the specified coordinates are in an unknown format
     */
    static Artifact parse(final String coordinates) {

        // the Apache Maven Coordinates Pattern
        final Pattern PATTERN = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");

        final Matcher matcher = PATTERN.matcher(coordinates);

        if (!matcher.matches()) {
            throw new ParseException(LookaheadReader.Location.START,
                "<groupId>:<artifactId>[:<module>[:<classifier>]]:<version>",
                coordinates);
        }

        final String groupId = matcher.group(1);
        final String artifactId = matcher.group(2);
        final String extension = Strings.isEmpty(matcher.group(4)) ? "jar" : matcher.group(4);
        final String classifier = matcher.group(6);
        final String version = matcher.group(7);

        return create(groupId, artifactId, version, extension, classifier);
    }

    /**
     * An immutable version of an {@link Artifact}, defined according to the
     * <a href="https://cwiki.apache.org/confluence/display/MAVENOLD/Versioning">Maven Version</a> specification.
     */
    final class Version
        implements Comparable<Version> {

        /**
         * The {@link Pattern} for a valid version {@link String}.
         */
        public static final Pattern PATTERN = Pattern.compile("\\w+((\\.|-)\\w+)*");

        /**
         * The {@link Evaluator} for a {@link Version}.
         */
        public static final Evaluator<Version> EVALUATOR = Evaluator.create(PATTERN, Version::parse, "Maven Version");

        /**
         * The raw version {@link String}.
         */
        private final String rawVersion;

        /**
         * The {@link Element}s defined by the {@link Version}.
         */
        private final ElementList elements;

        /**
         * Constructs a {@link Version} given a raw version {@link String}.
         *
         * @param rawVersion the raw version
         */
        private Version(final String rawVersion) {
            Objects.requireNonNull(rawVersion, "The version must not be null");

            // validate the version format
            if (PATTERN.matcher(rawVersion).matches()) {
                this.rawVersion = rawVersion;
            }
            else {
                throw new IllegalArgumentException("The specified raw version [" + rawVersion + "] is invalid");
            }

            // attempt to parse the version into Elements that can be used for comparison
            this.elements = new ElementList();
            final String version = rawVersion.toLowerCase(Locale.ENGLISH);

            ElementList list = this.elements;

            final Deque<Element> stack = new ArrayDeque<>();
            stack.push(list);

            boolean isDigit = false;

            int startIndex = 0;

            for (int i = 0; i < version.length(); i++) {
                final char c = version.charAt(i);

                if (c == '.') {
                    if (i == startIndex) {
                        list.add(NumericElement.ZERO);
                    }
                    else {
                        list.add(parseElement(isDigit, version.substring(startIndex, i)));
                    }
                    startIndex = i + 1;
                }
                else if (c == '-') {
                    if (i == startIndex) {
                        list.add(NumericElement.ZERO);
                    }
                    else {
                        list.add(parseElement(isDigit, version.substring(startIndex, i)));
                    }
                    startIndex = i + 1;

                    list.add(list = new ElementList());
                    stack.push(list);
                }
                else if (Character.isDigit(c)) {
                    if (!isDigit && i > startIndex) {
                        list.add(new StringElement(version.substring(startIndex, i), true));
                        startIndex = i;

                        list.add(list = new ElementList());
                        stack.push(list);
                    }

                    isDigit = true;
                }
                else {
                    if (isDigit && i > startIndex) {
                        list.add(parseElement(true, version.substring(startIndex, i)));
                        startIndex = i;

                        list.add(list = new ElementList());
                        stack.push(list);
                    }

                    isDigit = false;
                }
            }

            if (version.length() > startIndex) {
                list.add(parseElement(isDigit, version.substring(startIndex)));
            }

            while (!stack.isEmpty()) {
                list = (ElementList) stack.pop();
                list.sanitize();
            }
        }

        /**
         * Obtains the version {@link String}.
         *
         * @return the version {@link String}.
         */
        public String get() {
            return this.rawVersion;
        }

        @Override
        public int compareTo(final Version version) {
            return this.elements.compareTo(version.elements);
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final Version other = (Version) object;
            return this.elements.compareTo(other.elements) == 0;
        }

        @Override
        public int hashCode() {
            return this.elements.hashCode();
        }

        @Override
        public String toString() {
            return this.rawVersion;
        }

        /**
         * Creates a {@link Version} by parsing a raw version {@link String}.
         *
         * @param rawVersion the raw version
         * @return the {@link Version}
         */
        public static Version parse(final String rawVersion) {
            return new Version(rawVersion);
        }

        /**
         * Parses a {@link String} into an {@link Element}.
         *
         * @param isDigit
         * @param string the {@link String}
         * @return an {@link Element}
         */
        private static Element parseElement(final boolean isDigit, final String string) {
            if (isDigit) {
                return new NumericElement(stripLeadingZeroes(string));
            }
            return new StringElement(string, false);
        }

        /**
         * Returns the input {@link String} without any leading {@code 0}s that may be present.
         *
         * @param string the input {@link String}
         * @return the {@link String} without leading {@code 0}s
         */
        private static String stripLeadingZeroes(final String string) {
            if (Strings.isEmpty(string)) {
                return "0";
            }
            for (int i = 0; i < string.length(); ++i) {
                final char c = string.charAt(i);
                if (c != '0') {
                    return string.substring(i);
                }
            }
            return string;
        }

        /**
         * An element of a {@link Version}.
         */
        private interface Element
            extends Comparable<Element> {

            /**
             * Determines if the {@link Element} is zero (for comparison purposes).
             *
             * @return {@code true} if the {@link Element} is considered zero, {@code false} otherwise
             */
            boolean isZero();
        }

        /**
         * A numeric {@link Element} in a {@link Version}.
         */
        private static class NumericElement
            implements Element {

            /**
             * A constant representation of a zero {@link NumericElement}.
             */
            private final static NumericElement ZERO = new NumericElement(0);

            /**
             * The value of the {@link Element}.
             */
            private final BigInteger value;

            /**
             * Constructs a {@link NumericElement} given the specified positive value.
             *
             * @param value the numeric value
             */
            NumericElement(final int value) {
                this.value = BigInteger.valueOf(value);
            }

            /**
             * Constructs a {@link NumericElement} given the specified {@link String}.
             *
             * @param string the numeric value
             */
            NumericElement(final String string) {
                this.value = new BigInteger(string);
            }

            @Override
            public boolean isZero() {
                return this.value.equals(BigInteger.ZERO);
            }

            @Override
            public int compareTo(final Element element) {
                if (element == null) {
                    // 1.0 == 1, 1.1 > 1
                    return this.value.equals(BigInteger.ZERO) ? 0 : 1;
                }
                else if (element instanceof StringElement) {
                    // 1.1 > 1-sp
                    return 1;
                }
                else if (element instanceof ElementList) {
                    // 1.1 > 1-1
                    return 1;
                }
                else if (element instanceof NumericElement) {
                    return this.value.compareTo(((NumericElement) element).value);
                }
                else {
                    throw new IllegalStateException("Invalid Element: " + element.getClass());
                }
            }

            @Override
            public boolean equals(final Object object) {
                if (this == object) {
                    return true;
                }
                if (object == null || getClass() != object.getClass()) {
                    return false;
                }
                final NumericElement that = (NumericElement) object;
                return this.value.equals(that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.value);
            }

            @Override
            public String toString() {
                return this.value.toString();
            }
        }

        /**
         * A {@link String}-based {@link Element} in a {@link Version}, usually a qualifier of some kind.
         */
        private static class StringElement
            implements Element {

            /**
             * The standard qualifiers, in order of precedence, from lowest to highest.
             */
            private static final List<String> QUALIFIERS =
                Arrays.asList("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");

            /**
             * The standard aliases for qualifiers.
             */
            private static final Properties ALIASES = new Properties();

            static {
                ALIASES.put("ga", "");
                ALIASES.put("final", "");
                ALIASES.put("release", "");
                ALIASES.put("cr", "rc");
            }

            /**
             * A numerical value of a release version, so that it can be compared with other qualifiers.
             */
            private static final String RELEASE_VERSION_INDEX = String.valueOf(QUALIFIERS.indexOf(""));

            /**
             * The canonical {@link String} value.
             */
            private final String value;

            /**
             * Constructs a {@link StringElement} given a {@link String} value.
             *
             * @param value the value
             * @param followedByDigit if the is followed by a digit
             */
            StringElement(final String value, final boolean followedByDigit) {
                if (followedByDigit && value.length() == 1) {
                    // a1 = alpha-1, b1 = beta-1, m1 = milestone-1
                    switch (value.charAt(0)) {
                    case 'a':
                        this.value = "alpha";
                        break;
                    case 'b':
                        this.value = "beta";
                        break;
                    case 'm':
                        this.value = "milestone";
                        break;
                    default:
                        this.value = ALIASES.getProperty(value, value);
                    }
                }
                else {
                    this.value = ALIASES.getProperty(value, value);
                }
            }

            @Override
            public boolean isZero() {
                return comparable(this.value).compareTo(RELEASE_VERSION_INDEX) == 0;
            }

            /**
             * Obtains {@link String} that can be used for version comparison, taking into account the ordering of
             * known qualifiers then unknown qualifiers with lexical ordering.
             *
             * @param string the {@link String} to be compared
             * @return an value that can be used with lexical comparison
             */
            public static String comparable(final String string) {
                final int index = QUALIFIERS.indexOf(string);

                return index == -1 ? (QUALIFIERS.size() + "-" + string) : String.valueOf(index);
            }

            @Override
            public int compareTo(final Element element) {

                if (element == null) {
                    // 1-rc < 1, 1-ga > 1
                    return comparable(this.value).compareTo(RELEASE_VERSION_INDEX);
                }
                else if (element instanceof NumericElement) {
                    // 1.any < 1.1
                    return -1;
                }
                else if (element instanceof ElementList) {
                    // 1.any < 1-1
                    return -1;
                }
                else if (element instanceof StringElement) {
                    return comparable(this.value).compareTo(comparable(((StringElement) element).value));

                }
                else {
                    throw new IllegalStateException("Invalid Element: " + element.getClass());
                }
            }

            @Override
            public boolean equals(final Object object) {
                if (this == object) {
                    return true;
                }
                if (object == null || getClass() != object.getClass()) {
                    return false;
                }
                final StringElement that = (StringElement) object;
                return this.value.equals(that.value);
            }

            @Override
            public int hashCode() {
                return this.value.hashCode();
            }

            @Override
            public String toString() {
                return this.value;
            }
        }

        /**
         * A list of {@link Element}s, that is also an {@link Element}, thus allowing representation of sub-lists.
         */
        private static class ElementList
            extends ArrayList<Element>
            implements Element {

            @Override
            public boolean isZero() {
                return size() == 0;
            }

            @Override
            public int compareTo(final Element element) {
                if (element == null) {
                    if (size() == 0) {
                        // 1-0 = 1- (normalize) = 1
                        return 0;
                    }

                    // Compare the entire list of Elements with null - not just the first one, MNG-6964
                    for (Element e : this) {
                        final int result = e.compareTo(null);
                        if (result != 0) {
                            return result;
                        }
                    }
                    return 0;
                }

                if (element instanceof NumericElement) {
                    // 1-1 < 1.0.x
                    return -1;
                }
                else if (element instanceof StringElement) {
                    // 1-1 > 1-sp
                    return 1;
                }
                else if (element instanceof ElementList) {
                    final Iterator<Element> left = iterator();
                    final Iterator<Element> right = ((ElementList) element).iterator();

                    while (left.hasNext() || right.hasNext()) {
                        final Element l = left.hasNext() ? left.next() : null;
                        final Element r = right.hasNext() ? right.next() : null;

                        // if this is shorter, then invert the compare and mul with -1
                        final int result = l == null ? (r == null ? 0 : -1 * r.compareTo(l)) : l.compareTo(r);

                        if (result != 0) {
                            return result;
                        }
                    }

                    return 0;
                }
                else {
                    throw new IllegalStateException("Invalid Element: " + element.getClass());
                }
            }

            @Override
            public String toString() {
                final StringBuilder buffer = new StringBuilder();
                for (final Element element : this) {
                    if (buffer.length() > 0) {
                        buffer.append((element instanceof ElementList) ? '-' : '.');
                    }
                    buffer.append(element);
                }
                return buffer.toString();
            }

            /**
             * Removes {@link #isZero()} based {@link Element}s from the end of the {@link ElementList}.
             */
            private void sanitize() {
                for (int i = size() - 1; i >= 0; i--) {
                    final Element element = get(i);

                    if (element.isZero()) {
                        // remove null trailing items: 0, "", empty list
                        remove(i);
                    }
                    else if (!(element instanceof ElementList)) {
                        break;
                    }
                }
            }
        }

        /**
         * Defines a <i>bound</i> for a {@link Version}.
         */
        public static class Bound
            implements Predicate<Version> {

            /**
             * The constraint for the {@link Version} in the {@link Bound}.
             */
            public enum Constraint {
                BEFORE,
                AFTER
            }

            /**
             * Defines if a {@link Constraint} is inclusive of the {@link Version} or must be strictly
             * {@link Constraint#BEFORE} or {@link Constraint#AFTER} the {@link Version}.
             */
            public enum Inclusivity {
                /**
                 * The {@link Bound} includes the {@link Version}.
                 */
                INCLUSIVELY,

                /**
                 * The {@link Bound} <i>does not</i> include the {@link Version}.
                 */
                STRICTLY
            }

            /**
             * The {@link Version} for the {@link Bound}.
             */
            private final Version version;

            /**
             * The {@link Constraint} for the {@link Version} in the {@link Bound}.
             */
            private final Constraint constraint;

            /**
             * The {@link Inclusivity} of the {@link Constraint}.
             */
            private final Inclusivity inclusivity;

            /**
             * Constructs a {@link Bound} for the specified {@link Version}.
             *
             * @param version the {@link Version}
             * @param inclusivity the {@link Inclusivity}
             * @param constraint the {@link Constraint}
             */
            private Bound(final Inclusivity inclusivity,
                          final Constraint constraint,
                          final Version version) {

                this.version = Objects.requireNonNull(version, "The Version must not be null");
                this.inclusivity = Objects.requireNonNull(inclusivity, "The Inclusivity must not be null");
                this.constraint = Objects.requireNonNull(constraint, "The Constraint must not be null");
            }

            /**
             * Obtains the {@link Version} of the {@link Bound}.
             *
             * @return the {@link Version}
             */
            public Version getVersion() {
                return this.version;
            }

            /**
             * Obtains the {@link Constraint} for the {@link Bound}.
             *
             * @return the {@link Constraint}
             */
            public Constraint getConstraint() {
                return this.constraint;
            }

            /**
             * Obtains the {@link Inclusivity} of the {@link Bound}.
             *
             * @return the {@link Inclusivity}
             */
            public Inclusivity getInclusivity() {
                return this.inclusivity;
            }

            /**
             * Determines if the {@link Bound} {@link Inclusivity} is {@link Inclusivity#INCLUSIVELY}.
             *
             * @return {@code true} if the {@link Bound} is {@link Inclusivity#INCLUSIVELY}, {@code false} otherwise
             */
            public boolean isInclusive() {
                return this.inclusivity == Inclusivity.INCLUSIVELY;
            }

            @Override
            public boolean test(final Version version) {
                if (version == null) {
                    return false;
                }

                final int comparison = version.compareTo(this.version);

                return this.constraint == Constraint.BEFORE
                    ? (this.inclusivity == Inclusivity.INCLUSIVELY ? comparison <= 0 : comparison < 0)
                    : (this.inclusivity == Inclusivity.INCLUSIVELY ? comparison >= 0 : comparison > 0);
            }

            @Override
            public boolean equals(final Object object) {
                if (this == object) {
                    return true;
                }
                if (object == null || getClass() != object.getClass()) {
                    return false;
                }
                final Bound bound = (Bound) object;
                return this.version.equals(bound.version)
                    && this.constraint == bound.constraint
                    && this.inclusivity == bound.inclusivity;
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.version, this.constraint, this.inclusivity);
            }

            @Override
            public String toString() {
                return this.constraint == Constraint.AFTER
                    ? (this.inclusivity == Inclusivity.INCLUSIVELY ? "[" : "(") + this.version
                    : this.version + (this.inclusivity == Inclusivity.INCLUSIVELY ? "]" : ")");
            }

            /**
             * Creates a {@link Bound} for the specified {@link Version}.
             *
             * @param inclusivity the {@link Inclusivity}
             * @param constraint the {@link Constraint}
             * @param version the {@link Version}
             * @return a new {@link Bound}
             */
            public static Bound of(final Inclusivity inclusivity,
                                   final Constraint constraint,
                                   final Version version) {

                return new Bound(inclusivity, constraint, version);
            }

            /**
             * Creates a {@link Bound} for the specified {@link Version} {@link String}.
             *
             * @param inclusivity the {@link Inclusivity}
             * @param constraint the {@link Constraint}
             * @param version the {@link Version}
             * @return a new {@link Bound}
             */
            public static Bound of(final Inclusivity inclusivity,
                                   final Constraint constraint,
                                   final String version) {

                return of(inclusivity, constraint, Version.parse(Objects.requireNonNull(version)));
            }
        }

        /**
         * Defines a <i>Range</i> of {@link Artifact.Version}s, conforming to the semantics defined by the
         * <a href="https://cwiki.apache.org/confluence/display/MAVENOLD/Dependency+Mediation+and+Conflict+Resolution#DependencyMediationandConflictResolution-DependencyVersionRanges">Maven Dependency Version Ranges</a>.
         */
        public static class Range
            implements Predicate<Version> {

            /**
             * The lower {@link Artifact.Version.Bound}.
             */
            private final Artifact.Version.Bound lower;

            /**
             * The upper {@link Artifact.Version.Bound}.
             */
            private final Artifact.Version.Bound upper;

            /**
             * Constructs a {@link Range} given an upper and lower {@link Artifact.Version.Bound}
             *
             * @param lower the lower {@link Artifact.Version.Bound}
             * @param upper the upper {@link Artifact.Version.Bound}
             */
            private Range(final Artifact.Version.Bound lower, final Artifact.Version.Bound upper) {
                this.lower = lower;
                this.upper = upper;
            }

            /**
             * Obtains the lower {@link Artifact.Version.Bound} of the {@link Artifact.Version.Range}.
             *
             * @return the lower {@link Artifact.Version.Bound}
             */
            public Artifact.Version.Bound getLowerBound() {
                return this.lower;
            }

            /**
             * Obtains the upper {@link Artifact.Version.Bound} of the {@link Artifact.Version.Range}.
             *
             * @return the upper {@link Artifact.Version.Bound}
             */
            public Artifact.Version.Bound getUpperBound() {
                return this.upper;
            }

            /**
             * Determines if the specified {@link Version} is in the {@link Range}.
             *
             * @param version the {@link Version}
             * @return {@code true} if the {@link Version} is in the {@link Range}, {@code false} otherwise
             */
            public boolean contains(final Version version) {
                return test(version);
            }

            @Override
            public boolean test(final Version version) {

                if (this.lower != null && this.lower.getConstraint() == Bound.Constraint.BEFORE && this.upper == null) {
                    return this.lower.test(version);
                }

                if (this.upper != null && this.upper.getConstraint() == Bound.Constraint.AFTER && this.lower == null) {
                    return this.upper.test(version);
                }

                if (this.lower != null && this.lower.getConstraint() == Bound.Constraint.BEFORE &&
                    this.upper != null && this.upper.getConstraint() == Bound.Constraint.AFTER) {
                    return this.lower.test(version) || this.upper.test(version);
                }

                return (this.lower == null || this.lower.test(version))
                    && (this.upper == null || this.upper.test(version));
            }

            @Override
            public boolean equals(final Object object) {
                if (this == object) {
                    return true;
                }
                if (object == null || getClass() != object.getClass()) {
                    return false;
                }
                final Range range = (Range) object;
                return Objects.equals(this.lower, range.lower) && Objects.equals(this.upper, range.upper);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.lower, this.upper);
            }

            // TODO: implement toString

            /**
             * Creates a {@link Range} limited to the specified {@link Version}.
             *
             * @param version the {@link Version}
             * @return a new {@link Range}
             */
            public static Range of(final Version version) {
                Objects.requireNonNull(version, "The Version must not be null");

                return new Range(
                    Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.AFTER, version),
                    Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.BEFORE, version));
            }

            /**
             * Parses a {@link String} into a {@link Range} according to the following.
             *
             * <ul>
             *     <li>{@code (,1.0]} means version &lt;= 1.0</li>
             *     <li>{@code 1.0} means version == 1.0</li>
             *     <li>{@code [1.0]} means version == 1.0</li>
             *     <li>{@code [1.2,1.3]} means 1.2 &lt;= version &lt;= 1.3</li>
             *     <li>{@code [1.0,2.0)} means 1.0 &lt;= version &lt; 2.0</li>
             *     <li>{@code [1.5,)} means version &gt;= 1.5</li>
             *     <li>{@code (,1.0],[1.2,)} means version &lt;= 1.0 or version &gt;= 1.2</li>
             *     <li>{@code (,1.1),(1.1,)} means version &lt; 1.1 or version &gt; 1.1</li>
             * </ul>
             *
             * @param string the {@link String} to parse
             * @return the {@link Range}
             */
            public static Range parse(final String string) {

                Bound lower = null;
                Bound upper = null;

                if (Strings.isEmpty(string)) {
                    throw new ParseException(LookaheadReader.Location.START, "An Artifact.Version.Range",
                        "An empty string");
                }

                final Scanner scanner = new Scanner(Strings.trim(string));
                scanner.register(Filter.WHITESPACE);
                scanner.register(Version.class, Version.EVALUATOR);

                final Optional<Version> version = scanner.optionallyConsume(Version.class);

                if (version.isPresent()) {
                    lower = Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.AFTER, version.get());
                    upper = Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.BEFORE, version.get());
                }
                else if (scanner.optionallyConsume("[").isPresent()) {

                    lower = Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.AFTER,
                        scanner.consume(Version.class));

                    if (scanner.optionallyConsume("]").isPresent()) {
                        upper = Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.BEFORE, lower.getVersion());
                    }
                    else if (scanner.optionallyConsume(",").isPresent()) {
                        if (!scanner.optionallyConsume(")").isPresent()) {
                            final Version upperVersion = scanner.consume(Version.class);

                            if (scanner.optionallyConsume("]").isPresent()) {
                                upper = Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.BEFORE, upperVersion);
                            }
                            else if (scanner.optionallyConsume(")").isPresent()) {
                                upper = Bound.of(Bound.Inclusivity.STRICTLY, Bound.Constraint.BEFORE, upperVersion);
                            }
                            else {
                                throw new IllegalArgumentException("Missing ] or ) after Range upper bound");
                            }
                        }
                    }
                    else {
                        throw new IllegalArgumentException("Missing ] or ,) after Range lower bound");
                    }
                }
                else if (scanner.optionallyConsume("(,").isPresent()) {
                    final Version lowerVersion = scanner.consume(Version.class);

                    final Bound bound;
                    if (scanner.optionallyConsume("]").isPresent()) {
                        bound = Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.BEFORE, lowerVersion);
                    }
                    else if (scanner.optionallyConsume(")").isPresent()) {
                        bound = Bound.of(Bound.Inclusivity.STRICTLY, Bound.Constraint.BEFORE, lowerVersion);
                    }
                    else {
                        throw new IllegalArgumentException("Missing ] or ) after Range upper bound");
                    }

                    if (bound.isInclusive()) {
                        upper = bound;
                    }

                    if (scanner.optionallyConsume(",").isPresent()) {
                        lower = bound;

                        if (scanner.optionallyConsume("[").isPresent()) {
                            upper = Bound.of(Bound.Inclusivity.INCLUSIVELY, Bound.Constraint.AFTER,
                                scanner.consume(Version.class));
                        }
                        else if (scanner.optionallyConsume("(").isPresent()) {
                            upper = Bound.of(Bound.Inclusivity.STRICTLY, Bound.Constraint.AFTER,
                                scanner.consume(Version.class));
                        }
                        else {
                            throw new IllegalArgumentException("Missing [ or ( before Range upper bound");
                        }

                        scanner.consume(",)");
                    }
                }
                else {
                    throw new IllegalArgumentException("Invalid Range specified");
                }

                // ensure nothing else follows
                if (scanner.hasNext()) {
                    throw new ParseException(scanner.getLocation(), "No further input", "additional input");
                }

                return new Range(lower, upper);
            }
        }
    }

    /**
     * Specifies a {@link Version} constrained {@link Artifact}.
     * <p>
     * {@link Artifact}s are constrained to {@link Version}s using {@link Version.Range}s.
     */
    class Constraint
        implements Predicate<Artifact> {

        private final String groupId;
        private final String artifactId;
        private final Version.Range range;
        private final String type;
        private final String classifier;

        /**
         * Constructs an {@link Artifact.Constraint}.
         *
         * @param groupId the groupId for the {@link Artifact.Constraint}
         * @param artifactId the artifactId for the {@link Artifact.Constraint}
         * @param range the {@link Version.Range} for the {@link Artifact.Constraint}
         * @param type the type for the {@link Artifact.Constraint}
         * @param classifier the optional classifier for the {@link Artifact.Constraint}
         */
        private Constraint(final String groupId,
                           final String artifactId,
                           final Version.Range range,
                           final String type,
                           final String classifier) {

            this.groupId = Objects.requireNonNull(groupId, "The groupId must not be null");
            this.artifactId = Objects.requireNonNull(artifactId, "The artifactId must not be null");
            this.range = Objects.requireNonNull(range, "The range must not be null");
            this.type = Objects.requireNonNull(type, "The type must not be null");
            this.classifier = Strings.isEmpty(classifier) ? null : classifier;
        }

        /**
         * Obtains the group for the {@link Artifact}.
         *
         * @return the group for the {@link Artifact}.
         */
        public String groupId() {
            return this.groupId;
        }

        /**
         * Obtains the name for the {@link Artifact}.
         *
         * @return the name for the {@link Artifact}.
         */
        public String artifactId() {
            return this.artifactId;
        }

        /**
         * Obtains the {@link Version.Range} constraining the {@link Version}s of the {@link Artifact}.
         *
         * @return the {@link Version.Range}
         */
        public Version.Range range() {
            return this.range;
        }

        /**
         * Obtains the type for the {@link Artifact}.
         *
         * @return the type for the {@link Artifact}.
         */
        public String type() {
            return this.type;
        }

        /**
         * Obtains the {@link Optional} classifier for the {@link Artifact}.
         *
         * @return the {@link Optional} classifier for the {@link Artifact}.
         */
        public Optional<String> classifier() {
            return Optional.ofNullable(this.classifier);
        }

        /**
         * Determines if the specified {@link Artifact} satisfies the {@link Constraint} by having the same
         * {@link #groupId()}, {@link #artifactId()}, {@link #type()}, {@link #classifier()} and the
         * {@link Artifact#version()} is in the {@link #range()}.
         *
         * @param artifact the {@link Artifact}
         * @return {@code true} if the {@link Artifact} satisfies the {@link Constraint}, {@code false} otherwise
         */
        public boolean contains(final Artifact artifact) {
            return test(artifact);
        }

        /**
         * Determines if the specified {@link Version} is in the {@link Version.Range} defined by the {@link Constraint}.
         *
         * @param version the {@link Version}
         * @return {@code true} if the {@link Version} satisfies the {@link Constraint}, {@code false} otherwise
         */
        public boolean contains(final Version version) {
            return version != null && range().contains(version);
        }

        /**
         * Creates an {@link Artifact} based on the information defined by the {@link Constraint} and the specified
         * {@link Version}, if and only if the said {@link Version} satisfies the {@link Constraint}.
         *
         * @param version the {@link Version}
         * @return {@link Optional} {@link Artifact}
         * @throws IllegalArgumentException if the specified {@link Version} is not the {@link Version.Range} defined
         *          by the {@link Constraint}
         */
        public Artifact createArtifact(final Version version)
            throws IllegalArgumentException {

            if (!contains(version)) {
                throw new IllegalArgumentException(
                    "The specified version " + version + " is not in the range " + this.range);
            }

            return new Artifact.Implementation(groupId(), artifactId(), version, type(), this.classifier);
        }

        @Override
        public boolean test(final Artifact artifact) {
            return artifact != null
                && groupId().equals(artifact.groupId())
                && artifactId().equals(artifact.artifactId())
                && type().equals(artifact.type())
                && classifier().equals(artifact.classifier())
                && range().contains(artifact.version());
        }

        @Override
        public String toString() {
            return this.groupId + ":" +
                this.artifactId + ":" +
                this.type + ":" +
                (classifier().map(c -> c + ":").orElse("")) +
                this.range;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final Constraint that = (Constraint) object;

            return this.groupId.equals(that.groupId) &&
                this.artifactId.equals(that.artifactId) &&
                this.range.equals(that.range) &&
                this.type.equals(that.type) &&
                Objects.equals(this.classifier, that.classifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.artifactId, this.range, this.type, this.classifier);
        }

        /**
         * Creates a {@link Artifact.Constraint}, specifically constraining the {@link Version.Range} to the
         * specified {@link Artifact}.
         *
         * @param artifact the {@link Artifact}
         * @return a new {@link Artifact.Constraint}
         */
        public static Constraint of(final Artifact artifact) {
            Objects.requireNonNull(artifact, "The Artifact must not be null");

            return new Constraint(artifact.groupId(),
                artifact.artifactId(),
                Version.Range.of(artifact.version()),
                artifact.type(),
                artifact.classifier().orElse(null));
        }

        /**
         * Creates a {@link Artifact.Constraint} based on the specified Apache Maven Coordinates, which are in the
         * following format {@code <groupId>:<artifactId>[:<module>[:<classifier>]]:<version-range>}, where by
         * the {@code <version-range>} is formatted according to {@link Version.Range#parse(String)}.
         *
         * @param coordinates the coordinates
         *
         * @return a new {@link Artifact.Constraint}
         * @throws ParseException if the specified coordinates are in an unknown format
         */
        public static Constraint parse(final String coordinates) {

            // the Apache Maven Coordinates Pattern
            final Pattern PATTERN = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");

            final Matcher matcher = PATTERN.matcher(coordinates);

            if (!matcher.matches()) {
                throw new ParseException(LookaheadReader.Location.START,
                    "<groupId>:<artifactId>[:<module>[:<classifier>]]:<version-range>",
                    coordinates);
            }

            final String groupId = matcher.group(1);
            final String artifactId = matcher.group(2);
            final String extension = Strings.isEmpty(matcher.group(4)) ? "jar" : matcher.group(4);
            final String classifier = matcher.group(6);
            final Version.Range range = Version.Range.parse(matcher.group(7));

            return new Constraint(groupId, artifactId, range, extension, classifier);
        }
    }

    /**
     * Provides the ability to resolve the {@link Path} and {@link ModuleDescriptor} of {@link Artifact}s so that
     * they may be used by {@link Task}s.
     */
    interface Resolver {

        /**
         * Attempts to resolve the specified {@link Artifact}.
         * <p>
         * Should the {@link Resolver} be unable to locate the {@link Artifact}, the returned {@link Exceptional}
         * will indicate the possible cause of the issue, including resolver failure.
         *
         * @param artifact the {@link Artifact}
         *
         * @return an {@link Exceptional} {@link Path} of the resolved {@link Artifact}
         */
        Exceptional<Path> resolve(Artifact artifact);

        /**
         * Attempts to obtain the {@link ModuleReference} for the specified {@link Artifact}.
         * <p>
         * Should the {@link Resolver} be unable to resolve the {@link ModuleReference}, the {@link ModuleCatalog}
         * will be used.  Should neither the {@link Resolver} or {@link ModuleCatalog} be able to determine the
         * {@link ModuleReference}, {@link Exceptional#empty()} will be returned.
         *
         * @param artifact the {@link Artifact}
         * @param catalog the {@link ModuleCatalog}
         *
         * @return an {@link Exceptional} {@link ModuleReference} for the specified {@link Artifact}, or
         *         {@link Exceptional#empty()} if unknown.
         *
         * @see ModuleCatalog#getDerivedModuleReference(Artifact)
         */
        Exceptional<ModuleReference> getModuleReference(Artifact artifact,
                                                        ModuleCatalog catalog);

        /**
         * Attempt to obtain the {@link ModuleDescriptor} for the specified {@link Artifact}.
         *
         * @param artifact the {@link Artifact}
         * @param catalog the {@link ModuleCatalog}
         * @param versioning the {@link ModuleVersioning}
         *
         * @return an {@link Exceptional} {@link ModuleDescriptor} for the specified {@link Artifact}
         */
        Exceptional<ModuleDescriptor> getModuleDescriptor(Artifact artifact,
                                                          ModuleCatalog catalog,
                                                          ModuleVersioning versioning);
    }

    /**
     * The default implementation of an {@link Artifact}.
     */
    class Implementation
        implements Artifact {

        private final String groupId;
        private final String artifactId;
        private final Version version;
        private final String type;
        private final String classifier;

        /**
         * Constructs an {@link Artifact}.
         *
         * @param groupId the groupId for the {@link Artifact}
         * @param artifactId the artifactId for the {@link Artifact}
         * @param version the version for the {@link Artifact}
         * @param type the type for the {@link Artifact}
         * @param classifier the optional classifier for the {@link Artifact}
         */
        public Implementation(final String groupId,
                              final String artifactId,
                              final Version version,
                              final String type,
                              final String classifier) {

            this.groupId = Objects.requireNonNull(groupId, "The groupId must not be null");
            this.artifactId = Objects.requireNonNull(artifactId, "The artifactId must not be null");
            this.version = Objects.requireNonNull(version, "The version must not be null");
            this.type = Objects.requireNonNull(type, "The type must not be null");
            this.classifier = Strings.isEmpty(classifier) ? null : classifier;
        }

        /**
         * Constructs an {@link Artifact}.
         *
         * @param groupId the groupId for the {@link Artifact}
         * @param artifactId the artifactId for the {@link Artifact}
         * @param version the version for the {@link Artifact}
         * @param type the type for the {@link Artifact}
         * @param classifier the optional classifier for the {@link Artifact}
         */
        public Implementation(final String groupId,
                              final String artifactId,
                              final String version,
                              final String type,
                              final String classifier) {

            this(groupId, artifactId, Version.parse(version), type, classifier);
        }

        /**
         * Constructs an {@link Artifact}, without a classifier.
         *
         * @param groupId the groupId for the {@link Artifact}
         * @param artifactId the artifactId for the {@link Artifact}
         * @param version the version for the {@link Artifact}
         * @param type the type for the {@link Artifact}
         */
        public Implementation(final String groupId,
                              final String artifactId,
                              final String version,
                              final String type) {

            this(groupId, artifactId, version, type, null);
        }

        @Override
        public String groupId() {
            return this.groupId;
        }

        @Override
        public String artifactId() {
            return this.artifactId;
        }

        @Override
        public Version version() {
            return this.version;
        }

        @Override
        public String type() {
            return this.type;
        }

        @Override
        public Optional<String> classifier() {
            return Optional.ofNullable(this.classifier);
        }

        @Override
        public String toString() {
            return this.groupId + ":" +
                this.artifactId + ":" +
                this.type + ":" +
                (classifier().map(c -> c + ":").orElse("")) +
                this.version;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final Implementation that = (Implementation) object;

            return this.groupId.equals(that.groupId) &&
                this.artifactId.equals(that.artifactId) &&
                this.version.equals(that.version) &&
                this.type.equals(that.type) &&
                Objects.equals(this.classifier, that.classifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.artifactId, this.version, this.type, this.classifier);
        }
    }
}
