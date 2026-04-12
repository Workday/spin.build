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
import build.serve.lsp.LspServer;
import build.serve.lsp.LspTransport;
import build.spin.option.ServerPort;
import jakarta.inject.Inject;

import java.nio.file.FileSystem;
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

    /**
     * The default server listening port.
     */
    @Inject
    public ServerPort SERVER_PORT = ServerPort.DEFAULT;

    @Inject
    private TelemetryRecorder recorder;

    private LanguageServer() {
    }

    public static void main(final String[] args) {
        new LanguageServer().start();
    }

    @Override
    public Exceptional<CompletableFuture<Integer>> start() {
        final var server = LspServer.builder().build();
        final var port = SERVER_PORT.get();
        final var done = new CompletableFuture<Integer>();

        if (port < 0) {
            this.recorder.info("Language Server Listening On STDIN");
            Thread.ofVirtual().name("language-server-stdio").start(() -> {
                try {
                    LspTransport.stdio(server);
                    done.complete(0);
                } catch (final Exception e) {
                    this.recorder.error("Language server STDIO error: " + e.getMessage());
                    done.completeExceptionally(e);
                }
            });
        } else {
            this.recorder.info("Language Server Listening On Port: " + port);
            Thread.ofVirtual().name("language-server-tcp").start(() -> {
                try {
                    LspTransport.tcp(server, port);
                    done.complete(0);
                } catch (final Exception e) {
                    if (!done.isDone()) {
                        this.recorder.error("Language server TCP error: " + e.getMessage());
                        done.completeExceptionally(e);
                    }
                }
            });
        }

        return Exceptional.of(done);
    }

    public static class MetaClass
        implements build.spin.Server.MetaClass {

        @Override
        public boolean isDetectedIn(final FileSystem fileSystem) {
            return true;
        }
    }
}
