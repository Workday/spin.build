package build.spin.module.console;

/*-
 * #%L
 * Spin Console Module
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
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.injection.Context;
import build.spin.Daemon;
import build.spin.Project;
import build.spin.Workspace;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.util.HttpString;
import jakarta.inject.Inject;
import jakarta.servlet.Servlet;

import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.undertow.Handlers.resource;

/**
 * A {@link Workspace} {@link Daemon} providing a GraphQL server endpoint.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public class WorkspaceConsole
    implements Daemon {

    /**
     * The default GraphQL address and port.
     */
    private static final String DEFAULT_ORIGIN = "http://127.0.0.1:3000";

    /**
     * The default host (for serving non-graph http(s) requests).
     */
    private static final String DEFAULT_HOST = "0.0.0.0";

    /**
     * The default port (for serving non-graph http(s) requests).
     */
    private static final int DEFAULT_PORT = 8080;

    /**
     * The {@link CompletableFuture} controlling daemon lifecycle.
     */
    private final CompletableFuture<Integer> daemon;

    /**
     * The {@link TelemetryRecorder} for the {@link WorkspaceConsole}.
     */
    @Inject
    private TelemetryRecorder recorder;

    /**
     * The {@link Context} to perform dependency injection for the {@link Daemon}.
     */
    @Inject
    private Context context;

    /**
     * The {@link Workspace} in which the {@link WorkspaceConsole} is operating.
     */
    @Inject
    private Workspace workspace;

    /**
     * The {@link DeploymentManager} managing the {@link ConsoleServlet} deployment.
     */
    private DeploymentManager manager;

    /**
     * The {@link Undertow} server for serving GraphQL and non-GraphQL http(s) console requests.
     */
    private Undertow undertow;

    /**
     * Constructs a {@link WorkspaceConsole}.
     */
    public WorkspaceConsole() {
        this.daemon = new CompletableFuture<>();
    }

    /**
     * Creates the {@link GraphQLSchema} for the {@link WorkspaceConsole}.
     *
     * @return the {@link GraphQLSchema}
     */
    private GraphQLSchema createSchema() {

        // read the WorkspaceConsole GraphQL Schema (from the resources)
        final var reader = new InputStreamReader(
            Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("schema.graphqls")));

        // establish a TypeRegistry based on the GraphQL Schema
        final var typeRegistry = new SchemaParser().parse(reader);

        final RuntimeWiring runtimeWiring =
            newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("hello", new StaticDataFetcher("world")))
                .build();

        final SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    @Override
    public Exceptional<CompletableFuture<Integer>> start() {

        // define the ConsoleServlet to handle GraphQL requests
        try {
            final DeploymentInfo deploymentInfo = Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath("/graphql")
                .setDeploymentName("WorkspaceConsole")
                .addServlet(Servlets.servlet("ConsoleServlet", ConsoleServlet.class, () -> {
                        // establish a new servlet (using Dependency Injection)
                        final var context = this.context.newContext();

                        context.bind(WorkspaceConsole.class).to(this);
                        context.bind(GraphQLSchema.class).to(createSchema());

                        final var servlet = context.create(ConsoleServlet.class);

                        return new InstanceHandle<>() {
                            @Override
                            public Servlet getInstance() {
                                return servlet;
                            }

                            @Override
                            public void release() {
                                servlet.destroy();
                            }
                        };
                    })
                    .addMapping("/*"));

            this.manager = Servlets.defaultContainer().addDeployment(deploymentInfo);

            this.manager.deploy();

            // establish the ResourceManager to serve static resources (from the console/ resource folder)
            final ResourceManager resourceManager =
                new ClassPathResourceManager(WorkspaceConsole.class.getClassLoader(), "console");

            final PathHandler pathHandler = Handlers.path()
                .addPrefixPath("/graphql", this.manager.start())
                .addPrefixPath("/", resource(resourceManager)
                    .setDirectoryListingEnabled(true)
                    .addWelcomeFiles("index.html"));

            final var allowedOrigin = DEFAULT_ORIGIN;

            final SetHeaderHandler allowOriginHeaderHandler = Handlers.header(pathHandler,
                String.valueOf(new HttpString("Access-Control-Allow-Origin")), allowedOrigin);
            final SetHeaderHandler allowCredentialsHeaderHandler = Handlers.header(allowOriginHeaderHandler,
                String.valueOf(new HttpString("Access-Control-Allow-Credentials")), "true");
            final SetHeaderHandler allowHeadersHeaderHandler = Handlers.header(allowCredentialsHeaderHandler,
                String.valueOf(new HttpString("Access-Control-Allow-Headers")), "Content-Type");

            final var host = DEFAULT_HOST;
            final var port = DEFAULT_PORT;

            this.undertow = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(allowHeadersHeaderHandler)
                .build();

            // start undertow!
            this.undertow.start();

            this.undertow.getListenerInfo()
                .forEach(listenerInfo -> this.recorder.info("Listening: %s", listenerInfo));

            return Exceptional.of(this.daemon);
        } catch (final Exception e) {
            this.recorder.fatal("Failed to start WorkspaceConsole", e);
            return Exceptional.ofException(e);
        }
    }

    /**
     * The {@link Daemon.MetaClass} for {@link WorkspaceConsole}.
     */
    public static class MetaClass
        implements Daemon.MetaClass {

        @Override
        public boolean isDetectedIn(final Project project) {
            // the WorkspaceConsole is only active when the Project is the Workspace (has no parent)
            return project.parent().isEmpty();
        }
    }
}
