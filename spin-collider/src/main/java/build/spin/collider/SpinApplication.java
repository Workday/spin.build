package build.spin.collider;

/*-
 * #%L
 * Spin Collider Application
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
import build.base.configuration.ConfigurationBuilder;
import build.base.network.Network;
import build.base.network.Server;
import build.base.network.option.SocketTimeout;
import build.spawn.application.Platform;
import build.spawn.application.Process;
import build.spawn.jdk.JDK;
import build.spawn.jdk.JDKApplication;
import build.spawn.jdk.option.MainClass;
import build.spin.application.Spin;
import build.spin.option.ServerPort;
import jakarta.inject.Inject;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * Application wrapper for the {@link Spin} application.
 *
 * @author bin.chen
 * @since Feb-2023
 */
public interface SpinApplication
    extends JDKApplication {

    class Implementation
        extends JDKApplication.Implementation
        implements SpinApplication {
        private final CompletableFuture<SpinApplication> onStart;
        public volatile String hostName;

        /**
         * Constructs a {@link JDKApplication.Implementation}.
         *
         * @param platform         the {@link Platform}
         * @param process          the {@link Process}
         * @param applicationClass the {@link Class} of {@link SpinApplication}
         * @param configuration    the {@link Configuration}
         * @param server           the {@link Server} to which this {@link SpinApplication} will connect
         */
        @Inject
        private Implementation(final Platform platform,
                               final Process process,
                               final Class<? extends SpinApplication> applicationClass,
                               final Configuration configuration,
                               final Server server) {
            super(platform, process, applicationClass, configuration, server);

            this.onStart = CompletableFuture.completedFuture(null)
                .thenApply(ignored -> this.hostName = "localhost")
                .thenCompose(__ -> {
                    final InetSocketAddress address = new InetSocketAddress(this.hostName, ServerPort.DEFAULT.get());
                    System.out.println("Address " + address);
                    return Network.connect(address, SocketTimeout.ofMillis(100)).thenApply(socket -> this);
                })
                .thenApply(__ -> this);
        }

        @Override
        public CompletableFuture<SpinApplication> onStart() {
            return super.onStart().thenCompose(__ -> this.onStart);
        }
    }

    /**
     * {@link Customizer} class for {@link SpinApplication}.
     */
    class Customizer
        implements build.spawn.application.Customizer<SpinApplication> {

        @Override
        public void onPreparing(final Platform platform,
                                final Class<? extends SpinApplication> applicationClass,
                                final ConfigurationBuilder configurationBuilder) {

            configurationBuilder.add(
                MainClass.of(Spin.class));
            configurationBuilder.add(
                JDK.current().home());
        }
    }
}
