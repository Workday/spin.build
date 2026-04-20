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

import build.base.foundation.Capture;
import build.base.foundation.Exceptional;
import build.base.foundation.Strings;
import build.base.foundation.iterator.FilteringIterator;
import build.base.foundation.predicate.Predicates;
import build.base.foundation.stream.Streamable;
import build.base.foundation.tuple.Triple;
import build.base.parsing.Filter;
import build.base.parsing.ParseException;
import build.base.parsing.Scanner;
import build.base.version.Version;
import build.spin.Project;
import build.spin.Visitor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Provides immutable runtime information concerning a <i>Module</i> produced, used or required by a {@link Project}.
 * <p>
 * <i>Modules</i> are independent units of deployment that can be composed to construct an application at runtime.
 * In Java, <i>modules</i> are defined according to the
 * <a href="https://www.oracle.com/corporate/features/understanding-java-9-modules.html">Java Module System</a>.
 * <p>
 * While the {@link ModuleDescriptor} closely follows the design principles of the Java Module System,
 * and more specifically the Java Platform
 * <a href="https://docs.oracle.com/javase/9/docs/api/java/lang/module/ModuleDescriptor.html">ModuleDescriptor</a>
 * class, {@link Project}s are not required to use either Java or the Java Module System to define <i>modules</i>.
 * {@link ModuleDescriptor}s may be used to define <i>modules</i> for {@link Project}s independent of Java and/or
 * the Java Module System.  For example, it's reasonable to use {@link ModuleDescriptor}s to capture and
 * represent <i>module</i> information provided by other module systems, including Apache Maven and Apache Ivy.
 *
 * @author brian.oliver
 * @since Aug-2019
 */
public interface ModuleDescriptor {

    /**
     * The name of the source file containing a {@link ModuleDescriptor}.
     */
    String SOURCE_FILENAME = "module-info.java";

    /**
     * The default {@link Version} used when no version is specified.
     */
    Version DEFAULT_VERSION = Version.parse("1.0.0-SNAPSHOT");

    /**
     * Obtains the name of the module.
     *
     * @return the name of the module
     */
    String name();

    /**
     * Obtains the {@link ModuleReference} for the {@link ModuleDescriptor}.
     *
     * @return the {@link ModuleReference}
     */
    default ModuleReference reference() {
        return ModuleReference.of(this);
    }

    /**
     * Obtains the {@link Optional} {@link URI} representing source location of the {@link ModuleDescriptor}.
     *
     * @return the {@link Optional} {@link URI} of the source location, or {@link Optional#empty()} when generated.
     */
    Optional<URI> location();

    /**
     * Determines if the module was enhanced with additional information after it was read or created.
     *
     * @return {@code true} if the module has been enhanced, {@code false} otherwise
     */
    default boolean isEnhanced() {
        return is(Modifier.ENHANCED);
    }

    /**
     * Determines if the module is open.
     *
     * @return {@code true} if the module is open, {@code false} otherwise
     */
    default boolean isOpen() {
        return is(Modifier.OPEN);
    }

    /**
     * Determines if the module is automatic.
     *
     * @return {@code true} if the module is automatic, {@code false} otherwise
     */
    default boolean isAutomatic() {
        return is(Modifier.AUTOMATIC);
    }

    /**
     * Determines if the module is synthetic.
     * <p>
     * A synthetic {@link ModuleDescriptor} is one that has been dynamically created to represent information for a
     * potential module defined by an external Module System that doesn't use {@link ModuleDescriptor}s.
     * <p>
     * For example, a synthetic module {@link ModuleDescriptor} may be created for a non-modular Java Archive, based
     * on an Apache Maven pom.xml file.
     *
     * @return {@code true} if the module is synthetic, {@code false} otherwise
     */
    default boolean isSynthetic() {
        return is(Modifier.SYNTHETIC);
    }

    /**
     * Determines if the module has the specific {@link Modifier}.
     *
     * @param modifier the {@link Modifier}
     * @return {@code true} if the module has the specified {@link Modifier}, {@code false} otherwise
     */
    boolean is(ModuleDescriptor.Modifier modifier);

    /**
     * Obtains the {@link Modifier}s for the module.
     *
     * @return a {@link Stream} of the {@link Modifier}s
     */
    Stream<Modifier> modifiers();

    /**
     * Obtains the {@link Requires}, each representing a dependency of the module.
     *
     * @return a {@link Stream} of {@link Requires}
     */
    Stream<Requires> requires();

    /**
     * Obtains the {@link Optional} {@link Version} of the module.
     *
     * @return the {@link Optional} {@link Version}
     */
    Optional<Version> version();

    /**
     * Obtains the {@link Provides}, each representing one or more service implementations provided by the module.
     *
     * @return a {@link Stream} of {@link Provides}
     */
    Stream<Provides> provides();

    /**
     * Obtains the fully qualified class names of the services used by the module.
     *
     * @return a {@link Stream} of fully qualified class names
     */
    Stream<String> uses();

    /**
     * Obtains the {@link Exports}, each representing a source package exported by the module.
     *
     * @return a {@link Stream} of {@link Exports}
     */
    Stream<Exports> exports();

    /**
     * Obtains the {@link Opens}, each representing a source package opened by the module.
     *
     * @return a {@link Stream} of {@link Exports}
     */
    Stream<Opens> opens();

    /**
     * Obtains the fully qualified class names of annotations declared on the {@code module} declaration
     * (i.e. annotations that appear in {@code module-info.java} before the {@code module} keyword).
     *
     * @return a {@link Stream} of fully qualified annotation class names (without the leading {@code @})
     */
    default Stream<String> annotations() {
        return Stream.empty();
    }

    /**
     * Performs a depth-first-traversal of the {@link ModuleDescriptor}, including any dependencies defined by
     * {@link Requires}s.
     *
     * @param visitor the {@link ModuleDescriptor} {@link build.spin.Visitor}
     * @param resolver the {@link ModuleDescriptor.Resolver} for {@link ModuleReference}s.
     *
     * @throws CyclicDependencyException when an attempt is made to visit a {@link ModuleDescriptor} which is currently
     *         in the process of being visited, either directly or indirectly
     * @throws UnresolvableModuleException when a {@link ModuleDescriptor} can't be resolved
     */
    default void walk(final Visitor<? super ModuleDescriptor> visitor,
                      final ModuleDescriptor.Resolver resolver)
        throws CyclicDependencyException, UnresolvableModuleException {

        walk(visitor, Predicates.always(), resolver);
    }

    /**
     * Performs a depth-first-traversal of the {@link ModuleDescriptor}, including any dependencies defined by
     * {@link Requires}s.
     *
     * @param visitor the {@link ModuleDescriptor} {@link build.spin.Visitor}
     * @param predicate the {@link Predicate} to filter {@link Requires} to process
     * @param resolver the {@link ModuleDescriptor.Resolver} for {@link ModuleReference}s.
     *
     * @throws CyclicDependencyException when an attempt is made to visit a {@link ModuleDescriptor} which is currently
     *         in the process of being visited, either directly or indirectly
     * @throws UnresolvableModuleException when a {@link ModuleDescriptor} can't be resolved
     */
    default void walk(final build.spin.Visitor<? super ModuleDescriptor> visitor,
                      final Predicate<? super Requires> predicate,
                      final ModuleDescriptor.Resolver resolver)
        throws CyclicDependencyException, UnresolvableModuleException {

        Objects.requireNonNull(visitor, "The Visitor must not be null");
        Objects.requireNonNull(predicate, "The Predicate must not be null");
        Objects.requireNonNull(resolver, "The ModuleDescriptor.Resolver must not be null");

        // establish the stack of ModuleDescriptors for depth-first traversal
        // (ModuleDescriptor, CommencedVisiting, Requires)
        final Deque<Triple<ModuleDescriptor, Boolean, Iterator<Requires>>> stack = new LinkedList<>();

        stack.push(Triple.of(this, false, FilteringIterator.of(requires().iterator(), predicate)));

        while (!stack.isEmpty()) {
            final var triple = stack.peek();
            final var moduleDescriptor = triple.first();
            final var commenced = triple.second();
            final var iterator = triple.third();

            if (commenced) {
                if (iterator.hasNext()) {
                    // process the next dependency for the ModuleDescriptor
                    final var requires = iterator.next();

                    // attempt to resolve the ModuleDescriptor for the ModuleReference
                    final var descriptor = resolver.apply(requires)
                        .orElseRethrow(throwable -> new UnresolvableModuleException(requires.reference()));

                    // ensure the dependency isn't in the stack (if it is, we've found a CyclicDependencyException)
                    if (stack.stream()
                        .map(Triple::first)
                        .anyMatch(m -> m.name().equals(requires.name()))) {
                        throw new CyclicDependencyException(moduleDescriptor, descriptor);
                    }

                    stack.push(
                        Triple.of(descriptor, false,
                            FilteringIterator.of(descriptor.requires().iterator(), predicate)));
                }
                else {
                    // we're done with this ModuleDescriptor
                    stack.pop();
                    visitor.onLeaving(moduleDescriptor);
                }
            }
            else {
                // commence processing the ModuleDescriptor
                stack.pop();

                if (visitor.onEntering(moduleDescriptor)) {
                    visitor.visit(moduleDescriptor);
                    stack.push(Triple.of(moduleDescriptor, true, iterator));
                }
                else {
                    visitor.onLeaving(moduleDescriptor);
                }
            }
        }
    }

    /**
     * Obtains the {@link Streamable} <i>set</i> of transitive {@link ModuleDescriptor}s for the dependencies of
     * the module.
     *
     * @param resolver the {@link Resolver} to resolve {@link ModuleDescriptor}s for {@link Requires} dependencies
     * @return a {@link Streamable} of {@link ModuleDescriptor} dependencies
     */
    default Streamable<ModuleDescriptor> dependencies(final Resolver resolver) {

        final LinkedHashSet<ModuleDescriptor> descriptors = new LinkedHashSet<>();

        walk(descriptors::add, Predicates.always(), resolver);

        return Streamable.of(descriptors);
    }

    /**
     * Provides runtime information concerning a dependency of a module.
     */
    interface Requires {

        /**
         * The name of the required module.
         *
         * @return the name of the required module
         */
        String name();

        /**
         * The {@link Optional} {@link Version} of the required module.
         *
         * @return the {@link Optional} {@link Version}
         */
        Optional<Version> version();

        /**
         * Obtains a {@link ModuleReference} for the required module.
         *
         * @return a {@link ModuleReference}
         */
        default ModuleReference reference() {
            return ModuleReference.of(name(), version());
        }

        /**
         * The {@link Modifier}s for the module.
         *
         * @return a {@link Stream} of {@link Modifier}s
         */
        Stream<Modifier> modifiers();

        /**
         * The modifiers for a required module.
         */
        enum Modifier {
            TRANSITIVE,
            STATIC,
            SYNTHETIC,
            MANDATED
        }

        /**
         * Determines if the {@link Requires} has a {@link Modifier#TRANSITIVE} {@link Modifier}.
         *
         * @return {@code true} if {@link Modifier#TRANSITIVE}, {@code false} otherwise
         */
        boolean isTransitive();

        /**
         * Determines if the {@link Requires} has a {@link Modifier#STATIC} {@link Modifier}.
         *
         * @return {@code true} if {@link Modifier#STATIC}, {@code false} otherwise
         */
        boolean isStatic();

        class Implementation
            implements Requires {

            /**
             * The {@link ModuleReference} to the required module.
             */
            private final ModuleReference reference;

            /**
             * The {@link Modifier}s for the required module.
             */
            private final EnumSet<Modifier> modifiers;

            /**
             * Constructs a {@link Requires.Implementation}.
             *
             * @param name the name of the required module
             * @param modifiers the {@link Modifier}s for the required module.
             * @param version the {@code nullable} {@link Version}
             */
            private Implementation(final String name,
                                   final EnumSet<Modifier> modifiers,
                                   final Version version) {

                this.reference = ModuleReference.of(name, version);
                this.modifiers = modifiers == null
                    ? EnumSet.noneOf(Modifier.class)
                    : modifiers.clone();
            }

            @Override
            public String name() {
                return this.reference.name();
            }

            @Override
            public Optional<Version> version() {
                return this.reference.version();
            }

            @Override
            public ModuleReference reference() {
                return this.reference;
            }

            @Override
            public Stream<Modifier> modifiers() {
                return this.modifiers.stream();
            }

            @Override
            public boolean isTransitive() {
                return this.modifiers.contains(Modifier.TRANSITIVE);
            }

            @Override
            public boolean isStatic() {
                return this.modifiers.contains(Modifier.STATIC);
            }

            @Override
            public int hashCode() {
                return this.reference.hashCode();
            }

            @Override
            public boolean equals(final Object object) {
                if (object instanceof Requires) {
                    final Requires other = (Requires) object;

                    final EnumSet<Modifier> modifiers = this.modifiers.clone();
                    final long count = other.modifiers()
                        .map(modifiers::remove)
                        .filter(value -> !value)
                        .count();

                    return reference().equals(other.reference())
                        && modifiers.isEmpty() && count == 0;
                }
                return false;
            }
        }

        /**
         * Constructs a {@link Requires} for the specified module name with the provided {@link Requires.Modifier}s.
         *
         * @param name the module name
         * @param modifiers the {@link Requires.Modifier}s
         * @return a new {@link Requires}
         */
        static Requires of(final String name, final Requires.Modifier... modifiers) {
            return of(name, null, modifiers);
        }

        /**
         * Constructs a {@link Requires} for the specified module name and {@link Version} with the
         * provided {@link Requires.Modifier}s.
         *
         * @param name the module name
         * @param version the {@link Version}
         * @param modifiers the {@link Requires.Modifier}s
         * @return a new {@link Requires}
         */
        static Requires of(final String name,
                           final Version version,
                           final Requires.Modifier... modifiers) {

            final EnumSet<Requires.Modifier> set = EnumSet.noneOf(Requires.Modifier.class);
            if (modifiers != null) {
                for (Requires.Modifier modifier : modifiers) {
                    set.add(modifier);
                }
            }
            return new Implementation(name, set, version);
        }
    }

    /**
     * Provides runtime information defining the one or more implementations of a Service provided by a module.
     */
    interface Provides {

        /**
         * Obtains the fully qualified class name of the service type.
         *
         * @return the fully qualified class name of the service type
         */
        String service();

        /**
         * Obtains the {@link Stream} of fully qualified class names of providers or provider factories.
         *
         * @return the {@link Stream} of fully qualified class names of providers or provider factories
         */
        Stream<String> providers();
    }

    /**
     * Provides runtime information defining the optionally qualified exported package from a module.
     */
    interface Exports {

        /**
         * Determines if the {@link Exports} is qualified.
         *
         * @return {@code true} when the {@link Exports} is qualified, {@code false} otherwise
         */
        default boolean isQualified() {
            return targets().anyMatch(Predicates.always());
        }

        /**
         * Obtains the fully qualified name of the exported source package.
         *
         * @return the exported source package
         */
        String source();

        /**
         * Obtains the {@link Stream} of module names to which the source is exported.
         *
         * @return the {@link Stream} of module names
         */
        Stream<String> targets();

        /**
         * The {@link Optional} {@link Exports.Modifier} for an {@link Exports}.
         *
         * @return the {@link Exports.Modifier}
         */
        Optional<Exports.Modifier> modifier();

        /**
         * The modifiers for an {@link Exports}.
         */
        enum Modifier {
            /**
             * The export was implicitly declared.
             */
            MANDATED,

            /**
             * The export was not explicitly or implicitly declared.
             */
            SYNTHETIC
        }
    }

    /**
     * Provides runtime information defining the optionally qualified opened package from a module.
     */
    interface Opens {

        /**
         * Determines if the {@link Opens} is qualified.
         *
         * @return {@code true} when the {@link Opens} is qualified, {@code false} otherwise
         */
        default boolean isQualified() {
            return targets().anyMatch(Predicates.always());
        }

        /**
         * Obtains the fully qualified name of the opened source package.
         *
         * @return the exported source package
         */
        String source();

        /**
         * Obtains the {@link Stream} of module names to which the source is open.
         *
         * @return the {@link Stream} of module names
         */
        Stream<String> targets();

        /**
         * The {@link Optional} {@link Opens.Modifier} for an {@link Opens}.
         *
         * @return the {@link Opens.Modifier}
         */
        Optional<Opens.Modifier> modifier();

        /**
         * The modifiers for an {@link Opens}.
         */
        enum Modifier {
            /**
             * Opening was implicitly declared.
             */
            MANDATED,

            /**
             * Opening was not explicitly or implicitly declared.
             */
            SYNTHETIC
        }
    }

    /**
     * The modifiers for a module.
     */
    enum Modifier {
        /**
         * An automatic module.
         */
        AUTOMATIC,

        /**
         * An open module.
         */
        OPEN,

        /**
         * The module was implicitly declared.
         */
        MANDATED,

        /**
         * The module was neither explicitly nor implicitly declared (it was generated).
         */
        SYNTHETIC,

        /**
         * The module was enhanced with additional information after it was read or created.
         */
        ENHANCED
    }


    /**
     * Parses the specified text-based {@code module-info.java} formatted {@link Reader} to produce a
     * {@link ModuleDescriptor.Builder}, including the parsed information.
     *
     * @param reader the {@link Reader}
     * @return the {@link ModuleDescriptor.Builder}
     * @throws ParseException when the {@link ModuleDescriptor} can't be parsed
     */
    static ModuleDescriptor.Builder parse(final Reader reader)
        throws ParseException {

        // regular expression patterns for parsing
        final Pattern NAME_PATTERN = Pattern.compile("([a-zA-Z][a-zA-Z0-9]*)(\\.[a-zA-Z][a-zA-Z0-9]*)*");
        final Pattern MODULE_NAME_PATTERN = NAME_PATTERN;
        final Pattern PACKAGE_NAME_PATTERN = NAME_PATTERN;
        final Pattern TYPE_NAME_PATTERN = NAME_PATTERN;

        // strings for parsing
        final String CLOSE_BRACE = "}";
        final String COMMA = ",";
        final String EXPORTS = "exports";
        final String MODULE = "module";
        final String OPEN = "open";
        final String OPEN_BRACE = "{";
        final String OPENS = "opens";
        final String PROVIDES = "provides";
        final String REQUIRES = "requires";
        final String SEMICOLON = ";";
        final String STATIC = "static";
        final String TO = "to";
        final String TRANSITIVE = "transitive";
        final String USES = "uses";
        final String WITH = "with";

        final Scanner scanner = new Scanner(reader)
            .register(Filter.WHITESPACE)
            .register(Filter.JAVA_SINGLE_LINE_COMMENT)
            .register(Filter.JAVA_MULTILINE_COMMENT);

        // skip any import statements that legally precede the module declaration
        final String IMPORT = "import";
        while (scanner.follows(IMPORT)) {
            scanner.consume(IMPORT);
            scanner.consume(Pattern.compile("[^;]+"));
            scanner.consume(SEMICOLON);
        }

        // capture annotations that legally precede the module declaration (e.g. @SomeAnnotation or @Some.Annotation(...))
        final ArrayList<String> moduleAnnotations = new ArrayList<>();
        while (scanner.follows(Pattern.compile("@[\\w.]+"))) {
            final String token = scanner.consume(Pattern.compile("@[\\w.]+"));
            moduleAnnotations.add(token.substring(1)); // strip leading @
            if (scanner.follows(Pattern.compile("\\("))) {
                scanner.consume(Pattern.compile("\\([^)]*\\)"));
            }
        }

        final Optional<String> open = scanner.optionallyConsume(OPEN);

        scanner.consume(MODULE);

        final String moduleName = scanner.consume(MODULE_NAME_PATTERN);

        // establish a ModuleDescriptor.Builder to eventually build the ModuleDescriptor
        // (the ModuleDescriptor is considered "generated" as it hasn't been extracted from an explicit module)
        final Builder builder = Builder.create(moduleName)
            .noLocation();

        builder.setOpen(open.isPresent());
        moduleAnnotations.forEach(builder::annotation);

        scanner.consume(OPEN_BRACE);

        while (scanner.hasNext() && !scanner.follows(CLOSE_BRACE)) {

            if (scanner.optionallyConsume(EXPORTS).isPresent()) {

                final String source = scanner.consume(PACKAGE_NAME_PATTERN);

                final ArrayList<String> targets = new ArrayList<>();
                if (scanner.optionallyConsume(TO).isPresent()) {
                    do {
                        targets.add(scanner.consume(MODULE_NAME_PATTERN));

                        if (scanner.follows(COMMA)) {
                            scanner.consume(COMMA);
                        }
                    } while (scanner.follows(MODULE_NAME_PATTERN));
                }

                builder.exports(source, Exports.Modifier.MANDATED, targets.stream());

                scanner.consume(SEMICOLON);
            }
            else if (scanner.optionallyConsume(OPENS).isPresent()) {

                final String source = scanner.consume(PACKAGE_NAME_PATTERN);

                final ArrayList<String> targets = new ArrayList<>();
                if (scanner.optionallyConsume(TO).isPresent()) {
                    do {
                        targets.add(scanner.consume(MODULE_NAME_PATTERN));

                        if (scanner.follows(COMMA)) {
                            scanner.consume(COMMA);
                        }
                    } while (scanner.follows(MODULE_NAME_PATTERN));
                }

                builder.opens(source, Opens.Modifier.MANDATED, targets.stream());

                scanner.consume(SEMICOLON);
            }
            else if (scanner.optionallyConsume(USES).isPresent()) {
                final String service = scanner.consume(TYPE_NAME_PATTERN);

                builder.uses(service);

                scanner.consume(SEMICOLON);
            }
            else if (scanner.optionallyConsume(PROVIDES).isPresent()) {
                final String service = scanner.consume(TYPE_NAME_PATTERN);
                scanner.consume(WITH);

                final LinkedHashSet<String> providers = new LinkedHashSet<>();
                do {
                    final String provider = scanner.consume(TYPE_NAME_PATTERN);
                    providers.add(provider.trim());

                    if (scanner.follows(COMMA)) {
                        scanner.consume(COMMA);
                    }
                } while (scanner.follows(TYPE_NAME_PATTERN));

                builder.provides(service, providers.stream());

                scanner.consume(SEMICOLON);
            }
            else if (scanner.optionallyConsume(REQUIRES).isPresent()) {

                final EnumSet<Requires.Modifier> modifiers = EnumSet.noneOf(Requires.Modifier.class);

                if (scanner.follows(STATIC)) {
                    scanner.consume(STATIC);
                    modifiers.add(Requires.Modifier.STATIC);
                }
                else if (scanner.follows(TRANSITIVE)) {
                    scanner.consume(TRANSITIVE);
                    modifiers.add(Requires.Modifier.TRANSITIVE);
                }

                final String name = scanner.consume(MODULE_NAME_PATTERN);

                builder.requires(name, modifiers, null);

                scanner.consume(SEMICOLON);
            }
            else {
                throw new ParseException(
                    scanner.getLocation(),
                    "Invalid module-info.java format",
                    "exports, opens, uses, provides, or requires");
            }
        }

        scanner.consume(CLOSE_BRACE);

        return builder;
    }

    /**
     * Parses the specified {@code module-info.java} formatted {@link String} to produce a
     * {@link ModuleDescriptor.Builder}.
     *
     * @param string the {@link String}
     * @return the {@link ModuleDescriptor.Builder}
     */
    static ModuleDescriptor.Builder parse(final String string) {
        return parse(new StringReader(string));
    }

    /**
     * Attempts to extract the {@link ModuleDescriptor} for an {@link Artifact} by reading it from the optionally
     * defined {@code module-info.class} contained with in the Java Archive at the specified {@link Path}.  Failing
     * that, attempt to obtain establish an automatic module {@link ModuleDescriptor} based on the
     * {@code META-INF/MANIFEST.MF} {@code Automatic-Module-Name} entry.
     * <p>
     * The successfully returned {@link ModuleDescriptor} may be incomplete and/or lacking more precise module
     * dependency information that may be available from an {@link Artifact.Resolver}.  For example, it's possible
     * that {@code requires} version information is missing from a compiled {@code module-info.class} because the
     * dependency was not placed on the module path when compiled.
     *
     * @param artifact the {@link Artifact} representing the
     * @param path the {@link Path} of the Java Archive (jar) file
     *
     * @return the {@link Exceptional} {@link ModuleDescriptor} or {@link Exceptional#empty()} if not extractable
     */
    static Exceptional<ModuleDescriptor> extract(final Artifact artifact,
                                                 final Path path) {

        if (!Files.exists(path)) {
            return Exceptional.ofException(new FileNotFoundException(path.toString()));
        }

        // attempt to open the file as a jar
        try (JarFile jarFile = new JarFile(path.toFile())) {

            // attempt to locate a module-info.class (they should be all the same!)
            final Optional<JarEntry> jarEntry = jarFile.stream()
                .filter(entry -> entry.getName().endsWith("module-info.class"))
                .findFirst();

            if (jarEntry.isPresent()) {
                final byte[] bytes = jarFile.getInputStream(jarEntry.get()).readAllBytes();
                final var classModel = ClassFile.of().parse(bytes);
                final Optional<ModuleAttribute> moduleAttribute = classModel.findAttribute(java.lang.classfile.Attributes.module());

                if (moduleAttribute.isPresent()) {
                    final ModuleAttribute mod = moduleAttribute.get();

                    final ModuleDescriptor.Builder builder = ModuleDescriptor.Builder
                        .create(mod.moduleName().name().stringValue())
                        .setLocation(path.toUri());

                    builder.setOpen(mod.moduleFlags().contains(AccessFlag.OPEN));
                    builder.setSynthetic(mod.moduleFlags().contains(AccessFlag.SYNTHETIC));
                    builder.setMandated(mod.moduleFlags().contains(AccessFlag.MANDATED));

                    // include the requires declarations
                    mod.requires().forEach(req -> {
                        final EnumSet<ModuleDescriptor.Requires.Modifier> modifiers =
                            EnumSet.noneOf(ModuleDescriptor.Requires.Modifier.class);

                        if (req.requiresFlags().contains(AccessFlag.TRANSITIVE)) {
                            modifiers.add(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
                        }

                        if (req.requiresFlags().contains(AccessFlag.STATIC_PHASE)) {
                            modifiers.add(ModuleDescriptor.Requires.Modifier.STATIC);
                        }

                        if (req.requiresFlags().contains(AccessFlag.SYNTHETIC)) {
                            modifiers.add(ModuleDescriptor.Requires.Modifier.SYNTHETIC);
                        }

                        if (req.requiresFlags().contains(AccessFlag.MANDATED)) {
                            modifiers.add(ModuleDescriptor.Requires.Modifier.MANDATED);
                        }

                        final Version version = req.requiresVersion()
                            .map(v -> v.stringValue())
                            .filter(v -> !Strings.isEmpty(v))
                            .map(Version::parse)
                            .orElse(null);

                        builder.requires(req.requires().name().stringValue(), modifiers, version);
                    });

                    // include the provides
                    mod.provides().forEach(prov -> {
                        builder.provides(
                            prov.provides().asInternalName(),
                            prov.providesWith().stream().map(ce -> ce.asInternalName()));
                    });

                    // include the uses
                    mod.uses().forEach(use -> builder.uses(use.asInternalName()));

                    // include Version
                    final Optional<String> moduleVersion = mod.moduleVersion().map(v -> v.stringValue());
                    if (moduleVersion.isPresent() && !Strings.isEmpty(moduleVersion.get())) {
                        builder.setVersion(moduleVersion.get());
                    }
                    else {
                        builder.setVersion(artifact.version().toString());
                    }

                    // include Exports
                    mod.exports().forEach(exp -> {
                        builder.exports(exp.exportedPackage().name().stringValue(),
                            exp.exportsFlags().contains(AccessFlag.MANDATED)
                                ? Exports.Modifier.MANDATED
                                : exp.exportsFlags().contains(AccessFlag.SYNTHETIC)
                                    ? Exports.Modifier.SYNTHETIC
                                    : null,
                            exp.exportsTo().stream().map(me -> me.name().stringValue()));
                    });

                    // include Opens
                    mod.opens().forEach(open -> {
                        builder.opens(open.openedPackage().name().stringValue(),
                            open.opensFlags().contains(AccessFlag.MANDATED)
                                ? Opens.Modifier.MANDATED
                                : open.opensFlags().contains(AccessFlag.SYNTHETIC)
                                    ? Opens.Modifier.SYNTHETIC
                                    : null,
                            open.opensTo().stream().map(me -> me.name().stringValue()));
                    });

                    return Exceptional.of(builder.build());
                }
                else {
                    return Exceptional.ofException(
                        new RuntimeException("The module-info.class in " + path + " is not a valid ModuleDescriptor"));
                }
            }
            else {
                // attempt to create a synthetic ModuleDescriptor using information from the Manifest
                final Manifest manifest = jarFile.getManifest();

                if (manifest != null) {
                    // determine the Automatic-Module-Name
                    final String moduleName = manifest.getMainAttributes().getValue("Automatic-Module-Name");

                    if (moduleName != null) {
                        final ModuleDescriptor.Builder builder = ModuleDescriptor.Builder
                            .create(moduleName)
                            .setLocation(path.toUri());

                        builder.setAutomatic(true);

                        // determine the Implementation Version
                        String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);

                        // some version numbers contain spaces (which are illegal)
                        // (we can only use up until the space as a version)
                        if (version != null) {
                            final int idx = version.indexOf(' ');
                            if (idx >= 0) {
                                version = version.substring(0, idx);
                            }

                            if (!Strings.isEmpty(version)) {
                                builder.setVersion(version);
                            }
                        }
                        else {
                            builder.setVersion(artifact.version().toString());
                        }

                        return Exceptional.of(builder.build());
                    }
                }
            }
        }
        catch (final IOException e) {
            return Exceptional.ofException(new UnresolvableModuleDescriptorException(artifact, e));
        }

        return Exceptional.empty();
    }

    /**
     * A builder of {@link ModuleDescriptor}s.
     */
    class Builder {

        /**
         * The name of the module.
         */
        private String name;

        /**
         * The {@link Optional} {@link URI} representing the source location.
         */
        private Optional<URI> location;

        /**
         * The {@link Capture}d {@link Version} of the module.
         */
        private final Capture<Version> version;

        /**
         * The {@link Modifier}s for the module.
         */
        private final EnumSet<Modifier> modifiers;

        /**
         * The {@link Requires} for the module.
         */
        private final LinkedList<Requires> requires;

        /**
         * The {@link Provides} for the module.
         */
        private final LinkedList<Provides> provides;

        /**
         * The fully qualified class names of services used by the module.
         */
        private final LinkedHashSet<String> uses;

        /**
         * The {@link Exports} for the module.
         */
        private final LinkedList<Exports> exports;

        /**
         * The {@link Opens} for the module.
         */
        private final LinkedList<Opens> opens;

        /**
         * The fully qualified class names of annotations declared on the module declaration.
         */
        private final LinkedList<String> annotations;

        /**
         * Constructs a {@link ModuleDescriptor} {@link Builder}.
         *
         * @param name the name of the module
         */
        private Builder(final String name) {
            this.name = Objects.requireNonNull(name, "The Module name must not be null");
            this.location = Optional.empty();
            this.version = Capture.empty();
            this.modifiers = EnumSet.noneOf(Modifier.class);
            this.requires = new LinkedList<>();
            this.provides = new LinkedList<>();
            this.uses = new LinkedHashSet<>();
            this.exports = new LinkedList<>();
            this.opens = new LinkedList<>();
            this.annotations = new LinkedList<>();
        }

        /**
         * Constructs a new {@link ModuleDescriptor.Builder} for a specifically named module.
         *
         * @param name the name of the module
         * @return a new {@link ModuleDescriptor.Builder}
         */
        public static Builder create(final String name) {
            return new Builder(name);
        }

        /**
         * Sets the name of the {@link ModuleDescriptor}.
         *
         * @param name the name
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setName(final String name) {
            this.name = Objects.requireNonNull(name, "The Module name must not be null");

            return this;
        }

        /**
         * Obtains the name of the {@link ModuleDescriptor}.
         *
         * @return the name of the module defined by the {@link ModuleDescriptor}
         */
        public String getName() {
            return this.name;
        }

        /**
         * Sets the {@link Optional} {@link URI} source location.
         *
         * @param location the {@link Optional} {@link URI} source location
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setLocation(final Optional<URI> location) {
            this.location = location == null ? Optional.empty() : location;
            return this;
        }

        /**
         * Sets the {@link URI} source location.
         *
         * @param location the  {@link URI} source location
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setLocation(final URI location) {
            this.location = Optional.ofNullable(location);
            return this;
        }

        /**
         * Sets the {@link URI} source location to be {@link Optional#empty()}.
         *
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder noLocation() {
            this.location = Optional.empty();
            return this;
        }

        /**
         * Obtains the {@link Optional} source locaiton {@link URI}.
         *
         * @return the {@link Optional} source locaiton {@link URI}
         */
        public Optional<URI> getLocation() {
            return this.location;
        }

        /**
         * Sets the {@link Version} of the module for the {@link ModuleDescriptor}.
         * <p>
         * Setting this to {@code null} will result in the version being cleared.
         *
         * @param version the {@link Version}
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setVersion(final Version version) {
            if (version == null) {
                this.version.clear();
            }
            else {
                this.version.set(version);
            }
            return this;
        }

        /**
         * Sets the {@link Version} of the module for the {@link ModuleDescriptor} using a raw version.
         * <p>
         * Setting this to {@code null} will result in the version being cleared.
         *
         * @param version the raw {@link Version}
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setVersion(final String version) {
            if (Strings.isEmpty(version)) {
                this.version.clear();
            }
            else {
                this.version.set(Version.parse(version));
            }
            return this;
        }

        /**
         * Sets if the {@link ModuleDescriptor} will be for an open module.
         *
         * @param open if the module is open
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setOpen(final boolean open) {
            if (open) {
                this.modifiers.add(Modifier.OPEN);
            }
            else {
                this.modifiers.remove(Modifier.OPEN);
            }
            return this;
        }

        /**
         * Sets if the {@link ModuleDescriptor} will be for an automatic module.
         *
         * @param automatic if the module is automatic
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setAutomatic(final boolean automatic) {
            if (automatic) {
                this.modifiers.add(Modifier.AUTOMATIC);
            }
            else {
                this.modifiers.remove(Modifier.AUTOMATIC);
            }
            return this;
        }

        /**
         * Sets if the {@link ModuleDescriptor} will be for a synthetic module.
         *
         * @param synthetic if the module is synthetic
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setSynthetic(final boolean synthetic) {
            if (synthetic) {
                this.modifiers.add(Modifier.SYNTHETIC);
            }
            else {
                this.modifiers.remove(Modifier.SYNTHETIC);
            }
            return this;
        }

        /**
         * Sets if the {@link ModuleDescriptor} will be for a mandated module.
         *
         * @param mandated if the module is mandated
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setMandated(final boolean mandated) {
            if (mandated) {
                this.modifiers.add(Modifier.MANDATED);
            }
            else {
                this.modifiers.remove(Modifier.MANDATED);
            }
            return this;
        }

        /**
         * Sets if the {@link ModuleDescriptor} will be for an enhanced module.
         *
         * @param enhanced if the module is enhanced
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder setEnhanced(final boolean enhanced) {
            if (enhanced) {
                this.modifiers.add(Modifier.ENHANCED);
            }
            else {
                this.modifiers.remove(Modifier.ENHANCED);
            }
            return this;
        }

        /**
         * Adds the {@link Requires} for the {@link ModuleDescriptor}.
         *
         * @param name the name of the required module
         * @param modifiers the {@link Requires.Modifier}s
         * @param version the {@link Version} (may be {@code null)}
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder requires(final String name,
                                final EnumSet<Requires.Modifier> modifiers,
                                final Version version) {

            this.requires.add(new Requires.Implementation(name, modifiers, version));

            return this;
        }

        /**
         * Adds a {@link Provides} definition for the {@link ModuleDescriptor}.
         *
         * @param service the fully qualified class name of the service being implemented
         * @param implementations the fully qualified class names of the service implementations
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder provides(final String service,
                                final Stream<String> implementations) {

            Objects.requireNonNull(service, "The service must not be null");
            Objects.requireNonNull(implementations, "The implementations must not be null");

            final String trimmedService = service.trim().replace("/", ".");
            final ArrayList<String> providers = implementations
                .map(implementation -> implementation.trim().replace("/", "."))
                .filter(string -> !Strings.isEmpty(string))
                .collect(Collectors.toCollection(ArrayList::new));

            if (!trimmedService.isEmpty() && !providers.isEmpty()) {
                this.provides.add(new Provides() {
                    @Override
                    public String service() {
                        return trimmedService;
                    }

                    @Override
                    public Stream<String> providers() {
                        return providers.stream();
                    }

                    @Override
                    public int hashCode() {
                        return service().hashCode();
                    }

                    @Override
                    public boolean equals(final Object object) {

                        if (object instanceof Provides) {
                            final Provides other = (Provides) object;

                            return service().equals(other.service())
                                && providers().collect(Collectors.toSet())
                                .equals(other.providers().collect(Collectors.toSet()));
                        }
                        return false;
                    }
                });
            }

            return this;
        }

        /**
         * Adds a {@link Provides} definition for the {@link ModuleDescriptor}.
         *
         * @param service the fully qualified class name of the service being implemented
         * @param implementations the fully qualified class names of the service implementations
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder provides(final String service,
                                final String... implementations) {

            return provides(service, implementations == null ? Stream.empty() : Arrays.stream(implementations));
        }

        /**
         * Adds the specified fully qualified service class name used by the module.
         *
         * @param service the fully qualified service class names
         *
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder uses(final String service) {

            if (service != null) {
                final String canoncial = service.trim().replace("/", ".");
                if (!canoncial.isEmpty()) {
                    this.uses.add(canoncial);
                }
            }

            return this;
        }

        /**
         * Adds an {@link Exports} definition to the {@link ModuleDescriptor}.
         *
         * @param source the fully qualified source package name
         * @param modifier the {@code null}able {@link Exports.Modifier}
         * @param targets the targeted modules
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder exports(final String source,
                               final Exports.Modifier modifier,
                               final Stream<String> targets) {

            Objects.requireNonNull(source, "The source must not be null");

            final String sanitizedSource = source.trim().replace("/", ".");

            final ArrayList<String> sanitizedTargets = targets == null
                ? new ArrayList<>()
                : targets
                    .map(String::trim)
                    .filter(string -> !Strings.isEmpty(string))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (!sanitizedSource.isEmpty()) {
                this.exports.add(new Exports() {
                    @Override
                    public String source() {
                        return sanitizedSource;
                    }

                    @Override
                    public Stream<String> targets() {
                        return sanitizedTargets.stream();
                    }

                    @Override
                    public Optional<Modifier> modifier() {
                        return Optional.ofNullable(modifier);
                    }

                    @Override
                    public int hashCode() {
                        return source().hashCode();
                    }

                    @Override
                    public boolean equals(final Object object) {
                        if (object instanceof Exports) {
                            final Exports other = (Exports) object;

                            return source().equals(other.source())
                                && modifier().equals(other.modifier())
                                && targets().collect(Collectors.toSet()).equals(
                                other.targets().collect(Collectors.toSet()));
                        }
                        return false;
                    }
                });
            }

            return this;
        }

        /**
         * Adds an {@link Opens} definition to the {@link ModuleDescriptor}.
         *
         * @param source the fully qualified source package name
         * @param modifier the {@code null}able {@link Opens.Modifier}
         * @param targets the targeted modules
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder opens(final String source,
                             final Opens.Modifier modifier,
                             final Stream<String> targets) {

            Objects.requireNonNull(source, "The source must not be null");

            final String sanitizedSource = source.trim().replace("/", ".");

            final ArrayList<String> sanitizedTargets = targets == null
                ? new ArrayList<>()
                : targets
                    .map(String::trim)
                    .filter(string -> !Strings.isEmpty(string))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (!sanitizedSource.isEmpty()) {
                this.opens.add(new Opens() {
                    @Override
                    public String source() {
                        return sanitizedSource;
                    }

                    @Override
                    public Stream<String> targets() {
                        return sanitizedTargets.stream();
                    }

                    @Override
                    public Optional<Modifier> modifier() {
                        return Optional.ofNullable(modifier);
                    }

                    @Override
                    public int hashCode() {
                        return source().hashCode();
                    }

                    @Override
                    public boolean equals(final Object object) {
                        if (object instanceof Opens) {
                            final Opens other = (Opens) object;

                            return source().equals(other.source())
                                && modifier().equals(other.modifier())
                                && targets().collect(Collectors.toSet()).equals(
                                other.targets().collect(Collectors.toSet()));
                        }
                        return false;
                    }
                });
            }

            return this;
        }

        /**
         * Includes all of the declarations of the specified {@link ModuleDescriptor} in the
         * {@link ModuleDescriptor.Builder}, not including the module name.
         *
         * @param moduleDescriptor the {@link ModuleDescriptor}
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder include(final ModuleDescriptor moduleDescriptor) {

            // include the requires
            moduleDescriptor.requires()
                .forEach(require -> requires(
                    require.name(),
                    require.modifiers().collect(
                        Collectors.toCollection(() -> EnumSet.noneOf(Requires.Modifier.class))),
                    require.version().orElse(null)));

            // include the provides
            moduleDescriptor.provides()
                .forEach(p -> provides(p.service(), p.providers()));

            // include the uses
            moduleDescriptor.uses()
                .forEach(this::uses);

            // include the exports
            moduleDescriptor.exports()
                .forEach(e -> exports(e.source(), e.modifier().orElse(null), e.targets()));

            // include the opens
            moduleDescriptor.opens()
                .forEach(o -> opens(o.source(), o.modifier().orElse(null), o.targets()));

            return this;
        }

        /**
         * Records a fully qualified annotation class name declared on the {@code module} declaration.
         *
         * @param annotationClassName the fully qualified annotation class name (without the leading {@code @})
         * @return this {@link Builder} to allow fluent-style method calls
         */
        public Builder annotation(final String annotationClassName) {
            this.annotations.add(Objects.requireNonNull(annotationClassName, "annotationClassName must not be null"));
            return this;
        }

        /**
         * Builds a new {@link ModuleDescriptor} given the current state of the {@link Builder}.
         *
         * @return a new {@link ModuleDescriptor}
         */
        public ModuleDescriptor build() {
            return new Implementation(this.name,
                this.location,
                this.version.optional(),
                this.modifiers.clone(),
                this.requires.stream(),
                this.provides.stream(),
                this.uses.stream(),
                this.exports.stream(),
                this.opens.stream(),
                this.annotations.stream());
        }
    }

    /**
     * A {@link Function} to resolve a {@link ModuleDescriptor} for a {@link Requires} dependency.
     */
    @FunctionalInterface
    interface Resolver
        extends Function<Requires, Exceptional<ModuleDescriptor>> {

        /**
         * Attempts to acquire the {@link ModuleDescriptor} represented by the specified {@link Requires}.
         *
         * @param requires the {@link Requires}
         * @return the {@link Exceptional} {@link ModuleDescriptor}
         */
        @Override
        Exceptional<ModuleDescriptor> apply(Requires requires);

        /**
         * Creates a {@link ModuleDescriptor.Resolver} using the provided {@link ModuleCatalog} to determine
         * {@link Artifact}s, the {@link ModuleVersioning} to resolve module versions and the {@link Artifact.Resolver} to
         * resolve the said {@link Artifact}s.
         *
         * @param resolver the {@link Artifact.Resolver}
         * @param catalog the {@link ModuleCatalog}
         * @param versioning the {@link ModuleVersioning}
         *
         * @return a new {@link ModuleDescriptor.Resolver}
         */
        static Resolver create(final Artifact.Resolver resolver,
                               final ModuleCatalog catalog,
                               final ModuleVersioning versioning) {

            Objects.requireNonNull(resolver, "The Artifact.Resolver must not be null");
            Objects.requireNonNull(catalog, "The ModuleCatalog must not be null");
            Objects.requireNonNull(versioning, "The Versioning must not be null");

            return requires -> Exceptional.ofOptional(requires.version()
                    .map(__ -> requires.reference())
                    .flatMap(catalog::getArtifact))
                .flatMap(artifact -> resolver.getModuleDescriptor(artifact, catalog, versioning));
        }
    }

    /**
     * An internal immutable implementation of a {@link ModuleDescriptor}.
     */
    class Implementation
        implements ModuleDescriptor {

        private final ModuleReference reference;
        private final Optional<URI> location;
        private final EnumSet<Modifier> modifiers;
        private final ArrayList<Requires> requires;
        private final ArrayList<Provides> provides;
        private final ArrayList<String> uses;
        private final ArrayList<Exports> exports;
        private final ArrayList<Opens> opens;
        private final ArrayList<String> annotations;

        /**
         * Constructs a {@link ModuleDescriptor} {@link Implementation}.
         */
        private Implementation(final String name,
                               final Optional<URI> location,
                               final Optional<Version> version,
                               final EnumSet<Modifier> modifiers,
                               final Stream<Requires> requires,
                               final Stream<Provides> provides,
                               final Stream<String> uses,
                               final Stream<Exports> exports,
                               final Stream<Opens> opens,
                               final Stream<String> annotations) {

            this.reference = ModuleReference.of(name, version);
            this.location = location == null ? Optional.empty() : location;
            this.modifiers = modifiers;
            this.requires = requires.collect(Collectors.toCollection(ArrayList::new));
            this.provides = provides.collect(Collectors.toCollection(ArrayList::new));
            this.uses = uses.collect(Collectors.toCollection(ArrayList::new));
            this.exports = exports.collect(Collectors.toCollection(ArrayList::new));
            this.opens = opens.collect(Collectors.toCollection(ArrayList::new));
            this.annotations = annotations.collect(Collectors.toCollection(ArrayList::new));
        }

        @Override
        public String name() {
            return this.reference.name();
        }

        @Override
        public Optional<URI> location() {
            return this.location;
        }

        @Override
        public ModuleReference reference() {
            return this.reference;
        }

        @Override
        public boolean is(final Modifier modifier) {
            return this.modifiers.contains(modifier);
        }

        @Override
        public Stream<Modifier> modifiers() {
            return this.modifiers.stream();
        }

        @Override
        public Stream<Requires> requires() {
            return this.requires.stream();
        }

        @Override
        public Optional<Version> version() {
            return this.reference.version();
        }

        @Override
        public Stream<Provides> provides() {
            return this.provides.stream();
        }

        @Override
        public Stream<String> uses() {
            return this.uses.stream();
        }

        @Override
        public Stream<Exports> exports() {
            return this.exports.stream();
        }

        @Override
        public Stream<Opens> opens() {
            return this.opens.stream();
        }

        @Override
        public Stream<String> annotations() {
            return this.annotations.stream();
        }

        @Override
        public String toString() {
            final String INDENT = "    ";

            final StringBuilder builder = new StringBuilder();
            if (isOpen()) {
                builder.append("open ");
            }

            builder.append("module ");

            builder.append(name());
            builder.append(" {\n");

            // output the "requires"
            requires().forEach(require -> {
                builder.append(INDENT);
                builder.append("requires ");

                if (require.isStatic()) {
                    builder.append("static ");
                }

                if (require.isTransitive()) {
                    builder.append("transitive ");
                }

                builder.append(require.name());
                builder.append(";\n");
            });

            // output the "exports"
            exports().forEach(e -> {
                builder.append(INDENT);
                builder.append("exports ");
                builder.append(e.source());

                if (e.targets().findFirst().isPresent()) {
                    builder.append(" to ");
                    builder.append(e.targets().collect(Collectors.joining(", ")));
                }

                builder.append(";\n");
            });

            // output the "opens"
            opens().forEach(o -> {
                builder.append(INDENT);
                builder.append("opens ");
                builder.append(o.source());

                if (o.targets().findFirst().isPresent()) {
                    builder.append(" to ");
                    builder.append(o.targets().collect(Collectors.joining(", ")));
                }

                builder.append(";\n");
            });

            // output the "uses"
            uses().forEach(service -> {
                builder.append(INDENT);
                builder.append("uses ");
                builder.append(service);
                builder.append(";\n");
            });

            // output the "provides"
            provides().forEach(p -> {
                builder.append(INDENT);
                builder.append("provides ");
                builder.append(p.service());
                builder.append(" with ");
                builder.append(p.providers().collect(Collectors.joining(", ")));
                builder.append(";\n");
            });
            builder.append("}\n");

            return builder.toString();
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
            return this.reference.equals(that.reference)
                && this.modifiers.containsAll(that.modifiers) && that.modifiers.containsAll(this.modifiers)
                && this.requires.containsAll(that.requires) && that.requires.containsAll(this.requires)
                && this.provides.containsAll(that.provides) && that.provides.containsAll(this.provides)
                && this.uses.containsAll(that.uses) && that.uses.containsAll(this.uses)
                && this.exports.containsAll(that.exports) && that.exports.containsAll(this.exports)
                && this.opens.containsAll(that.opens) && that.opens.containsAll(this.opens);
        }

        @Override
        public int hashCode() {
            return this.reference.hashCode();
        }
    }
}
