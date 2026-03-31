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

import build.base.foundation.Exceptional;
import build.base.foundation.stream.Streams;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.jdk.option.ClassPath;
import build.spin.Project;
import build.spin.Task;
import build.spin.module.modulesystem.Artifact;
import build.spin.module.modulesystem.ModuleCatalog;
import build.spin.module.modulesystem.ModuleDescriptor;
import build.spin.module.modulesystem.ModuleReference;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Stack;

/**
 * An abstract {@link Task} to detect the {@link ClassPath} suitable for <strong>compiling</strong> a {@link JavaPlugin}
 * {@link Project}, including resolving external dependencies for a {@link Project}, <strong>but not resolving or
 * building </strong> any internal {@link Project} dependencies.
 *
 * @author brian.oliver
 * @since Oct-2019
 */
public abstract class AbstractDetectCompilationClassPath
    implements Task<ClassPath> {

    @Inject
    private TelemetryRecorder recorder;

    @Inject
    private Project project;

    @Inject
    private ModuleDescriptor moduleDescriptor;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    @Inject
    private ModuleCatalog catalog;

    @Inject
    private Artifact.Resolver resolver;

    @Inject
    private TargetDirectoryName target;

    @Inject
    private ModuleVersioning versioning;

    /**
     * Constructs a {@link ClassPath} given the dependencies specified by the {@link ModuleDescriptor}.
     *
     * @return the {@link ClassPath}
     */
    public ClassPath create() {

        // the paths in the ClassPath
        final LinkedHashSet<Path> paths = new LinkedHashSet<>();

        // the modules that have been visited (for transitive inclusion)
        final HashSet<String> includedModules = new HashSet<>();

        // establish a stack of modules to resolve and include in the ClassPath
        final Stack<ModuleDescriptor.Requires> stack = new Stack<>();
        Streams.reverse(this.moduleDescriptor.requires()).forEach(stack::push);

        while (!stack.isEmpty()) {

            // obtain the next required module to resolve
            final ModuleDescriptor.Requires requires = stack.pop();

            // attempt to find the required module within the root project
            final Optional<Project> dependency = this.project.workspace()
                .stream()
                .filter(prj ->
                    // attempt to locate the ModuleDescriptor for the Project using the first JavaCompilerPlugin
                    // (we can theoretically use the first as they should all technically be the same)
                    prj.plugins(JavaCompilerPlugin.class)
                        .findFirst()
                        .map(JavaCompilerPlugin::getModuleDescriptor)
                        .map(descriptor -> {
                            // include the required modules of the required module for processing when it is
                            // "transitive" and we've not already included them

                            // TODO: if the module descriptor is this descriptor, we've got a cycle

                            // include the project iff is the required module
                            if (requires.name().equals(descriptor.name())) {
                                if (requires.isTransitive()) {
                                    if (!includedModules.contains(requires.name())) {

                                        // place the required module back on the stack
                                        // (as the transitive requires must occur before the required module)
                                        stack.push(requires);

                                        // include the required modules of the required module
                                        // (if not already processed)
                                        Streams.reverse(descriptor.requires())
                                            .filter(r -> !JavaPlatform.isJavaPlatformModule(r.name()))
                                            .filter(r -> !includedModules.contains(r.name()))
                                            .forEach(stack::push);

                                        // we've now included the transitive dependencies
                                        includedModules.add(requires.name());
                                    }
                                }
                                return true;
                            }
                            else {
                                return false;
                            }
                        })
                        .orElse(false))
                .findFirst();

            if (dependency.isPresent()) {
                // include the path to the dependency classes and resources
                final Path dependencyPath = dependency.get().path();

                paths.add(dependencyPath.resolve(this.buildDirectoryName.get() + "/main/" + this.target.get()));
            }
            else if (!includedModules.contains(requires.name()) && !JavaPlatform.isJavaPlatformModule(
                requires.name())) {

                // determine the ModuleDescriptor.Version for the required Module (using Versioning)
                //  (it might not be defined, if so, we should warn)
                final Optional<ModuleDescriptor.Version> moduleVersion = this.versioning.getVersion(requires.name());

                // TODO: warn if the ModuleDescriptor.Version is not defined

                // determine the Version we need to obtain (using Requires)
                final Optional<ModuleDescriptor.Version> requiresVersion = requires.version();

                // TODO: check that the versions are the same (if both are defined)
                //  (warn if they are different)

                // TODO: if no versions are defined, we must abort the task
                //  (by throwing some kind of UndefinedModuleVersionException?)

                // determine the required ModuleDescriptor.Version for the Artifact
                // (use the Versioning defined version (if defined) or the Required version (if not defined))
                final ModuleDescriptor.Version requiredVersion = moduleVersion
                    .orElseGet(() -> requiresVersion
                        .orElseThrow(() -> new RuntimeException(
                            "Failed to determine Artifact Version for " + requires.name() + " in "
                                + this.project.name())));

                final ModuleReference moduleReference = ModuleReference.of(requires.name(), requiredVersion);

                // use the Catalog to locate the Artifact
                final Optional<Artifact> optional = this.catalog.getArtifact(moduleReference);

                if (optional.isPresent()) {
                    final Artifact artifact = optional.get();
                    this.recorder.info("Discovered External Dependency [%s] to be resolved", artifact);

                    // and then the Resolver to resolve the path to it
                    if (this.resolver.resolve(artifact)
                        .ifPresent(path -> {
                            this.recorder.info("External Dependency [%s] resolved to [%s]", artifact, path);

                            // include the path to the artifact
                            paths.add(path);

                            // attempt to resolve the ModuleDescriptor for the external dependency
                            final Exceptional<ModuleDescriptor> optionalDescriptor =
                                this.resolver.getModuleDescriptor(artifact, this.catalog, this.versioning);

                            // include the transitive dependencies of the external dependency
                            // TODO: if the ModuleDescriptor requires this module, we have a cycle!
                            optionalDescriptor.ifPresent(descriptor ->
                                Streams.reverse(descriptor.requires())
                                    .filter(ModuleDescriptor.Requires::isTransitive)
                                    .filter(r -> !includedModules.contains(r.name()))
                                    .peek(r -> this.recorder
                                        .diagnostic("Including transitive dependency [%s] for [%s]", r.name(),
                                            artifact))
                                    .forEach(stack::push));
                        })
                        .isException()) {

                        // the external dependency wasn't located!
                        this.recorder.error("Failed to resolve External Dependency [%s]", optional.get());
                    }
                }
                else {
                    this.recorder.warn(
                        "Failed to locate [%s] in Module Catalog.  It won't be included in the classpath",
                        requires.name());
                }
            }
        }

        return ClassPath.of(paths.stream());
    }
}
