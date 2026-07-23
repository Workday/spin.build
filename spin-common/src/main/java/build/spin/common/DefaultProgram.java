package build.spin.common;

/*-
 * #%L
 * Spin Common Library
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

import build.base.configuration.Configuration;
import build.base.foundation.Capture;
import build.base.foundation.Introspection;
import build.base.foundation.UniformResource;
import build.base.graph.Graph;
import build.base.graph.GraphCycles;
import build.base.telemetry.Activity;
import build.base.telemetry.Meter;
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.dependency.injection.Binder;
import build.codemodel.dependency.injection.ConfigurationResolver;
import build.codemodel.dependency.injection.Context;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.Module;
import build.codemodel.dependency.injection.ProvidesResolver;
import build.codemodel.dependency.injection.Resolver;
import build.codemodel.dependency.injection.ValueBinding;
import build.codemodel.jdk.TypeUsages;
import build.spin.AssetCache;
import build.spin.Engine;
import build.spin.Instruction;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Program;
import build.spin.ProgramExecutionException;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.Workspace;
import build.spin.annotation.PostProcess;
import build.spin.annotation.PreProcess;
import build.spin.common.injection.FromResolver;
import build.spin.common.injection.ProjectResourceResolver;
import build.spin.common.telemetry.TelemetryPublisher;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static build.base.foundation.Introspection.describe;

/**
 * The default implementation of a {@link Program}.
 *
 * @author brian.oliver
 * @since Nov-2019
 */
public final class DefaultProgram
    implements Program {

    /**
     * The {@link TelemetryRecorder} for the {@link Engine}.
     */
    private final TelemetryRecorder recorder;

    /**
     * The {@link Engine} that created the {@link Program}.
     */
    private final Engine engine;

    /**
     * The {@link Configuration} for the {@link Program}.
     */
    private final Configuration optionsByType;

    /**
     * The {@link DefaultInstruction} {@link Instruction}s by {@link Reference}.
     */
    private final LinkedHashMap<Reference, DefaultInstruction<?>> instructions;

    /**
     * The task dependency graph: edge A → B means "A depends on B".
     */
    private final Graph<Reference> dependencyGraph;

    /**
     * A cycle detected in the task dependency graph, if any.
     * Non-empty means {@link #execute} must throw immediately.
     */
    private final List<Reference> detectedCycle;

    /**
     * The {@link Context} for the {@link Program}.
     */
    private final Context context;

    /**
     * The {@link InjectionFramework} from the {@link Engine}.
     */
    private final InjectionFramework framework;

    /**
     * Constructs a {@link DefaultProgram} for the specified {@link Project} with the provided {@link Configuration}.
     *
     * @param project       the {@link Project}
     * @param optionsByType the {@link Configuration}
     */
    @SuppressWarnings("unchecked")
    public DefaultProgram(final Project project,
                          final Configuration optionsByType) {

        Objects.requireNonNull(project, "The project must not be null");

        this.engine = project.engine();
        this.framework = this.engine.framework();
        this.optionsByType = optionsByType == null ? Configuration.empty() : optionsByType;
        this.instructions = new LinkedHashMap<>();

        // establish TelemetryRecorder
        final URI uri = UniformResource.createURI("program", this);
        this.recorder = new TelemetryPublisher(uri, this.engine::publish);

        this.context = this.framework.newContext(new ProgramModule(this.optionsByType, project.workspace()));
        this.context.addResolver(ConfigurationResolver.of(this.optionsByType));
        this.context.addResolver(this.engine.context().resolver());
        this.context.bind(Program.class).to(this);
        this.context.validate();

        // determine the Task.Pattern
        final Task.Pattern pattern = this.optionsByType
            .getOptional(Task.Pattern.class)
            .orElseThrow(() -> new RuntimeException("Failed to define a Task.Pattern"));

        // TODO: prepare the plugins for each of the Projects?

        final Activity inference = this.recorder.commence("Creating Program for Tasks Matching [%s]", pattern.get());

        // the tasks for which we need to determine an Executable
        final Stack<Invocable<?>> stack = new Stack<>();

        // walk the Project tree to determine the Tasks to be executed (based on their name/regex)
        project.walk(prj -> prj.invocables()
            .filter(invocable -> invocable.matches(pattern))
            .forEach(stack::push));

        // we keep track of the Projects and the Projects that their Tasks depend upon (not including themselves)
        // (this allows us to create a Project-based Dependency Graph)
        final HashMap<Project, HashSet<Project>> projects = new HashMap<>();

        // we've not yet included the @Automatic Tasks
        boolean includedAutomaticTasks = false;

        this.recorder.info("Found %d Task(s)", stack.size());

        final Activity creatingInstructions = this.recorder
            .commence("Creating Instructions for [%s]", project.name());

        final AtomicInteger nextExecutableIdentity = new AtomicInteger(1);

        while (!stack.isEmpty()) {
            final Invocable<?> taskInvocable = stack.peek();
            final Reference taskReference = taskInvocable.getReference();
            final Project taskProject = taskInvocable.getProject();
            final Plugin taskPlugin = taskInvocable.getPlugin();

            final int size = stack.size();

            // add the current task as a known tasks (ignored if already known)
            this.instructions.computeIfAbsent(taskReference, __ -> {
                // include the Project in the Projects being tracked
                projects.computeIfAbsent(taskProject, existing -> new HashSet<>());

                // establish a TelemetryRecorder for the publishing Task specific Telemetry
                final URI taskURI = taskInvocable.getURI();
                final TelemetryPublisher publisher = new TelemetryPublisher(taskURI, this.engine::publish);

                final Context taskContext = this.framework.newContext(new TaskContextModule(taskProject, publisher));
                taskContext.addResolver(ProvidesResolver.of(taskPlugin, this.framework));

                // add a Resolver for Iterable of Plugins implementing the specified interface
                taskContext.addResolver(injectionPoint -> {
                    if (TypeUsages.getThreadContextClass(injectionPoint.typeUsage())
                        .map(Iterable.class::equals).orElse(false)) {

                        return TypeUsages.getFirstTypeParameterClass(injectionPoint.typeUsage())
                            .map(c -> {
                                final Iterable<Plugin> iterable = () -> taskProject
                                    .plugins()
                                    .filter(c::isInstance)
                                    .iterator();
                                return ValueBinding.of(injectionPoint, iterable);
                            });
                    }
                    return Optional.empty();
                });

                // add a Resolver to allow Resolving of the Executables for the Program
                taskContext.addResolver(injectionPoint -> {
                    if (TypeUsages.getThreadContextClass(injectionPoint.typeUsage())
                        .map(Stream.class::equals).orElse(false)) {
                        // TODO: we're assuming it's a Stream<Instruction>
                        return Optional.of(ValueBinding.of(injectionPoint, this.instructions.values().stream()));
                    }
                    return Optional.empty();
                });

                // bind the interfaces implemented by the Plugin
                taskContext.bind(taskPlugin).asAllInterfaces();

                // allow project resources that are resolvers to resolve
                taskContext.addResolver(dependency -> project.resources()
                    .filter(Resolver.class::isInstance)
                    .map(r -> (Resolver<Object>) r)
                    .flatMap(resolver -> resolver.resolve(dependency).stream())
                    .findFirst());

                // allow the Program to resolve InjectionPoints
                taskContext.addResolver(this.context.resolver());

                // allow project resources to be resolved and injected — registered last since it always
                // resolves an Optional<X>-typed dependency, so other resolvers must get first refusal
                taskContext.addResolver(new ProjectResourceResolver(taskProject));

                this.recorder.diagnostic("Creating Instruction [%s] defined by [%s] for [%s]",
                    describe(taskInvocable.getTaskClass()),
                    taskInvocable.getPlugin().name(),
                    taskInvocable.getProject().name());

                // create the Executable Instruction
                final DefaultInstruction<?> instruction = new DefaultInstruction<>(
                    nextExecutableIdentity.getAndIncrement(),
                    taskInvocable,
                    publisher,
                    taskContext);

                // queue dependencies of the current task (that aren't already known) so we can create
                // Executables for them.

                // we don't create Executables for codependencies as they'll be executed as part of an Executable
                // we do however include the dependencies of the codependencies
                // (ie: implied transitive dependencies)

                // we also include all non-codependent Tasks in the Project that happen @After this task
                // (as they are triggered by the presence of this Task)
                Stream.concat(
                        instruction.codependencies().flatMap(Invocable::dependencies),
                        Stream.concat(instruction.dependencies(),
                            taskProject.invocables()
                                .filter(defn -> !defn.isCodependency() && defn.isAfter(taskReference))
                                .map(Invocable::getReference)))
                    .filter(r -> !this.instructions.containsKey(r))
                    .peek(r -> {
                        //include the dependency for the project (when it's not itself)
                        if (taskProject != r.project()) {
                            projects.get(taskProject).add(r.project());
                        }
                    })
                    .forEach(r -> r.project().invocables()
                        .filter(d -> d.getReference().equals(r))
                        .findFirst()
                        .ifPresent(stack::push));

                return instruction;
            });

            // remove the current task when all of its dependencies (if any) are processed (ie: known)
            if (stack.size() == size) {
                stack.pop();
            }

            // when the Stack is empty, introduce the automatic Tasks that haven't been included yet
            if (stack.isEmpty() && !includedAutomaticTasks) {
                project.walk(prj -> prj.invocables()
                    .filter(definition -> definition.isAutomatic() && !definition.isCodependency())
                    .filter(definition -> !this.instructions.containsKey(definition.getReference()))
                    .forEach(stack::push));

                includedAutomaticTasks = true;
            }
        }

        creatingInstructions.complete();

        // build the dependency graph: edge A → B means "A depends on B"
        final Graph.Builder<Reference> graphBuilder = Graph.directed();
        this.instructions.keySet().forEach(graphBuilder::addVertex);
        this.instructions.values().forEach(instruction ->
            instruction.dependencies()
                .filter(this.instructions::containsKey)
                .forEach(dep -> graphBuilder.addEdge(instruction.getReference(), dep)));
        final Graph<Reference> dependencyGraph = graphBuilder.build();

        this.dependencyGraph = dependencyGraph;
        this.detectedCycle = GraphCycles.findCycle(dependencyGraph).orElse(List.of());

        inference.complete();

        this.recorder.diagnostic("Instructions for %s are as follows (not ordered)", project.name());

        this.instructions.values()
            .forEach(instruction -> {
                this.recorder.diagnostic("Instruction #%d: %s", instruction.getIdentity(), instruction.getReference());
                instruction.dependencies()
                    .forEach(reference -> this.recorder.diagnostic("   requires %s", reference));

                instruction.codependencies()
                    .filter(definition ->
                        Introspection.hasDeclaredAnnotation(definition.getTaskClass(), PreProcess.class))
                    .forEach(definition ->
                        this.recorder.diagnostic("   pre-processed by %s", definition.getReference()));

                instruction.codependencies()
                    .filter(definition ->
                        Introspection.hasDeclaredAnnotation(definition.getTaskClass(), PostProcess.class))
                    .forEach(definition ->
                        this.recorder.diagnostic("   post-processed by %s", definition.getReference()));
            });

        this.recorder.diagnostic("Project-level Dependencies:");

        projects.forEach((prj, deps) -> {
            if (deps.isEmpty()) {
                this.recorder.diagnostic("Project %s has no dependencies", prj.name());
            } else {
                this.recorder.diagnostic("Project %s", prj.name());
                deps.forEach(dep -> this.recorder.diagnostic("   requires %s", dep.name()));
            }
        });
    }

    record ProgramModule(Configuration options, Workspace workspace) implements Module {

        @Override
        public void configure(final Binder binder) {
            binder.bind(Configuration.class).to(this.options);
            binder.bind(Workspace.class).to(this.workspace);
        }
    }

    record TaskContextModule(Project project, TelemetryPublisher recorder) implements Module {

        @Override
        public void configure(final Binder binder) {
            binder.bind(Workspace.class).to(this.project.workspace());
            binder.bind(Project.class).to(this.project);
            binder.bind(Path.class).to(this.project.path());
            binder.bind(TelemetryRecorder.class).to(this.recorder);
        }
    }

    @Override
    public AssetCache execute(final AssetCache cache) throws ProgramExecutionException {

        if (!this.detectedCycle.isEmpty()) {
            throw new ProgramExecutionException(this, this.detectedCycle.get(0),
                "Cyclic task dependency detected: " + this.detectedCycle);
        }

        final Meter execution = this.recorder.commence(this.instructions.size(), "Executing Program Tasks");

        final AssetCache localCache = DefaultAssetCache.create();
        final AssetCache executionCache = NearAssetCache.of(localCache, cache);

        // pending[ref] = number of direct dependencies not yet completed
        final var pending = new ConcurrentHashMap<Reference, AtomicInteger>();
        this.instructions.keySet().forEach(ref ->
            pending.put(ref, new AtomicInteger(this.dependencyGraph.successors(ref).size())));

        final Queue<ProgramExecutionException> failures = new ConcurrentLinkedQueue<>();

        // guards against a task being dispatched more than once when multiple dependents
        // complete concurrently and both decrement pending to zero for the same reference
        final var dispatched = ConcurrentHashMap.<Reference>newKeySet();

        try (var scope = StructuredTaskScope.open()) {
            // seed with tasks that have no dependencies
            this.instructions.keySet().stream()
                .filter(ref -> pending.get(ref).get() == 0)
                .forEach(ref -> {
                    // fail-fast: once any task has failed, stop starting new ones — tasks
                    // already in flight are left to finish, but nothing new gets dispatched
                    if (failures.isEmpty() && dispatched.add(ref)) {
                        scope.fork(() -> runTask(ref, pending, dispatched, executionCache, execution, failures));
                    }
                });

            scope.join();
        } catch (final StructuredTaskScope.FailedException e) {
            // runTask no longer propagates failures to the scope; this branch guards against
            // unexpected internal errors only
            throw new ProgramExecutionException(this, this.instructions.keySet().iterator().next(),
                "Unexpected failure during task execution", e.getCause());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Program execution interrupted", e);
        }

        if (!failures.isEmpty()) {
            final ProgramExecutionException first = failures.poll();
            failures.forEach(first::addSuppressed);
            throw first;
        }

        execution.complete();
        return localCache;
    }

    private static String extractOutput(final Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ProcessFailedException p && !p.output().isEmpty()) {
                return p.output();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Void runTask(final Reference reference,
                         final ConcurrentHashMap<Reference, AtomicInteger> pending,
                         final Set<Reference> dispatched,
                         final AssetCache executionCache,
                         final Meter execution,
                         final Queue<ProgramExecutionException> failures) {

        if (!executionCache.contains(reference)) {
            final DefaultInstruction<?> instruction = this.instructions.get(reference);
            final Invocable<?> invocable = instruction.getInvocable();
            final Task<?> task = instruction.getTask();
            final Activity activity = this.recorder.commence("Executing %s", invocable);

            final Context executionContext = this.framework.newContext();
            executionContext.addResolver(new FromResolver(this.recorder, instruction, executionCache));
            executionContext.addResolver(instruction.getContext().resolver());

            try {
                instruction.codependencies()
                    .filter(codependency -> codependency.getTaskClass().isAnnotationPresent(PreProcess.class))
                    .forEach(codependency -> {
                        final Task<?> preprocessor = codependency.createTask(executionContext);
                        preprocessor.execute(codependency, executionContext, this.framework);
                    });

                final Object initialResult = task.execute(invocable, executionContext, this.framework);

                // create a Capture for the Result to allow injection and re-definition by PostProcessors
                final Capture<Object> capture = Capture.ofNullable(initialResult);

                // allow PostProcessors to inject the current task's result before it's committed to the cache:
                //   @From(CurrentTask.class) Capture<T> — mutable; PostProcessor can change the result
                //   @From(CurrentTask.class) T          — read-only view of the initial result
                executionContext.addResolver(dependency ->
                    FromResolver.taskClass(dependency)
                        .filter(c -> c.isAssignableFrom(task.getClass()))
                        .flatMap(__ -> TypeUsages.getThreadContextClass(dependency.typeUsage()))
                        .flatMap(requiredClass -> {
                            if (Capture.class.equals(requiredClass)) {
                                return TypeUsages.getFirstTypeParameterClass(dependency.typeUsage())
                                    .flatMap(t -> invocable.getTaskResultClass().filter(t::isAssignableFrom))
                                    .map(__ -> ValueBinding.of(dependency, (Object) capture));
                            }
                            if (initialResult != null && requiredClass.isInstance(initialResult)) {
                                return Optional.of(ValueBinding.of(dependency, initialResult));
                            }
                            return Optional.empty();
                        }));

                instruction.codependencies()
                    .filter(codependency -> codependency.getTaskClass().isAnnotationPresent(PostProcess.class))
                    .forEach(codependency -> {
                        final Task<?> postprocessor = codependency.createTask(executionContext);
                        postprocessor.execute(codependency, executionContext, this.framework);
                    });

                final Object taskResult = capture.isPresent() ? capture.get() : null;
                activity.complete(taskResult);
                execution.progress();

                executionCache.putIfAbsent(taskResult == null
                    ? VoidAsset.create(invocable)
                    : DefaultAsset.create((Invocable<Object>) invocable, taskResult));
            } catch (final Exception e) {
                activity.completeExceptionally(e);
                final String output = extractOutput(e);
                failures.add(new ProgramExecutionException(
                    this, reference,
                    output.isEmpty() ? "Failed to execute " + invocable
                                     : "Failed to execute " + invocable + "\n" + output,
                    ProcessFailedException.unwrap(e)));
                return null; // dependents are not fired when a task fails
            }
        }

        // fire any dependents whose last dependency just completed;
        // each task owns a fresh nested scope — fork() requires the calling thread to be the scope owner
        final var readyDependents = this.dependencyGraph.predecessors(reference).stream()
            .filter(this.instructions::containsKey)
            .filter(dependent -> pending.get(dependent).decrementAndGet() == 0)
            .toList();

        if (!readyDependents.isEmpty() && failures.isEmpty()) {
            try (var nestedScope = StructuredTaskScope.open()) {
                readyDependents.stream()
                    .filter(_ -> failures.isEmpty())
                    .filter(dispatched::add)
                    .forEach(dep -> nestedScope.fork(() ->
                        runTask(dep, pending, dispatched, executionCache, execution, failures)));
                nestedScope.join();
            } catch (final StructuredTaskScope.FailedException ignored) {
                // runTask records failures without throwing; this branch is unreachable in normal operation
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return null;
    }
}
