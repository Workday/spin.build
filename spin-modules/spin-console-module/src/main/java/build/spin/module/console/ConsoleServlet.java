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

import build.spin.Engine;
import build.spin.Workspace;
import graphql.execution.AsyncExecutionStrategy;
import graphql.kickstart.execution.GraphQLQueryInvoker;
import graphql.kickstart.execution.config.DefaultExecutionStrategyProvider;
import graphql.kickstart.servlet.GraphQLConfiguration;
import graphql.kickstart.servlet.GraphQLHttpServlet;
import graphql.schema.GraphQLSchema;
import jakarta.inject.Inject;

/**
 * The {@link GraphQLHttpServlet} supporting GraphQL requests against a {@link Workspace}.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public class ConsoleServlet
    extends GraphQLHttpServlet {

    /**
     * The underlying {@link Engine} that established the {@link Engine}.
     */
    @Inject
    private Engine engine;

    /**
     * The {@link Workspace} for which we're serving requests.
     */
    @Inject
    private Workspace workspace;

    @Inject
    private GraphQLSchema schema;

    @Override
    protected GraphQLConfiguration getConfiguration() {

        return GraphQLConfiguration.with(this.schema)
            .with(
                GraphQLQueryInvoker.newBuilder()
                    .withExecutionStrategyProvider(
                        new DefaultExecutionStrategyProvider(
                            new AsyncExecutionStrategy(),
                            null,      // we don't support mutations (yet)
                            null))  // we don't support subscriptions (yet)
                    .build())
            .build();
    }
}
