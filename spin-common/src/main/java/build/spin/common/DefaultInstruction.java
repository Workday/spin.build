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

import build.base.telemetry.TelemetryRecorder;
import build.codemodel.dependency.injection.Context;
import build.spin.Instruction;
import build.spin.Invocable;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.PostProcess;
import build.spin.annotation.PreProcess;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The default implementation of an {@link Instruction}.
 *
 * @author brian.oliver
 * @since Jun-2019
 */
public class DefaultInstruction<T>
    implements Instruction<T> {

    /**
     * The unique identity of the {@link DefaultInstruction} in the {@link DefaultProgram} to which it belongs.
     */
    private final int identity;

    /**
     * The {@link Invocable} for the {@link Task}.
     */
    private final Invocable<T> invocable;

    /**
     * The {@link TelemetryRecorder} to report {@link Task} execution.
     */
    private final TelemetryRecorder recorder;

    /**
     * The {@link Context} to be used for the {@link Task} {@link DefaultInstruction}.
     */
    private final Context context;

    /**
     * The {@link Task} for the {@link DefaultInstruction}.
     */
    private final Task<T> task;

    /**
     * The {@link Task} dependencies, those {@link Task}s on which this {@link Task} depends.
     * <p>
     * Includes both genuine (forcing) dependencies and ordering-only {@link build.spin.annotation.Before}/
     * {@link build.spin.annotation.After} relationships, used to determine execution order once all
     * {@link Task}s are known.
     */
    private final LinkedHashSet<Reference> dependencies;

    /**
     * The subset of {@link #dependencies} that are genuine (forcing) dependencies - those defined by
     * {@link Invocable#dependencies()} (eg: {@link build.spin.annotation.From}) or {@link Task#dependencies()} -
     * as opposed to ordering-only {@link build.spin.annotation.Before}/{@link build.spin.annotation.After}
     * relationships, which must not by themselves cause a {@link Task} to be included in a {@link build.spin.Program}.
     */
    private final LinkedHashSet<Reference> requiredDependencies;

    /**
     * The {@link Task} dependents, those {@link Task}s that are immediately dependent upon this {@link Task}.
     */
    private final LinkedHashSet<Reference> dependents;

    /**
     * The {@link Task} codependencies, those {@link Task}s that must be executed to either
     * {@link PreProcess} or {@link PostProcess} this {@link Task}.
     */
    private final LinkedHashMap<Reference, Invocable<?>> codependencies;

    /**
     * The {@link Task} instance for each codependency in {@link #codependencies}, created once here
     * (rather than freshly per execution) so that {@link Task#dependencies()} is read from the same
     * instance {@link DefaultProgram#runTask} later executes inline - mirroring how {@link #task} is
     * created once and reused. Keyed by the same {@link Reference} as {@link #codependencies}.
     */
    private final LinkedHashMap<Reference, Task<?>> codependencyTasks;

    /**
     * Constructs an {@link DefaultInstruction} for a {@link Task}.
     *
     * @param identity  the identity of the {@link DefaultInstruction} in the {@link DefaultProgram}
     * @param invocable the {@link Reference}
     * @param recorder  the {@link TelemetryRecorder} for the {@link DefaultInstruction}
     * @param context   the {@link Context}
     */
    DefaultInstruction(final int identity,
                       final Invocable<T> invocable,
                       final TelemetryRecorder recorder,
                       final Context context) {

        this.identity = identity;
        this.invocable = invocable;
        this.recorder = recorder;
        this.context = context;
        this.dependencies = new LinkedHashSet<>();
        this.requiredDependencies = new LinkedHashSet<>();
        this.dependents = new LinkedHashSet<>();
        this.codependencies = new LinkedHashMap<>();
        this.codependencyTasks = new LinkedHashMap<>();

        // create the Task
        this.task = invocable.createTask(context);

        // include the dependencies as defined by the Task.Definition in the Instruction - these are genuine
        // (forcing) dependencies, eg: @From
        this.invocable.dependencies()
            .forEach(this::addRequiredDependency);

        // include the dependencies as defined by the Task in the Instruction - also genuine (forcing)
        this.task.dependencies()
            .forEach(this::addRequiredDependency);

        // include the tasks in the Project that must happen @Before this Task (that are not codependencies).
        // per the @Before contract, these are ordering-only: they must NOT by themselves force the referenced
        // Task into the Program, only constrain its order should it be included for another reason - so they're
        // added to dependencies() (for graph edges) but NOT to requiredDependencies() (for inclusion)
        this.invocable.getProject()
            .invocables()
            .filter(defn -> !defn.isCodependency() && defn.isBefore(this.invocable.getReference()))
            .map(Invocable::getReference)
            .forEach(this::addDependency);

        // include the tasks in the Project that this task happens @After (that are not codependencies).
        // per the @After contract, these are ordering-only - see the @Before case above
        this.invocable.getProject()
            .invocables()
            .filter(defn -> !defn.isCodependency() && this.invocable.isAfter(defn.getReference()))
            .map(Invocable::getReference)
            .forEach(this::addDependency);

        // include the codependencies in the project for the Task represented by the Instruction,
        // resolved transitively - a codependency that itself declares @PreProcess/@PostProcess has
        // its own nested codependencies discovered too, rather than only one level of nesting
        final LinkedHashSet<Reference> visitedCodependencies = new LinkedHashSet<>();
        visitedCodependencies.add(this.invocable.getReference());
        addCodependenciesRecursively(this.invocable.getReference(), visitedCodependencies);

        // a @PreProcess/@PostProcess codependency runs inline as part of this Instruction and never
        // gets an Instruction of its own, so its forcing dependencies are this Instruction's forcing
        // dependencies too. Its @From-parameter dependencies are folded in elsewhere (see
        // DefaultProgram, via Invocable#dependencies()); its programmatically declared
        // Task#dependencies() - the only way to declare a cross-project forcing dependency - are
        // folded in here, read from the same Task instance DefaultProgram#runTask executes inline
        // (see #codependencyTasks). A codependency that doesn't override dependencies() just
        // contributes an empty Stream.
        this.codependencyTasks.values().stream()
            .flatMap(Task::dependencies)
            .forEach(this::addRequiredDependency);

        // fold @Before/@After declared on any codependency anywhere in the Project (at any nesting
        // depth) into this Instruction's own ordering-only dependencies. A codependency never gets
        // its own Instruction - it's executed inline as part of its ultimate top-level owner - so any
        // ordering constraint it declares must be translated into a constraint on that owner instead
        this.invocable.getProject()
            .invocables()
            .filter(Invocable::isCodependency)
            .filter(defn -> defn.isBefore(this.invocable.getReference()))
            .map(DefaultInstruction::ultimateOwner)
            .filter(owner -> !owner.equals(this.invocable.getReference()))
            .forEach(this::addDependency);

        this.invocable.getProject()
            .invocables()
            .filter(Invocable::isCodependency)
            .filter(defn -> this.invocable.isAfter(defn.getReference()))
            .map(DefaultInstruction::ultimateOwner)
            .filter(owner -> !owner.equals(this.invocable.getReference()))
            .forEach(this::addDependency);
    }

    /**
     * Recursively discovers the codependencies of the {@link Task} identified by {@code reference},
     * adding each to {@link #codependencies} and then descending into its own codependencies in turn,
     * guarding against revisiting a {@link Reference} already seen (eg: a cyclic pre/post-process chain).
     * <p>
     * A {@link PreProcess} codependency's own codependencies are discovered before it is added (so
     * they execute before it, as they in turn pre-process it); a {@link PostProcess} codependency's
     * own codependencies are discovered after it is added (so they execute after it, as they in turn
     * post-process it). This keeps {@link #codependencies}' iteration order consistent with the order
     * {@code DefaultProgram#runTask} must execute them in for same-kind nesting - a {@link PreProcess}
     * nested under a {@link PostProcess}, or vice versa, is a known limitation not handled here.
     *
     * @param reference the {@link Reference} whose codependencies are to be discovered
     * @param visited   the {@link Reference}s already discovered, to guard against cycles/duplicates
     */
    private void addCodependenciesRecursively(final Reference reference, final Set<Reference> visited) {
        this.invocable.getProject()
            .codependencies(reference)
            .filter(codependency -> visited.add(codependency.getReference()))
            .forEach(codependency -> {
                if (codependency.getTaskClass().isAnnotationPresent(PreProcess.class)) {
                    addCodependenciesRecursively(codependency.getReference(), visited);
                    addCodependency(codependency);
                } else {
                    addCodependency(codependency);
                    addCodependenciesRecursively(codependency.getReference(), visited);
                }
            });
    }

    /**
     * Walks up a codependency's own {@link PreProcess}/{@link PostProcess} chain to find the
     * {@link Reference} of the top-level {@link Task} that actually owns an {@link Instruction} -
     * a codependency is executed inline as part of another {@link Task} rather than getting its own
     * {@link Instruction}, so any ordering constraint (eg: {@link build.spin.annotation.Before}/
     * {@link build.spin.annotation.After}) it declares must be translated into a constraint on that
     * top-level owner instead.
     *
     * @param invocable the {@link Invocable} to resolve the ultimate owner of
     * @return the {@link Reference} of the top-level owning {@link Task}
     */
    private static Reference ultimateOwner(final Invocable<?> invocable) {
        final Class<? extends Task<?>> taskClass = invocable.getTaskClass();

        final Optional<Class<? extends Task<?>>> target = Stream.concat(
                Arrays.stream(taskClass.getDeclaredAnnotationsByType(PreProcess.class)).map(PreProcess::value),
                Arrays.stream(taskClass.getDeclaredAnnotationsByType(PostProcess.class)).map(PostProcess::value))
            .findFirst();

        if (target.isEmpty()) {
            return invocable.getReference();
        }

        return invocable.getProject()
            .getInvocable(Reference.of(invocable.getProject(), target.get()))
            .map(DefaultInstruction::ultimateOwner)
            .orElseGet(() -> Reference.of(invocable.getProject(), target.get()));
    }

    @Override
    public int getIdentity() {
        return this.identity;
    }

    @Override
    public Reference getReference() {
        return this.invocable.getReference();
    }

    @Override
    public Project getProject() {
        return this.invocable.getProject();
    }

    @Override
    public Invocable<T> getInvocable() {
        return this.invocable;
    }

    @Override
    public String toString() {
        return getReference().toString();
    }

    /**
     * Obtains the {@link TelemetryRecorder} scoped to the {@link Task} this {@link DefaultInstruction}
     * executes (ie: {@code spin-task://workspace/project/task-name}), rather than the coarser
     * {@link DefaultProgram}-wide recorder.
     *
     * @return the {@link TelemetryRecorder}
     */
    public TelemetryRecorder getRecorder() {
        return this.recorder;
    }

    @Override
    public Task<?> getTask() {
        return this.task;
    }

    @Override
    public Stream<Reference> dependencies() {
        return this.dependencies.stream();
    }

    @Override
    public boolean hasDependencies() {
        return !this.dependencies.isEmpty();
    }

    /**
     * Obtains the {@link Reference}s of the genuine (forcing) dependencies of the {@link Task} - those that
     * must be included in a {@link build.spin.Program} for this {@link Task} to execute, as opposed to
     * ordering-only {@link build.spin.annotation.Before}/{@link build.spin.annotation.After} relationships.
     *
     * @return a {@link Stream} of {@link Reference}s
     */
    public Stream<Reference> requiredDependencies() {
        return this.requiredDependencies.stream();
    }

    @Override
    public Stream<Reference> dependents() {
        return this.dependents.stream();
    }

    @Override
    public boolean hasDependents() {
        return !this.dependents.isEmpty();
    }

    @Override
    public Stream<Invocable<?>> codependencies() {
        return this.codependencies.values().stream();
    }

    @Override
    public boolean hasCodependencies() {
        return !this.codependencies.isEmpty();
    }

    /**
     * Obtains the {@link Context} for the {@link Instruction}.
     *
     * @return the {@link Context}
     */
    public Context getContext() {
        return this.context;
    }

    /**
     * Adds the specified {@link Reference} as a dependency of the {@link Task}.
     *
     * @param reference the {@link Reference}
     */
    public void addDependency(final Reference reference) {
        // ensure the Plugin defined by the Task.Reference is defined by the Project in which the Task operates
        if (reference.project().getPlugin(reference.getPluginClass()).isPresent()) {
            this.dependencies.add(reference);
        } else {
            this.recorder.warn("Task dependency [%s] is not available in its project - skipping", reference);
        }
    }

    /**
     * Adds the specified {@link Reference} as a genuine (forcing) dependency of the {@link Task}, in addition
     * to recording it as an ordering dependency (see {@link #addDependency(Reference)}).
     *
     * @param reference the {@link Reference}
     */
    public void addRequiredDependency(final Reference reference) {
        addDependency(reference);

        if (this.dependencies.contains(reference)) {
            this.requiredDependencies.add(reference);
        }
    }

    /**
     * Adds the specified {@link Reference} to a {@link Task} that is dependent upon this {@link Task}.
     *
     * @param reference the {@link Reference}
     */
    public void addDependent(final Reference reference) {
        // ensure the Plugin defined by the Task.Reference is defined by the Project in which the Task operates
        if (reference.project().getPlugin(reference.getPluginClass()).isPresent()) {
            this.dependents.add(reference);
        } else {
            this.recorder.warn("Task dependent [%s] is not available in its project - skipping", reference);
        }
    }

    /**
     * Adds the specified {@link Invocable} as a codependency of the {@link Task}.
     *
     * @param invocable the {@link Invocable}
     */
    public void addCodependency(final Invocable<?> invocable) {
        this.codependencies.put(invocable.getReference(), invocable);
        this.codependencyTasks.put(invocable.getReference(), invocable.createTask(this.context));
    }

    /**
     * Obtains the {@link Task} instance for a codependency of this {@link DefaultInstruction} - the
     * instance created when this {@link DefaultInstruction} was built and reused for every execution,
     * as {@link #getTask()} is for the primary {@link Task}.
     *
     * @param codependency a codependency {@link Invocable}, as returned by {@link #codependencies()}
     * @return the {@link Task} instance for the codependency
     */
    @Override
    public Task<?> codependencyTask(final Invocable<?> codependency) {
        return this.codependencyTasks.get(codependency.getReference());
    }
}
