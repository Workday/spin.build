package build.spin.module.languageserver;

/*-
 * #%L
 * Spin Language Server Module
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
import build.spin.module.languageserver.protocol.ProtocolHandler;
import build.spin.module.languageserver.protocol.Server;
import build.spin.option.ServerPort;
import jakarta.inject.Inject;

import java.nio.file.FileSystem;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link build.spin.Server} providing a Language Server Protocol implementation.
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/">language-server-protocol</a>}
 *
 * @author drew.malin
 * @since Feb-2023
 */
public class LanguageServer
    implements build.spin.Server {

    private final Set<ProtocolHandler> handlers;

    /**
     * The default server listening port.
     */
    @Inject
    public ServerPort SERVER_PORT = ServerPort.DEFAULT;

    @Inject
    private TelemetryRecorder recorder;

    private LanguageServer() {
        this.handlers = new HashSet<>();
    }

    public static void main(final String[] args) {
        new LanguageServer().start();
    }

    @Override
    public Exceptional<CompletableFuture<Integer>> start() {

        final var server = new Server(SERVER_PORT.get(), this.handlers, this.recorder);

        try {
            server.start();
            return Exceptional.of(CompletableFuture.completedFuture(0));
        }
        catch (final Exception e) {
            return Exceptional.ofException(e);
        }
    }

    public void accept(final ProtocolHandler handler) {
        this.handlers.add(handler);
    }

    public static class MetaClass
        implements build.spin.Server.MetaClass {

        @Override
        public boolean isDetectedIn(final FileSystem fileSystem) {
            return true;
        }
    }
}
