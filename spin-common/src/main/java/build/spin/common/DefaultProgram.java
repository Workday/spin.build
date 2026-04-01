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
import build.base.telemetry.Activity;
import build.base.telemetry.Meter;
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.injection.ConfigurationResolver;
import build.codemodel.injection.Context;
import build.codemodel.injection.InjectionFramework;
import build.codemodel.injection.ProvidesResolver;
import build.codemodel.injection.Resolver;
import build.codemodel.injection.ValueBinding;
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
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Stack;
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

        // establish a Context for the Program
        this.context = this.framework.newContext();

        // allow the Program Configuration to be injected
        this.context.bind(Configuration.class).to(optionsByType);

        // allow the Program Configuration to be resolved
        this.context.addResolver(ConfigurationResolver.of(optionsByType));

        // allow the Engine to resolve InjectionPoints as a fallback
        // (this allows injection of Services)
        this.context.addResolver(this.engine.context().resolver());

        // allow the Workspace to be injected
        this.context.bind(Workspace.class).to(project.workspace());

        // allow the Program to be injected
        this.context.bind(Program.class).to(this);

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

                // establish a Context allow for injection of common values (not for Task Execution)
                final Context taskContext = this.framework.newContext();

                // allow the Plugin to provide Injectable values for injection into Tasks
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

                // allows the Workspace to be injected into Tasks
                taskContext.bind(Workspace.class).to(taskProject.workspace());

                // allow the Project to be injected into Tasks
                taskContext.bind(Project.class).to(taskProject);

                // allow the Project Path to be injected into Tasks
                taskContext.bind(Path.class).to(taskProject.path());

                // bind the interfaces implemented by the Plugin
                Introspection.getAll(taskPlugin.getClass(), Class::getInterfaces)
                    .forEach(definedInterface ->
                        taskContext.bind((Class) definedInterface).to(definedInterface.cast(taskPlugin)));

                // establish a TelemetryRecorder for the publishing Task specific Telemetry
                final URI taskURI = taskInvocable.getURI();
                final TelemetryPublisher publisher = new TelemetryPublisher(taskURI, this.engine::publish);
                taskContext.bind(TelemetryRecorder.class).to(publisher);

                // allow project resources to be resolved and injected
                taskContext.addResolver(new ProjectResourceResolver(taskProject));

                // allow project resources that are resolvers to resolve
                taskContext.addResolver(dependency -> project.resources()
                    .filter(Resolver.class::isInstance)
                    .map(r -> (Resolver<Object>) r)
                    .flatMap(resolver -> resolver.resolve(dependency).stream())
                    .findFirst());

                // allow the Program to resolve InjectionPoints
                taskContext.addResolver(this.context.resolver());

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

        // add the dependencies as dependents
        this.instructions.values()
            .forEach(instruction -> instruction.dependencies()
                .map(this.instructions::get)
                .filter(Objects::nonNull)  // the dependency may not exist in the project, so we skip it (that's ok)
                .forEach(dependent -> dependent.addDependent(instruction.getReference())));

        // TODO: validate instructions (ensure no cyclic dependencies)

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

    @Override
    @SuppressWarnings("unchecked")
    public synchronized AssetCache execute(final AssetCache cache)
        throws ProgramExecutionException {

        final Meter execution = this.recorder.commence(this.instructions.size(), "Executing Program Tasks");

        // establish the ExecutionCache to cache results for this Program
        final AssetCache localCache = DefaultAssetCache.create();

        // establish the ExecutionCache to use for resolving values
        final AssetCache executionCache = NearAssetCache.of(localCache, cache);

        // establish the remaining Executables to execute
        final HashSet<Reference> remaining = new HashSet<>(this.instructions.keySet());

        // establish the execution queue of Executables to execute
        final Queue<DefaultInstruction<?>> queue = new LinkedList<>();

        // queue the initial Executables to execute (those with no dependencies)
        this.instructions.values().stream()
            .filter(instruction -> !instruction.hasDependencies())
            .forEach(queue::add);

        while (!queue.isEmpty()) {

            // obtain the next Executable to execute from the queue
            final DefaultInstruction<?> instruction = queue.poll();

            // obtain the Reference and Project for the Task from the Instruction (we'll need these a lot)
            final Reference reference = instruction.getReference();
            final Project project = instruction.getProject();

            final Invocable<?> invocable = instruction.getInvocable();
            final Task<?> task = instruction.getTask();

            // execute the Task iff we haven't done so already
            if (executionCache.contains(reference)) {
                // remove the Task from the remaining Tasks
                remaining.remove(reference);

                // queue the dependents that are ready for execution
                instruction.dependents()
                    .filter(remaining::contains)
                    .map(this.instructions::get)
                    .filter(dependent -> dependent.dependencies().noneMatch(remaining::contains))
                    .forEach(queue::add);
            } else {
                // attempt to execute the Task
                final Activity activity = this.recorder.commence("Executing %s", invocable);

                // establish a Context for the execution of the Task
                final Context executionContext = this.framework.newContext();

                // include a Resolver for @From injection points
                executionContext.addResolver(new FromResolver(this.recorder, instruction, executionCache));

                // include the Context used to create the Instruction
                executionContext.addResolver(instruction.getContext().resolver());

                try {
                    // execute the pre-processing co-dependencies
                    instruction.codependencies()
                        .filter(codependency -> codependency.getTaskClass().isAnnotationPresent(PreProcess.class))
                        .forEach(codependency -> {
                            final Task<?> preprocessor = codependency.createTask(executionContext);
                            preprocessor.execute(codependency, executionContext, this.framework);
                        });

                    // execute the Task
                    final Object initialResult = task.execute(invocable, executionContext, this.framework);

                    // create a Capture for the Result to allow injection and re-definition by PostProcessors
                    final Capture<Object> capture = Capture.ofNullable(initialResult);
                    //
                    //                    // establish a Resolver for the Captured result
                    //                    executionContext.addResolver(injectionPoint -> {
                    //                        if (injectionPoint instanceof AnnotatedDependency<?> annotatedDependency) {
                    //                            if (annotatedDependency.getRequiredClass().equals(Capture.class)) {
                    //
                    //                                // ensure the InjectionPoint is annotated with @From for this Task
                    //                                final From from = annotatedDependency.annotatedElement()
                    //                                    .getDeclaredAnnotation(From.class);
                    //
                    //                                if (from == null || !from.value().isAssignableFrom(task.getClass())) {
                    //                                    return Optional.empty();
                    //                                }
                    //
                    //                                return instruction.getInvocable().getTaskResultClass()
                    //                                    .map(taskResultClass -> {
                    //                                        final Optional<Type> type = annotatedDependency.getParameterizedTypes().findFirst();
                    //                                        return type.filter(Class.class::isInstance)
                    //                                            .map(t -> (Class<?>) t)
                    //                                            .filter(c -> c.isAssignableFrom(taskResultClass))
                    //                                            .map(__ -> capture)
                    //                                            .orElseThrow(() -> new UnsatisfiedDependencyException(annotatedDependency));
                    //                                    });
                    //                            }
                    //                        }
                    //
                    //                        return Optional.empty();
                    //                    });
                    //
                    //                    // establish a Resolver for the immutable initial result
                    //                    executionContext.addResolver(injectionPoint -> {
                    //                        if (injectionPoint instanceof AnnotatedDependency<?> annotatedDependency) {
                    //                            // ensure the InjectionPoint is annotated with @From for this Task
                    //                            final From from = annotatedDependency.annotatedElement()
                    //                                .getDeclaredAnnotation(From.class);
                    //
                    //                            if (from == null || !from.value().isAssignableFrom(task.getClass())) {
                    //                                return Optional.empty();
                    //                            }
                    //
                    //                            return instruction.getInvocable().getTaskResultClass()
                    //                                .filter(taskClassResult -> taskClassResult.isInstance(initialResult))
                    //                                .map(__ -> Optional.of(initialResult))
                    //                                .orElseThrow(() -> new UnsatisfiedDependencyException(annotatedDependency));
                    //                        }
                    //
                    //                        return Optional.empty();
                    //                    });
                    //
                    // execute the post-processing co-dependencies
                    instruction.codependencies()
                        .filter(codependency -> codependency.getTaskClass().isAnnotationPresent(PostProcess.class))
                        .forEach(codependency -> {
                            final Task<?> postprocessor = codependency.createTask(executionContext);
                            postprocessor.execute(codependency, executionContext, this.framework);
                        });

                    // obtain the final result from the Capture as it may have been changed during PostProcessing
                    final Object taskResult = capture.isPresent() ? capture.get() : null;

                    // task executed
                    activity.complete(taskResult);

                    // progress has been made executing the program
                    execution.progress();

                    // store the result, thus allowing it to be resolved and injected into post-processors
                    // (and next Tasks where required)
                    executionCache.putIfAbsent(taskResult == null
                        ? VoidAsset.create(invocable)
                        : DefaultAsset.create((Invocable<Object>) invocable, taskResult));

                    // re-queue the Task after post-requisite execution to allow scheduling of the next Tasks
                    queue.add(instruction);
                } catch (final Exception e) {

                    // the activity failed!
                    activity.completeExceptionally(e);

                    throw new ProgramExecutionException(
                        this, reference, "Failed to execute Task [" + invocable.getTaskName() + "]", e);
                }
            }
        }

        execution.complete();

        // detect tasks that could not be executed due to cyclic or otherwise unsatisfiable dependencies
        if (!remaining.isEmpty()) {
            final Reference stuck = remaining.iterator().next();
            throw new ProgramExecutionException(this, stuck,
                "Program execution stalled: " + remaining.size()
                    + " task(s) could not execute due to cyclic or unsatisfied dependencies: " + remaining);
        }

        return localCache;
    }
}
