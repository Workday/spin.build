package build.spin.engine;

/*-
 * #%L
 * Spin Engine
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

import build.base.commandline.CommandLineParser;
import build.base.configuration.Configuration;
import build.base.configuration.Default;
import build.base.configuration.Option;
import build.base.flow.Publisher;
import build.base.flow.Subscriber;
import build.base.flow.SubscriberRegistry;
import build.base.foundation.Introspection;
import build.base.option.JDKVersion;
import build.base.telemetry.Telemetry;
import build.base.telemetry.TelemetryRecorder;
import build.base.telemetry.TelemetryRecorderFactory;
import build.codemodel.dependency.injection.Binder;
import build.codemodel.dependency.injection.ConfigurationResolver;
import build.codemodel.dependency.injection.Context;
import build.codemodel.dependency.injection.DefaultOptionResolver;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.Module;
import build.codemodel.dependency.injection.OptionalResolver;
import build.codemodel.dependency.injection.QualifiedResolver;
import build.codemodel.dependency.injection.Resolver;
import build.codemodel.dependency.injection.SystemPropertyResolver;
import build.codemodel.dependency.injection.ValueBinding;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.TypeUsages;
import build.spawn.platform.local.LocalMachine;
import build.spin.Cache;
import build.spin.Engine;
import build.spin.Extension;
import build.spin.Plugin;
import build.spin.Program;
import build.spin.Project;
import build.spin.Resource;
import build.spin.Service;
import build.spin.SpinURI;
import build.spin.Workspace;
import build.spin.annotation.Bootstrap;
import build.spin.common.DefaultProgram;
import build.spin.common.injection.ProjectResourceResolver;
import build.spin.common.telemetry.TelemetryPublisher;
import build.spin.common.util.Folders;
import build.spin.common.util.Invocables;
import build.spin.option.Verbose;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import static build.spin.common.util.Folders.isFolder;

/**
 * Provides {@link Project} discovery, inference and execution.
 *
 * @author brian.oliver
 * @since Nov-2019
 */
public final class DefaultEngine implements Engine {

    /**
     * The {@link TelemetryRecorder} for the {@link Engine}.
     */
    private final TelemetryRecorder recorder;

    /**
     * The {@link FileSystem} to use for {@link Project}s and {@link DefaultProgram}s.
     */
    private final FileSystem fileSystem;

    /**
     * The {@link InjectionFramework} for the {@link Engine}.
     */
    private final InjectionFramework framework;

    /**
     * The bootstrap {@link Configuration} for the {@link Engine}.
     */
    private final Configuration optionsByType;

    /**
     * The {@link Optional} {@link CommandLineParser}s to parse command-line {@link Option}s for {@link Extension}s.
     */
    private final Optional<CommandLineParser> commandLineParser;

    /**
     * The command line arguments.
     */
    private final String[] arguments;

    /**
     * The {@link SubscriberRegistry} for the {@link Engine}.
     */
    private final SubscriberRegistry<Telemetry> observers;

    /**
     * The {@link Context} to be used for injection by the {@link Engine}.
     */
    private final Context context;

    /**
     * The {@link Extension.MetaClass}s used by the {@link Engine}.
     */
    private final LinkedHashMap<Class<? extends Extension>, Extension.MetaClass> metaClasses;

    /**
     * The {@link Context}s used by each {@link Extension.MetaClass}.
     */
    private final LinkedHashMap<Class<? extends Extension>, Context> contexts;

    /**
     * The {@link Context}s in which each {@link Extension} instance was created, keyed by extension class.
     * Stored so {@link #close()} can invoke {@link build.codemodel.dependency.injection.PreDestroy} lifecycle methods.
     */
    private final LinkedHashMap<Class<? extends Extension>, Context> extensionContexts;

    /**
     * The {@link Service}s provided by the {@link Engine}.
     */
    private final LinkedHashMap<Class<? extends Service>, Service> services;

    /**
     * The simple names shared by more than one distinct {@link Plugin} {@link Class} discovered by this
     * {@link Engine} - see {@link #pluginDisplayName(Class)}.
     */
    private final Set<String> ambiguousPluginSimpleNames;

    /**
     * Constructs a new {@link Engine} with the specified {@link FileSystem} for {@link Project} discovery,
     * {@link ServiceLoader.Provider}s for {@link Extension.MetaClass}s, and {@link Configuration} to provide bootstrap
     * {@link Option}s.
     * <p>
     * Should a {@link FileSystem} not be provided, the {@link FileSystems#getDefault()} is assumed.
     * <p>
     * Should the {@link Configuration} not be provided, an empty {@link Configuration} will be used.
     *
     * @param classLoader          the {@link ClassLoader} to use for {@link Extension.MetaClass} discovery
     * @param fileSystem           the {@link FileSystem}
     * @param optionsByType        the bootstrap {@link Configuration}
     * @param commandLineParser    the {@link Optional} {@link CommandLineParser}
     * @param commandLineArguments the {@link Optional} command line arguments
     * @param observableConsumer   the {@link Optional} {@link Telemetry} {@link Publisher} {@link Consumer}
     */
    public DefaultEngine(final ClassLoader classLoader,
                         final FileSystem fileSystem,
                         final Configuration optionsByType,
                         final Optional<CommandLineParser> commandLineParser,
                         final Optional<String[]> commandLineArguments,
                         final Optional<Consumer<? super Publisher<? super Telemetry>>> observableConsumer) {

        Objects.requireNonNull(classLoader, "ClassLoader must not be null");

        this.framework = InjectionFramework.create();
        this.fileSystem = fileSystem == null ? FileSystems.getDefault() : fileSystem;
        this.optionsByType = optionsByType == null ? Configuration.empty() : optionsByType;

        this.commandLineParser = commandLineParser == null ? Optional.empty() : commandLineParser;

        final String[] EMPTY = new String[]{};
        this.arguments = commandLineArguments == null ? EMPTY : commandLineArguments.orElse(EMPTY);

        // establish TelemetryRecorder
        final URI uri = SpinURI.create("engine", this.getClass().getSimpleName());
        this.observers = new SubscriberRegistry<>();
        this.recorder = new TelemetryPublisher(uri, this::publish);

        if (observableConsumer != null) {
            observableConsumer.ifPresent(consumer -> consumer.accept(this.observers));
        }

        // establish the Dependency Injection context for the Engine
        final boolean verbose = this.optionsByType.getOptional(Verbose.class)
            .map(value -> value == Verbose.ENABLED)
            .orElse(false);

        this.context = this.framework.newContext(new EngineModule(this.framework, this.fileSystem, this.optionsByType,
            engineUri -> TelemetryPublisher.of(engineUri, this::publish, verbose)));

        // allow the @Default JDKVersion to be resolved for injection
        this.optionsByType.getOptional(JDKVersion.class)
            .ifPresent(version -> this.context.addResolver(QualifiedResolver.of(Default.class, version)));

        this.context.addResolver(SystemPropertyResolver.of(this.framework.codeModel()));
        this.context.addResolver(ConfigurationResolver.of(optionsByType));
        this.context.addResolver(DefaultOptionResolver.of(this.framework));

        this.metaClasses = new LinkedHashMap<>();
        this.contexts = new LinkedHashMap<>();
        this.extensionContexts = new LinkedHashMap<>();
        this.services = new LinkedHashMap<>();

        // allow Services to be resolved and injected
        final Resolver<Service> serviceResolver = dependency ->
            TypeUsages.getThreadContextClass(dependency.typeUsage())
                .flatMap(requiredClass -> this.services.values().stream()
                    .filter(service -> requiredClass.isAssignableFrom(service.getClass()))
                    .findFirst())
                .map(service -> ValueBinding.of(dependency, service));

        this.context.addResolver(serviceResolver);

        // discover and establish the @Bootstrap Service.MetaClasses that are
        // detected on the FileSystem
        // (and register those that are Resolvers with the Engine Context)
        ServiceLoader.load(Extension.MetaClass.class, classLoader).stream()
            .filter(provider -> Service.MetaClass.class.isAssignableFrom(provider.type()))
            .filter(provider -> provider.type().isAnnotationPresent(Bootstrap.class))
            .map(provider -> createExtensionMetaClass(provider.type(), this.context))
            .map(Service.MetaClass.class::cast).filter(metaClass -> metaClass.isDetectedIn(this.fileSystem))
            .map(metaClass -> createExtension(metaClass, this.contexts.get(metaClass.getExtensionClass())))
            .map(Service.class::cast).map(service -> this.services.put(service.getClass(), service))
            .filter(Resolver.class::isInstance).map(Resolver.class::cast).forEach(this.context::addResolver);

        // discover and establish the non-@Bootstrap Service.MetaClasses that
        // are detected on the FileSystem
        // (and register those that are Resolvers with the Engine Context)
        ServiceLoader.load(Extension.MetaClass.class, classLoader).stream()
            .filter(provider -> Service.MetaClass.class.isAssignableFrom(provider.type()))
            .filter(provider -> !provider.type().isAnnotationPresent(Bootstrap.class))
            .map(provider -> createExtensionMetaClass(provider.type(), this.context))
            .map(Service.MetaClass.class::cast).filter(metaClass -> metaClass.isDetectedIn(this.fileSystem))
            .map(metaClass -> createExtension(metaClass, this.contexts.get(metaClass.getExtensionClass())))
            .map(Service.class::cast).map(service -> this.services.put(service.getClass(), service))
            .filter(Resolver.class::isInstance).map(Resolver.class::cast).forEach(this.context::addResolver);

        // discover and establish the non-Service.MetaClasses
        ServiceLoader.load(Extension.MetaClass.class, classLoader).stream()
            .filter(provider -> !Service.MetaClass.class.isAssignableFrom(provider.type()))
            .forEach(provider -> createExtensionMetaClass(provider.type(), this.context));

        // determine, once, the simple names shared by more than one distinct Plugin discovered for this
        // invocation - see pluginDisplayName()
        this.ambiguousPluginSimpleNames = metaClasses(Plugin.MetaClass.class)
            .map(Plugin.MetaClass::getExtensionClass)
            .collect(Collectors.groupingBy(Class::getSimpleName, Collectors.counting()))
            .entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());

        // configure the provided CommandLineParse to include the
        // Extension.MetaClass command-line
        // options
        this.commandLineParser
            .ifPresent(parser -> metaClasses()
                .forEach(metaClass -> metaClass.getCommandLineClasses().forEach(parser::add)));

        this.context.validate().initializeEagerSingletons();
    }

    /**
     * Creates an {@link Extension.MetaClass} using the provided base {@link Context} given the {@link Class} of
     * {@link Extension.MetaClass}.
     *
     * @param <T>                     the type of {@link Extension.MetaClass}
     * @param extensionMetaClassClass the {@link Class} of {@link Extension.MetaClass}
     * @param context                 the base {@link Context}
     * @return a new {@link Extension.MetaClass}
     */
    private <T extends Extension.MetaClass> T createExtensionMetaClass(
        final Class<T> extensionMetaClassClass,
        final Context context) {

        final URI classUri = SpinURI.create("class", extensionMetaClassClass.getSimpleName());

        final Context metaClassContext = context.newContext();
        metaClassContext.bind(TelemetryRecorder.class).to(new TelemetryPublisher(classUri, this::publish));
        metaClassContext.bind(Engine.class).to(this);
        metaClassContext.bind(Context.class).to(metaClassContext);

        // create the Extension.MetaClass
        final var metaClass = metaClassContext.create(extensionMetaClassClass);

        // register the Context used and for this Extension.MetaClass
        this.contexts.put(metaClass.getExtensionClass(), metaClassContext);

        // register the Extension.MetaClass for the Extension class
        this.metaClasses.put(metaClass.getExtensionClass(), metaClass);

        this.recorder.diagnostic("Detected %s %s",
            metaClass.scheme(),
            Introspection.describe(metaClass.getExtensionClass()).replace('$', '.'));

        return metaClass;
    }

    /**
     * Creates an {@link Extension} given its {@link Extension.MetaClass}, resolving commandline parameters and options
     * for injection, and injecting necessary dependencies.
     *
     * @param metaClass        the {@link Extension.MetaClass}
     * @param metaClassContext the {@link Extension.MetaClass} {@link Context} to use for creating the {@link Extension}
     * @return a new {@link Extension}
     */
    @SuppressWarnings("unchecked")
    private Extension createExtension(final Extension.MetaClass metaClass, final Context metaClassContext) {

        final Context extensionContext = metaClassContext.newContext();
        extensionContext.bind((Class<Extension.MetaClass>) metaClass.getClass()).to(metaClass);
        extensionContext.bind(TelemetryRecorder.class).to(new TelemetryPublisher(
            SpinURI.create(metaClass.scheme(), metaClass.getExtensionClass().getSimpleName()),
            this::publish));
        extensionContext.bind(Context.class).to(extensionContext);

        this.commandLineParser.ifPresent(parser -> {
            final Configuration parsedOptions = parser.parse(arguments).build();
            final Configuration serviceOptionsByType = Configuration.of(
                Stream.concat(this.optionsByType.stream(), parsedOptions.stream()).toArray(Option[]::new));
            extensionContext.bind(Configuration.class).to(serviceOptionsByType);
            extensionContext.addResolver(ConfigurationResolver.of(serviceOptionsByType));
        });

        final var extension = extensionContext.create(metaClass.getExtensionClass());
        this.extensionContexts.put(metaClass.getExtensionClass(), extensionContext);
        return extension;
    }

    @Override
    public void close() {
        this.extensionContexts.values().forEach(Context::close);
        this.contexts.values().forEach(Context::close);
        this.context.close();
    }

    @Override
    public InjectionFramework framework() {
        return this.framework;
    }

    @Override
    public Configuration options() {
        return this.optionsByType;
    }

    @Override
    public FileSystem fileSystem() {
        return this.fileSystem;
    }

    @Override
    public Context context() {
        return this.context;
    }

    @Override
    public Stream<Extension.MetaClass> metaClasses() {
        return this.metaClasses.values().stream();
    }

    @Override
    public <T extends Extension.MetaClass> Stream<T> metaClasses(final Class<T> metaClassClass) {
        return metaClassClass == null
            ? Stream.empty()
            : metaClasses().filter(metaClassClass::isInstance).map(metaClassClass::cast);
    }

    @Override
    public Stream<Service> services() {
        return this.services.values().stream();
    }

    @Override
    public String pluginDisplayName(final Class<? extends Plugin> pluginClass) {
        return this.ambiguousPluginSimpleNames.contains(pluginClass.getSimpleName())
            ? pluginClass.getName()
            : pluginClass.getSimpleName();
    }

    @Override
    public Optional<Path> getWorkspacePath(final Path path) {

        Path workspacePath = null;

        // ensure we start with a Folder path!
        Path current = isFolder(path) ? path : (path == null ? null : path.getParent());

        while (current != null) {
            final Path detectionPath = current;

            // determine if there's a Resource that considers the current Path
            // the Workspace
            // (terminate early when the Workspace is detected)
            if (metaClasses(Resource.MetaClass.class)
                .filter(metaClass -> metaClass.isWorkspace(detectionPath)).peek(
                    metaClass -> this.recorder.diagnostic("%s detected Workspace at %s",
                        Introspection.describe(metaClass.getClass()).replace('$', '.'),
                        detectionPath))
                .findFirst().isPresent()) {

                return Optional.of(detectionPath);
            }

            // are any Plugins detected in the path?
            // (if so the path *possibly* contains a Project, but we won't
            // really know until we
            // later create a project
            // from the Workspace)
            if (metaClasses(Plugin.MetaClass.class).anyMatch(detector -> detector.isDetectedIn(detectionPath))) {
                workspacePath = detectionPath;
            }

            current = current.getParent();
        }

        return Optional.ofNullable(workspacePath);
    }

    @Override
    public Program createProgram(final Project project, final Configuration optionsByType) {

        Objects.requireNonNull(project, "The project must not be null");

        // establish new Program options based on the Engine
        // with those specified for the Program as overrides
        final Configuration programOptions = Configuration.of(
            Stream.concat(this.optionsByType.stream(),
                    (optionsByType == null ? Configuration.empty() : optionsByType).stream())
                .toArray(Option[]::new));

        return new DefaultProgram(project, programOptions);
    }

    @Override
    public Program createProgram(final Project project, final Option... options) {
        return createProgram(project, Configuration.of(options));
    }

    @Override
    public void subscribe(final Subscriber<? super Telemetry> subscriber) {
        this.observers.subscribe(subscriber);
    }

    @Override
    public void publish(final Telemetry telemetry) {
        this.observers.publish(telemetry);
    }

    @Override
    public boolean complete() {
        return this.observers.complete();
    }

    @Override
    public boolean error(final Throwable throwable) {
        return this.observers.error(throwable);
    }

    @Override
    public Optional<Workspace> createWorkspace(final Path path) {

        return getWorkspacePath(path).flatMap(workspacePath -> createProject(Optional.empty(), workspacePath))
            .map(Workspace.class::cast);
    }

    /**
     * Attempts to discover and create a suitable {@link Project} by performing a depth first search of the file system
     * from the specified path.
     * <p>
     * Should the {@link Project} be a {@link Workspace} (ie: root-{@link Project}), a {@link Workspace} is returned.
     *
     * @param parent the optional parent of the {@link Project}
     * @param path   the Project {@link Path}
     * @return an {@link Optional} {@link Project}
     */
    /**
     * Core value bindings for the engine {@link Context}. Extracted as a {@link Module} so tests
     * can override individual bindings via {@link build.codemodel.dependency.injection.Modules#override}.
     */
    record EngineModule(InjectionFramework framework, FileSystem fileSystem, Configuration optionsByType,
                        TelemetryRecorderFactory telemetryRecorderFactory)
        implements Module {

        @Override
        public void configure(final Binder binder) {
            binder.bind(InjectionFramework.class).to(this.framework);
            binder.bind(FileSystem.class).to(this.fileSystem);
            binder.bind(CodeModel.class).to(new JDKCodeModel(new NonCachingNameProvider()));
            binder.bind(Configuration.class).to(this.optionsByType);
            binder.bind(LocalMachine.class).to(new LocalMachine(this.telemetryRecorderFactory));
            binder.bind(DocumentBuilderFactory.class).to(DocumentBuilderFactory.newInstance());
            binder.bind(Cache.class).to(HeapBasedCache::new);
        }
    }

    Optional<Project> createProject(final Optional<Project> parent, final Path path) {

        // ensure the Path is a Folder
        if (!isFolder(path)) {
            // we can only discover a Project for a Path that exists
            return Optional.empty();
        }

        final Optional<Project> createdProject;
        final Optional<Project> childParent;

        // is the specified path ignored in the project?
        if (parent.map(project -> project.isIgnored(path)).orElse(false)) {
            return Optional.empty();
        }

        // attempt to create a Project for the specified path if either the
        // engine:
        // 1. Resource.MetaClasses detect that the path is a Root Project, or
        // 2. Plugin.MetaClasses detect this the path is a Project
        if (metaClasses(Resource.MetaClass.class).anyMatch(metaClass -> metaClass.isWorkspace(path))
            || metaClasses(Plugin.MetaClass.class)
            .anyMatch(metaClass -> metaClass.isDetectedIn(path))) {

            final Resolver<Optional<Project>> parentResolver =
                parent.map(parentProject -> OptionalResolver.of(Project.class, parentProject))
                    .orElseGet(() -> OptionalResolver.empty(Project.class));

            final Context context = this.framework.newContext(binder -> {
                binder.bind(Engine.class).to(this);
                binder.bind(Path.class).to(path);
            });
            context.addResolver(context().resolver());
            context.addResolver(parentResolver);

            final AbstractProject project =
                parent.isEmpty() ? context.create(DefaultWorkspace.class) : context.create(DefaultProject.class);

            context.bind(Project.class).to(project);
            context.bind(Workspace.class).to(project.workspace());

            // establish Resources for the project (allowing them to be injected
            // into the Plugins). ProjectResourceResolver must not be registered until after this
            // loop: it resolves a requested Resource type from the Project hierarchy, and since
            // this Project's own Resources haven't been attached to project.resources yet, adding
            // it earlier would let it silently satisfy a Resource-creation request (eg: creating
            // this Project's own ConfigurationResource) with an ancestor's already-created instance
            // of the same type instead of actually constructing this Project's own.
            metaClasses(Resource.MetaClass.class)
                .filter(metaClass -> metaClass.isWorkspace(path) || metaClass.isDetectedIn(project))
                .map(metaClass -> createExtension(metaClass, context)).map(Resource.class::cast)
                .forEach(resource -> project.resources.put(resource.getClass(), resource));

            context.addResolver(new ProjectResourceResolver(project));

            // establish the Plugins for the project
            metaClasses(Plugin.MetaClass.class).filter(metaClass -> metaClass.isDetectedIn(path))
                .map(metaClass -> createExtension(metaClass, context)).map(Plugin.class::cast)
                .forEach(plugin -> project.plugins.put(plugin.getClass(), plugin));

            // allow Plugins to be discovered based on the Project
            metaClasses(Plugin.MetaClass.class).filter(metaClass -> metaClass.isDetectedIn(project))
                .filter(metaClass -> !project.plugins.containsKey(metaClass.getExtensionClass()))
                .map(metaClass -> createExtension(metaClass, context)).map(Plugin.class::cast)
                .forEach(plugin -> project.plugins.put(plugin.getClass(), plugin));

            // determine the available Invocables for the Project based on the
            // Plugins
            // and any Invocables the Plugin wants to add itself
            project.plugins.values().stream()
                .flatMap(plugin -> Stream.concat(Invocables.stream(project, plugin), plugin.invocables()))
                .forEach(invocable -> project.invocables.put(invocable.getReference(), invocable));

            // add the project to the parent
            parent.ifPresent(p -> ((AbstractProject) p).children.add(project));

            createdProject = Optional.of(project);
            childParent = Optional.of(project);
        } else {
            createdProject = Optional.empty();
            childParent = parent;
        }

        // discover the potential projects based on child folders and add them
        // to the project
        try (Stream<Path> paths = Files.list(path)) {
            paths.filter(Folders::isFolder)
                .filter(child -> !isBuildOutputDirectory(child))
                .filter(child -> childParent.map(project -> !project.isIgnored(child)).orElse(true))
                .forEach(child -> createProject(childParent, child));
        } catch (final IOException e) {
            throw new RuntimeException("Failed to read child projects", e);
        }

        return createdProject;
    }

    /**
     * Gradle's default build-script filenames — evidence, alongside a {@code build/} sibling
     * directory name, that the directory is actually Gradle's own output rather than a
     * coincidentally-named directory (e.g. a Java package segment such as
     * {@code src/main/java/build/...}).
     */
    private static final Set<String> GRADLE_BUILD_SCRIPT_FILENAMES =
        Set.of("build.gradle", "build.gradle.kts");

    /**
     * Determines whether {@code path} is a known build-tool output directory (Maven's
     * {@code target/}, Gradle's {@code build/}) that must never be recursed into during project
     * discovery.
     * <p>
     * Without this, anything a build tool copies or writes into its own output directory — e.g.
     * Maven's resource plugin copying {@code src/test/resources} verbatim into
     * {@code target/test-classes}, including any nested {@code module-info.java} test fixtures —
     * gets independently rediscovered by {@link #createProject} as a second, distinct candidate
     * project. Unlike spin's own {@code .build/} output (already excluded because {@link Folders#isFolder}
     * skips hidden directories), Maven/Gradle output directories aren't hidden, so this exclusion is
     * required for those specifically.
     * <p>
     * Matching is deliberately not by directory name alone: {@code target} and, especially,
     * {@code build} are common enough as ordinary directory names — including as a Java package
     * segment, e.g. this very codebase's own {@code src/main/java/build/...} — that a bare name
     * match would prune real source trees from discovery. A directory only counts as build-tool
     * output when its parent also carries the corresponding build tool's own marker file
     * ({@code pom.xml} for {@code target/}, a Gradle build script for {@code build/}), i.e. the
     * directory actually sits where that build tool would place its output.
     *
     * @param path the candidate child {@link Path}
     * @return {@code true} if {@code path} is build-tool output for a build tool detected in its parent
     */
    static boolean isBuildOutputDirectory(final Path path) {
        final Path parent = path.getParent();

        return switch (path.getFileName().toString()) {
            case "target" -> Files.exists(parent.resolve("pom.xml"));
            case "build" -> GRADLE_BUILD_SCRIPT_FILENAMES.stream()
                .anyMatch(scriptName -> Files.exists(parent.resolve(scriptName)));
            default -> false;
        };
    }
}
