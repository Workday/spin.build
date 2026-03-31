package build.spin.testing;

/*-
 * #%L
 * Spin Testing Support
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
import build.base.foundation.Strings;
import build.base.foundation.UniformResource;
import build.base.option.JDKVersion;
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.injection.Dependency;
import build.codemodel.injection.IndependentDependency;
import build.spin.Engine;
import build.spin.Project;
import build.spin.Workspace;
import build.spin.common.telemetry.TelemetryPublisher;
import build.spin.engine.DefaultEngine;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A JUnit Extension to automatically discover a {@link Workspace} for testing at a specified {@link Path}.
 *
 * @author brian.oliver
 * @see WorkspacePath
 * @see RequireJavaVersion
 * @since Jun-2020
 */
public class WorkspaceDiscovery
    implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    /**
     * The {@link TelemetryRecorder} for {@link WorkspaceDiscovery} events.
     */
    final TelemetryRecorder recorder = new TelemetryPublisher(
        UniformResource.createURI("class", WorkspaceDiscovery.class.getCanonicalName()),
        System.out::println);

    /**
     * The root {@link Path} for the test {@link Project}s.
     */
    private Path projectsRootPath;

    /**
     * The current {@link Workspace}s discovered and under test.
     */
    private ConcurrentHashMap<Path, Workspace> workspaces;

    @Override
    public void beforeAll(final ExtensionContext context) {

        this.workspaces = new ConcurrentHashMap<>();

        this.projectsRootPath = context.getTestClass()
            .map(c -> c.getProtectionDomain().getCodeSource().getLocation())
            .map(URL::getPath)
            .map(Paths::get)
            .map(path -> path.resolve("workspaces"))
            .filter(Files::exists)
            .filter(Files::isDirectory)
            .orElseThrow(
                () -> new RuntimeException("The expected path [" + this.projectsRootPath
                    + "] doesn't exist"));
    }

    /**
     * Attempt to obtain the {@link Path} to the {@link Workspace} specified by the {@link WorkspacePath} annotated
     * method.
     *
     * @param method the {@link Method}
     * @return the {@link Path}
     */
    private Optional<Path> getWorkspacePath(final Method method) {
        return method.isAnnotationPresent(WorkspacePath.class)
            ? Optional.of(this.projectsRootPath.resolve(method.getDeclaredAnnotation(WorkspacePath.class).value()))
            : Optional.empty();
    }

    @Override
    public void beforeEach(final ExtensionContext context) {

        // establish the FileSystem for the Engine
        final FileSystem fileSystem = FileSystems.getDefault();

        // determine the @WorkspacePath from the Test Method
        final Optional<Path> path = context.getTestMethod()
            .flatMap(this::getWorkspacePath);

        // determine the default JDKVersion based on the @RequireJavaVersion
        // or when not present, use the system version of Java
        final JDKVersion defaultJavaVersion = context.getTestMethod()
            .map(method -> method.getAnnotation(RequireJavaVersion.class))
            .map(RequireJavaVersion::value)
            .map(JDKVersion::of)
            .orElseGet(() -> path.map(Path::toString)
                .flatMap(Strings::lastDigitsOf)
                .map(JDKVersion::of)
                .orElseGet(JDKVersion::current));

        this.recorder.info("Using Java Version: %s", defaultJavaVersion);

        // establish the Engine for the test
        // (each test requires its own Engine to allow overriding of configuration on a
        // per test basis)
        final Configuration bootStrapOptionsByType = Configuration.of(defaultJavaVersion);

        // establish the Engine
        final Engine engine = new DefaultEngine(
            Thread.currentThread().getContextClassLoader(),
            fileSystem,
            bootStrapOptionsByType,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        // establish a simple Observer to output Engine Telemetry
        engine.subscribe(event -> recorder.info("Telemetry: %s", event));

        // determine the path of the Workspace for testing based on the @WorkspacePath
        final Path workspacePath = path
            .orElseThrow(
                () -> new RuntimeException("Failed to determine the workspace for " + context.getDisplayName()));

        // establish the Workspace at the WorkspacePath
        final Workspace workspace = engine.createWorkspace(workspacePath)
            .orElseThrow(() -> new RuntimeException("Failed to establish the workspace at " + workspacePath))
            .workspace();

        // store the Workspace for the WorkspacePath
        this.workspaces.put(workspacePath, workspace);

        // TODO: remove the previous build output for the Workspace
    }

    @Override
    public void afterEach(final ExtensionContext context) {

        // determine the @WorkspacePath from the Test Method
        final Optional<Path> path = context.getTestMethod()
            .flatMap(this::getWorkspacePath);

        // close the Workspace
        path.map(workspacePath -> this.workspaces.get(workspacePath))
            .ifPresent(Workspace::close);

    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
        throws ParameterResolutionException {

        final Class<?> requiredClass = parameterContext.getParameter().getType();

        // Workspaces, Engines and resolvable parameters from the Engine are permitted for injection
        return Workspace.class.isAssignableFrom(requiredClass)
            || Engine.class.isAssignableFrom(requiredClass)
            || extensionContext.getTestMethod()
            .flatMap(this::getWorkspacePath)
            .map(path -> this.workspaces.get(path))
            .map(workspace -> {
                final var framework = workspace.engine().framework();
                final var typeUsage = framework.codeModel().getTypeUsage(parameterContext.getParameter());
                final Dependency dep = IndependentDependency.of(typeUsage, framework::getQualifierAnnotationTypes);
                return workspace.engine().context().resolver().resolve(dep).isPresent();
            })
            .orElse(false);
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
        throws ParameterResolutionException {

        final Class<?> requiredClass = parameterContext.getParameter().getType();

        final Workspace workspace = extensionContext.getTestMethod()
            .flatMap(this::getWorkspacePath)
            .map(path -> this.workspaces.get(path))
            .orElseThrow(() ->
                new RuntimeException("Failed to resolve Workspace for " + extensionContext.getDisplayName()));

        if (Workspace.class.isAssignableFrom(requiredClass)) {
            return workspace;
        } else if (Engine.class.isAssignableFrom(requiredClass)) {
            return workspace.engine();
        } else {
            @SuppressWarnings("unchecked") final Object result = workspace.engine()
                .context()
                .create((Class<Object>) requiredClass);
            return result;
        }
    }
}
