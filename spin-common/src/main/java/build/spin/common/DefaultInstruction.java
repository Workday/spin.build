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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
     */
    private final LinkedHashSet<Reference> dependencies;

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
        this.dependents = new LinkedHashSet<>();
        this.codependencies = new LinkedHashMap<>();

        // create the Task
        this.task = invocable.createTask(context);

        // include the dependencies as defined by the Task.Definition in the Instruction
        this.invocable.dependencies()
            .forEach(this::addDependency);

        // include the dependencies as defined by the Task in the Instruction
        this.task.dependencies()
            .forEach(this::addDependency);

        // include the tasks in the Project that must happen @Before this Task (that are not codependencies)
        // (these are dependencies of the Task)
        this.invocable.getProject()
            .invocables()
            .filter(defn -> !defn.isCodependency() && defn.isBefore(this.invocable.getReference()))
            .map(Invocable::getReference)
            .forEach(this::addDependency);

        // include the tasks in the Project that this task happens @After (that are not codependencies)
        // (this task has a dependency on those Tasks)
        this.invocable.getProject()
            .invocables()
            .filter(defn -> !defn.isCodependency() && this.invocable.isAfter(defn.getReference()))
            .map(Invocable::getReference)
            .forEach(this::addDependency);

        // include the codependencies in the project for the Task represented by the Instruction
        this.invocable.getProject()
            .codependencies(this.invocable.getReference())
            .forEach(this::addCodependency);
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
            this.recorder.warn("Task dependency [%s] is not available in project [%s] - skipping",
                reference, reference.project().name());
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
            this.recorder.warn("Task dependent [%s] is not available in project [%s] - skipping",
                reference, reference.project().name());
        }
    }

    /**
     * Adds the specified {@link Invocable} as a codependency of the {@link Task}.
     *
     * @param invocable the {@link Invocable}
     */
    public void addCodependency(final Invocable<?> invocable) {
        this.codependencies.put(invocable.getReference(), invocable);
    }
}
