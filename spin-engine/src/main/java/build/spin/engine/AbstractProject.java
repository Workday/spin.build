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

import build.base.configuration.Configuration;
import build.base.foundation.Capture;
import build.base.foundation.UniformResource;
import build.base.foundation.stream.Streams;
import build.base.telemetry.TelemetryRecorder;
import build.spin.Engine;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Resource;
import build.spin.Task;
import build.spin.Visitor;
import build.spin.Workspace;
import build.spin.annotation.PostProcess;
import build.spin.annotation.PreProcess;
import build.spin.common.telemetry.TelemetryPublisher;
import jakarta.inject.Inject;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * An abstract {@link Project}.
 *
 * @author brian.oliver
 * @since Jun-2019
 */
public abstract class AbstractProject implements Project {

    /**
     * The {@link Engine} for the {@link Project}.
     */
    protected final Engine engine;

    /**
     * The {@link TelemetryRecorder} for the {@link Project}.
     */
    protected final TelemetryRecorder recorder;

    /**
     * The {@link Optional} parent {@link Project} for the {@link Project}.
     */
    protected final Optional<Project> parent;

    /**
     * The {@link Path} in which the {@link Project} is defined.
     */
    protected final Path path;

    /**
     * The {@link Configuration} for the {@link Project}.
     */
    protected final Configuration optionsByType;

    /**
     * The child {@link Project}s of the {@link Project}.
     */
    protected ArrayList<Project> children;

    /**
     * The {@link Plugin}s for the {@link Project}.
     */
    protected LinkedHashMap<Class<? extends Plugin>, Plugin> plugins;

    /**
     * The {@link Resource}s for the {@link Project}.
     */
    protected LinkedHashMap<Class<? extends Resource>, Resource> resources;

    /**
     * The {@link Invocable}s for {@link Task}s that are defined for the {@link Project}.
     */
    protected LinkedHashMap<Reference, Invocable<?>> invocables;

    /**
     * Constructs a {@link Project}.
     *
     * @param engine The {@link Engine}
     * @param parent the {@link Optional} parent {@link Project}
     * @param path the {@link Path} of the {@link Project}
     * @param optionsByType the {@link Configuration} for the {@link Project}
     */
    @Inject
    public AbstractProject(final Engine engine,
        final Optional<Project> parent,
        final Path path,
        final Configuration optionsByType) {

        this.engine = engine;

        final URI uri = UniformResource.createURI("project", path.toAbsolutePath().toString());
        this.recorder = new TelemetryPublisher(uri, this.engine::publish);
        this.parent = parent;
        this.path = path;
        this.optionsByType = optionsByType;
        this.children = new ArrayList<>();
        this.plugins = new LinkedHashMap<>();
        this.resources = new LinkedHashMap<>();
        this.invocables = new LinkedHashMap<>();
    }

    @Override
    public String name() {
        return path().getFileName().toString();
    }

    @Override
    public Engine engine() {
        return this.engine;
    }

    @Override
    public Optional<Project> parent() {
        return this.parent;
    }

    @Override
    public int depth() {
        int depth = 0;
        Project project = this;
        while (project.parent().isPresent()) {
            depth++;
            project = project.parent().get();
        }

        return depth;
    }

    @Override
    public Path path() {
        return this.path;
    }

    @Override
    public Configuration options() {
        return this.optionsByType;
    }

    @Override
    public Stream<Plugin> plugins() {
        return this.plugins.values().stream();
    }

    @Override
    public <T> Stream<T> plugins(final Class<T> assignableTo) {
        return plugins()
            .filter(assignableTo::isInstance)
            .map(assignableTo::cast);
    }

    @Override
    public boolean contains(final Class<?> assignableTo) {
        return plugins(assignableTo)
            .findFirst()
            .isPresent();
    }

    @Override
    public Stream<Resource> resources() {
        return this.resources.values().stream();
    }

    @Override
    public Workspace workspace() {

        Project project = this;

        while (project.parent().isPresent()) {
            project = project.parent().get();
        }

        return (Workspace) project;
    }

    @Override
    public Stream<Project> stream(final Predicate<? super Project> predicate) {
        final LinkedHashSet<Project> projects = new LinkedHashSet<>();

        walk(project -> {
            if (predicate.test(project)) {
                projects.add(project);
            }
        });

        return projects.stream();
    }

    @Override
    public Stream<Project> stream() {
        final LinkedHashSet<Project> projects = new LinkedHashSet<>();

        walk(projects::add);

        return projects.stream();
    }

    @Override
    public Stream<Project> children() {
        return this.children.stream();
    }

    @Override
    public Stream<Invocable<?>> invocables() {
        return this.invocables.values().stream();
    }

    @Override
    public <T extends Plugin> Optional<T> getPlugin(final Class<T> pluginClass) {
        return plugins()
            .filter(pluginClass::isInstance)
            .findFirst()
            .map(pluginClass::cast);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Resource> Optional<T> getResource(final Class<T> resourceClass) {
        return Optional.ofNullable((T) this.resources.get(resourceClass));
    }

    @Override
    public <T extends Resource> Optional<T> findResource(final Class<T> resourceClass) {
        return getResource(resourceClass)
            .or(() -> this.parent.flatMap(project -> project.findResource(resourceClass)));
    }

    @Override
    public void walk(final Visitor<? super Project> visitor) {
        // the stack of projects to visit
        final Stack<Project> projects = new Stack<>();

        // the set of projects that have been processes
        // (to ensure visiting once and only once)
        final HashSet<Project> processed = new HashSet<>();

        // start by visiting this project
        projects.push(this);

        while (!projects.empty()) {

            final Project project = projects.peek();

            if (processed.contains(project)) {
                projects.pop();
                visitor.onLeaving(project);
            } else {
                processed.add(project);

                if (visitor.onEntering(project)) {
                    visitor.visit(project);

                    // visit the children
                    // (we reverse the order of the children as they are pushed onto the stack for processing)
                    Streams.reverse(project.children())
                        .filter(child -> !processed.contains(child))
                        .forEach(projects::push);
                }
            }
        }
    }

    @Override
    public boolean contains(final Path path) {
        return path != null && path.startsWith(path());
    }

    @Override
    public boolean isIgnored(final Path path) {
        return contains(path)
            && (resources().anyMatch(resource -> resource.isIgnored(path))
                || (parent().map(parent -> parent.isIgnored(path)).orElse(false)));
    }

    @Override
    public Stream<Invocable<?>> codependencies(final Reference reference) {
        // create a stream of Task.References to Tasks in this Plugin that specify @PreProcess or @PostProcess
        return invocables()
            .flatMap(definition -> {
                final Class<? extends Task<?>> taskClass = definition.getTaskClass();
                return Stream.concat(
                    Arrays.stream(taskClass.getDeclaredAnnotationsByType(PreProcess.class))
                        .filter(preprocessor -> preprocessor.value()
                            .isAssignableFrom(reference.getTaskClass()))
                        .map(initialize -> definition),
                    Arrays.stream(taskClass.getDeclaredAnnotationsByType(PostProcess.class))
                        .filter(postprocessor -> postprocessor.value()
                            .isAssignableFrom(reference.getTaskClass()))
                        .map(after -> definition));
            });
    }

    @Override
    public Optional<Project> getProject(final Path path) {
        if (path == null) {
            return Optional.empty();
        }

        // find the (closest ie: deepest) Project that contains the path
        final Capture<Project> capture = Capture.empty();

        workspace().walk(new Visitor<>() {
            @Override
            public boolean onEntering(final Project project) {
                return path.startsWith(project.path());
            }

            @Override
            public void visit(final Project project) {
                if (!capture.isPresent() || (capture.isPresent() && project.depth() > capture.get().depth())) {
                    capture.set(project);
                }
            }
        });

        return capture.optional();
    }

    @Override
    public void treeify(final StringBuilder builder,
        final String prefix,
        final String childPrefix,
        final Function<Project, String> contentExtractor) {
            
        builder.append(prefix);
        builder.append(contentExtractor == null ? name() : contentExtractor.apply(this));
        builder.append('\n');

        final Iterator<Project> children = children().sorted().iterator();
        while (children.hasNext()) {
            final Project child = children.next();

            if (children.hasNext()) {
                child.treeify(builder,
                    childPrefix + "  ├── ",
                    childPrefix + "  │   ",
                    contentExtractor);
            } else {
                child.treeify(builder,
                    childPrefix + "  └── ",
                    childPrefix + "      ",
                    contentExtractor);
            }
        }
    }

    @Override
    public int compareTo(final Project other) {
        return name().compareTo(other.name());
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(4096);
        treeify(builder, "", "", Project::name);
        return builder.toString();
    }
}
