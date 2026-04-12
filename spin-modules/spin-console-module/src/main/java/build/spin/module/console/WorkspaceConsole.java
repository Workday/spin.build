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
import build.serve.cors.CorsMiddleware;
import build.serve.foundation.routing.RouterBuilder;
import build.serve.graphql.GraphQlHandler;
import build.serve.graphql.GraphQlSchema;
import build.serve.graphql.GraphiQlHandler;
import build.serve.transport.http.HttpTransport;
import build.spin.Daemon;
import build.spin.Project;
import jakarta.inject.Inject;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link Daemon} providing a GraphQL server endpoint for a workspace.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public class WorkspaceConsole
    implements Daemon {

    /**
     * The allowed origin for CORS.
     */
    private static final String DEFAULT_ORIGIN = "http://127.0.0.1:3000";

    /**
     * The default host.
     */
    private static final String DEFAULT_HOST = "0.0.0.0";

    /**
     * The default port.
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
     * The {@link HttpTransport} serving GraphQL requests.
     */
    private HttpTransport transport;

    /**
     * Constructs a {@link WorkspaceConsole}.
     */
    public WorkspaceConsole() {
        this.daemon = new CompletableFuture<>();
    }

    @Override
    public Exceptional<CompletableFuture<Integer>> start() {
        try {
            final var sdl = new String(
                Objects.requireNonNull(
                    getClass().getClassLoader().getResourceAsStream("schema.graphqls"),
                    "schema.graphqls not found on classpath")
                    .readAllBytes(),
                StandardCharsets.UTF_8);

            final var schema = GraphQlSchema.builder(sdl)
                .fetcher("Query", "hello", env -> "world")
                .build();

            final var cors = CorsMiddleware.builder()
                .allowOrigin(DEFAULT_ORIGIN)
                .allowCredentials(true)
                .allowHeader("Content-Type")
                .build();

            final var router = RouterBuilder.create()
                .middleware(cors)
                .post("/graphql", GraphQlHandler.graphql(schema))
                .get("/", GraphiQlHandler.graphiql("/graphql"))
                .build();

            final var address = new InetSocketAddress(DEFAULT_HOST, DEFAULT_PORT);
            this.transport = new HttpTransport(address, 0, router);
            this.transport.start();

            this.recorder.info("WorkspaceConsole listening on http://%s:%d/", DEFAULT_HOST, DEFAULT_PORT);

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

        /**
         * Constructs a {@link MetaClass}.
         */
        public MetaClass() {
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            // the WorkspaceConsole is only active when the Project is the Workspace (has no parent)
            return project.parent().isEmpty();
        }
    }
}
