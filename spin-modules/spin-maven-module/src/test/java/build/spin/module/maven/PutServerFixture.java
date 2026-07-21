package build.spin.module.maven;

/*-
 * #%L
 * Spin Maven Module
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A minimal single-purpose HTTP/1.1 server (raw sockets, no JDK {@code jdk.httpserver} module dependency) that
 * accepts {@code PUT} requests, records their path, body, and {@code Authorization} header, and responds with
 * a fixed, configurable status code. Used by tests to fake a Maven repository accepting uploads.
 */
final class PutServerFixture implements AutoCloseable {

    /**
     * A recorded {@code PUT} request.
     */
    record Request(String path, String body, Optional<String> authorization) {
    }

    private final ServerSocket serverSocket;
    private final Thread thread;
    private final int statusCode;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    PutServerFixture(final int statusCode)
        throws IOException {

        this.statusCode = statusCode;
        this.serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        this.thread = new Thread(this::serve);
        this.thread.setDaemon(true);
        this.thread.start();
    }

    int port() {
        return this.serverSocket.getLocalPort();
    }

    List<Request> requests() {
        return this.requests;
    }

    private void serve() {
        while (this.running) {
            try (Socket socket = this.serverSocket.accept()) {
                handle(socket);
            } catch (final IOException e) {
                // expected once close() closes the server socket to unblock accept()
            }
        }
    }

    private void handle(final Socket socket)
        throws IOException {

        final var in = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));

        final var requestLine = in.readLine();
        final var path = requestLine.split(" ")[1];

        var contentLength = 0;
        String authorization = null;
        String header;
        while ((header = in.readLine()) != null && !header.isEmpty()) {
            if (header.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                contentLength = Integer.parseInt(header.substring(15).trim());
            } else if (header.regionMatches(true, 0, "Authorization:", 0, 14)) {
                authorization = header.substring(14).trim();
            }
        }

        final char[] bodyChars = new char[contentLength];
        if (contentLength > 0) {
            in.read(bodyChars);
        }

        this.requests.add(new Request(path, new String(bodyChars), Optional.ofNullable(authorization)));

        final OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 " + this.statusCode + " Status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    @Override
    public void close() {
        this.running = false;
        try {
            this.serverSocket.close();
        } catch (final IOException ignored) {
            // best-effort shutdown
        }
    }
}
