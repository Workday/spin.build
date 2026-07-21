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

import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.base.version.Version;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.injection.Provides;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.codemodel.jdk.descriptor.ModuleModifier;
import build.codemodel.jdk.descriptor.OpenModule;
import build.codemodel.jdk.descriptor.VersionTrait;
import build.spawn.jdk.JDK;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.module.modulesystem.ModuleVersioning;
import build.spin.option.BuildDirectoryName;
import build.spin.option.TargetDirectoryName;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract {@link Plugin} for Java-based {@link Project}s.
 *
 * @author brian.oliver
 * @since Aug-2020
 */
public abstract class AbstractJavaPlugin
    implements JavaPlugin {

    @Inject
    protected TelemetryRecorder recorder;

    @Inject
    protected Project project;

    @Inject
    protected ModuleVersioning versioning;

    @Inject
    protected JavaPlatform platform;

    @Inject
    private CodeModel codeModel;

    @Inject
    private BuildDirectoryName buildDirectoryName;

    @Inject
    private TargetDirectoryName targetDirectoryName;

    /**
     * The cached {@link JDKModuleDescriptor} determined for the {@link Project}.
     */
    private final AtomicReference<JDKModuleDescriptor> moduleDescriptor;

    /**
     * Constructs a {@link AbstractJavaPlugin}.
     */
    public AbstractJavaPlugin() {
        this.moduleDescriptor = new AtomicReference<>();
    }

    /**
     * Obtains the root {@link Path} for source code (including resources) for the {@link JavaPlugin}.
     *
     * @return the root {@link Path}
     */
    protected Path getSourceRootPath() {
        return this.project.path().resolve("src/main/");
    }

    @Override
    @Provides
    public abstract JDKVersion getJavaVersion();

    @Override
    @Provides
    public JDK getJDK() {
        return this.platform
            .getVersion(getJavaVersion().major())
            .orElseThrow(() -> {
                final var jdks = this.platform.stream().toList();
                return new RuntimeException("Failed to obtain Java Development Kit for Java " + getJavaVersion().major() + ". Available Java Development Kits: " + jdks);
            });
    }

    @Override
    @Provides
    public JDKModuleDescriptor getModuleDescriptor() {

        // determine the ModuleDescriptor only once
        return this.moduleDescriptor.updateAndGet(descriptor -> {
            if (descriptor == null) {
                // create a list of places to look for module-info.java (based on priority)
                final List<Path> moduleInfoPaths = new ArrayList<>();

                final Path unversionedModuleInfoPath = getSourceRootPath()
                    .resolve("java/")
                    .resolve(JDKModuleDescriptor.SOURCE_FILENAME);

                if (Files.exists(unversionedModuleInfoPath)) {
                    moduleInfoPaths.add(unversionedModuleInfoPath);
                }

                final Path versionedModuleInfoPath = getSourceRootPath()
                    .resolve("java" + getJavaVersion().major() + "/")
                    .resolve(JDKModuleDescriptor.SOURCE_FILENAME);

                if (Files.exists(versionedModuleInfoPath)) {
                    moduleInfoPaths.add(versionedModuleInfoPath);
                }

                final Path versions = getSourceRootPath()
                    .resolve("resources/META-INF/versions/");
                if (Files.exists(versions)) {
                    try {
                        Files.list(versions)
                            .filter(path -> Files.exists(path.resolve(JDKModuleDescriptor.SOURCE_FILENAME)))
                            .map(path -> {
                                try {
                                    return Integer.parseInt(path.getFileName().toString());
                                } catch (final NumberFormatException e) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .sorted(Comparator.reverseOrder())
                            .map(version -> versions.resolve(version.toString())
                                .resolve(JDKModuleDescriptor.SOURCE_FILENAME))
                            .forEach(moduleInfoPaths::add);
                    } catch (final IOException e) {
                        this.recorder.warn(e,
                            "Failed to list [%s] while looking for versioned module-info.java files for [%s].",
                            versions, this.project.name());
                    }
                }

                if (moduleInfoPaths.size() > 1) {
                    this.recorder.info(
                        "[%s] has %d candidate module-info.java files %s — using [%s]",
                        this.project.name(), moduleInfoPaths.size(), moduleInfoPaths, moduleInfoPaths.get(0));
                }

                final String normalizedProjectName = this.project.name().replace("-", ".");

                final JDKModuleDescriptor result = moduleInfoPaths.stream()
                    .findFirst()
                    .map(path -> {
                        try (BufferedReader reader = Files.newBufferedReader(path)) {
                            return JDKModuleDescriptor.parse(this.codeModel, reader);
                        } catch (final IOException e) {
                            this.recorder.warn(e,
                                "Failed to read [%s] for [%s]. Defaulting to an empty ModuleDescriptor.",
                                path, this.project.name());
                            return automaticDescriptor(normalizedProjectName);
                        }
                    })
                    .orElseGet(() -> {
                        this.recorder.warn(
                            "[%s] does not define a ModuleDescriptor. Defaulting to an empty ModuleDescriptor.",
                            this.project.name());
                        return automaticDescriptor(normalizedProjectName);
                    });

                // module-info.java source has no version syntax, so a parsed (or synthesized)
                // descriptor never carries one; attach the Project's actual version here so
                // downstream consumers don't report an unknown version for workspace-local modules.
                stampVersion(result, this.versioning.getVersion(result.moduleName().toString()));

                return result;
            }

            return descriptor;
        });
    }

    /**
     * Stamps {@code version}, if present, onto {@code descriptor} as a {@link VersionTrait}.
     * <p>
     * {@code descriptor} may be a shared instance from the CodeModel registry (multiple sibling
     * plugins for the same module name can each obtain and stamp it), so any existing
     * {@link VersionTrait} is removed first to keep re-stamping idempotent.
     *
     * @param descriptor the {@link JDKModuleDescriptor} to stamp
     * @param version the {@link Version} to stamp, if known
     */
    static void stampVersion(final JDKModuleDescriptor descriptor, final Optional<Version> version) {
        version.ifPresent(v -> {
            descriptor.getTrait(VersionTrait.class).ifPresent(descriptor::removeTrait);
            descriptor.addTrait(VersionTrait.of(v));
        });
    }

    private JDKModuleDescriptor automaticDescriptor(final String name) {
        final ModuleName moduleName = this.codeModel.getNameProvider().getModuleName(name).orElseThrow();
        // Use of() directly rather than createModuleDescriptor() to avoid registering this fallback
        // in the shared CodeModel cache — if a real module-info.java is later parsed for the same
        // module name (e.g. by a sibling plugin), parse() must be able to create the registry entry
        // uncontested.
        final JDKModuleDescriptor descriptor = JDKModuleDescriptor.of(this.codeModel, moduleName);
        descriptor.addTrait(OpenModule.OPEN);
        descriptor.addTrait(ModuleModifier.AUTOMATIC);
        return descriptor;
    }

    /**
     * Obtains the {@link Version} for the {@link Project}
     *
     * @return the {@link Version}
     */
    @Provides
    public Version getVersion() {
        return this.versioning.getVersion(getModuleDescriptor().moduleName().toString())
            .orElse(ModuleVersioning.DEFAULT_VERSION);
    }

    /**
     * Returns cross-project compile {@link Reference}s for tasks that extend
     * {@link AbstractDetectResolution} — specifically, the {@link JavaCompilerPlugin.Compile}
     * task of each workspace sibling whose module is directly required by this plugin's
     * module descriptor.
     *
     * <p>This drives the cross-project ordering in {@link build.spin.common.DefaultInvocable}:
     * the scheduler will not start a detection task until every required sibling has been
     * compiled, ensuring that the sibling's spin output directory exists when
     * {@link AbstractDetectResolution} checks for it.
     */
    @Override
    public Stream<Reference> projectDependencies(final Class<? extends Task<?>> forTaskClass) {
        if (!AbstractDetectResolution.class.isAssignableFrom(forTaskClass)) {
            return Stream.empty();
        }

        final Set<String> requiredModuleNames = getModuleDescriptor().requiresClauses()
            .map(r -> r.requiresModuleName().toString())
            .collect(Collectors.toSet());

        return this.project.workspace().stream()
            .filter(prj -> prj != this.project)
            .filter(prj -> prj.plugins(JavaCompilerPlugin.class)
                .findFirst()
                .map(p -> requiredModuleNames.contains(p.getModuleDescriptor().moduleName().toString()))
                .orElse(false))
            .filter(prj -> AbstractDetectResolution.resolveCompiledOutput(
                prj.path(), this.buildDirectoryName.get(), this.targetDirectoryName.get()).isEmpty())
            .flatMap(prj -> prj.invocables()
                .filter(inv -> JavaCompilerPlugin.Compile.class.isAssignableFrom(inv.getTaskClass()))
                .filter(inv -> inv.getTaskClass().getEnclosingClass() == forTaskClass.getEnclosingClass())
                .map(Invocable::getReference));
    }
}


