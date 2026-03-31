package build.spin.module.languageserver.protocol;

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

import build.base.telemetry.TelemetryRecorder;
import build.spin.module.languageserver.EndOfStreamException;
import build.spin.module.languageserver.jsonrpc.ErrorResponse;
import build.spin.module.languageserver.jsonrpc.Request;
import build.spin.module.languageserver.protocol.data.MessageType;
import build.spin.module.languageserver.protocol.data.StopRequest;
import build.spin.module.languageserver.protocol.data.TextDocumentIdentifier;
import build.spin.module.languageserver.protocol.methods.Methods;
import build.spin.module.languageserver.protocol.options.CompletionOptions;
import build.spin.module.languageserver.protocol.options.DefinitionOptions;
import build.spin.module.languageserver.protocol.options.DiagnosticOptions;
import build.spin.module.languageserver.protocol.options.DocumentSymbolOptions;
import build.spin.module.languageserver.protocol.options.ExecuteCommandOptions;
import build.spin.module.languageserver.protocol.options.ReferenceOptions;
import build.spin.module.languageserver.protocol.options.SaveOptions;
import build.spin.module.languageserver.protocol.options.ServerCapabilities;
import build.spin.module.languageserver.protocol.options.TextDocumentSyncKind;
import build.spin.module.languageserver.protocol.options.TextDocumentSyncOptions;
import build.spin.module.languageserver.protocol.options.WorkspaceSymbolOptions;
import build.spin.module.languageserver.protocol.params.CancelParams;
import build.spin.module.languageserver.protocol.params.CompletionParams;
import build.spin.module.languageserver.protocol.params.DidChangeTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidCloseTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidOpenTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidSaveTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DocumentSymbolParams;
import build.spin.module.languageserver.protocol.params.InitializeResult;
import build.spin.module.languageserver.protocol.params.ShowMessageParams;
import build.spin.module.languageserver.protocol.params.WorkDoneProgressCancelParams;
import build.spin.module.languageserver.protocol.registrations.DidChangeWatchedFilesRegistrationOptions;
import build.spin.module.languageserver.tasks.CancellableTaskExecutor;
import build.spin.module.languageserver.tasks.TaskExecutor;
import build.spin.module.languageserver.utils.JsonUtils;
import build.spin.module.languageserver.utils.WriteUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

import static build.spin.module.languageserver.protocol.data.Header.END_OF_HEADER;
import static build.spin.module.languageserver.protocol.data.Header.HEADER_CONTENT_LENGTH;

/**
 * The primary server communicating over the Language Server Protocol (LSP) with a client. Communication to the client
 * may be performed using a {@link ClientProxy}. LSP-level messages are handled by language-specific
 * {@link ProtocolHandler}s.
 *
 * @author drew.malin
 * @since Feb-2023
 */
public class Server {
    private static final int END_OF_STREAM = -1;
    private final int server_port;
    private final Set<ProtocolHandler> handlers;
    private final TaskExecutor<String> serverTaskExecutor;
    private final TelemetryRecorder recorder;

    private int connections;

    public Server(final int serverPort, final Set<ProtocolHandler> handlers, final TelemetryRecorder recorder) {
        this.handlers = handlers;
        this.serverTaskExecutor = new CancellableTaskExecutor<>(
            Executors.newFixedThreadPool(5), recorder);
        this.recorder = recorder;
        this.server_port = serverPort;
    }

    /**
     * Start Language Server as TCP/IP socket server. The server is multithreaded.
     *
     */
    public void start() {

        // listen on STDIN
        if (server_port < 0) {
            this.recorder.info("Language Server Listening On STDIN ");
            try {
               fromStream(System.in, System.out, this.handlers, this.recorder).listenAndServe();
               return;
            }
            catch (final Exception e) {
               this.recorder.info("Exception  " + e);
               System.exit(-1);
            }
        }

        // listen on SOCKFD
        try (ServerSocket serverSocket = new ServerSocket(server_port)) {
            this.recorder.info("Language Server Listening On: " + serverSocket.getLocalSocketAddress());

            while (true) {
                final Socket connection = serverSocket.accept();
                this.serverTaskExecutor.scheduleTask(String.valueOf(connections++),
                        () -> {
                            try {
                                fromSocket(connection, this.handlers, this.recorder).listenAndServe();
                            }
                            catch (final Exception e) {
                                this.recorder.info("Exception when processing client connection " + e);
                            }
                            finally {
                                try {
                                    connection.close();
                                }
                                catch (final IOException io) {
                                    this.recorder.info("Failed to close the client socket.");
                                }
                            }
                        });
            }
        }
        catch (final IOException e) {
            this.recorder.error("Failed to creat ServerSocket for LanguageServer " + e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Create {@link ProcessServerConnection} for socket connection.
     *
     * @param connection the socket
     * @param handlers the protocol handlers
     * @param recorder the recorder
     *
     * @return an instance of {@link ProcessServerConnection}
     */
    static ProcessServerConnection fromSocket(final Socket connection,
            final Set<ProtocolHandler> handlers,
            final TelemetryRecorder recorder) {
        final InputStream is;
        final OutputStream os;

        try {
            is = connection.getInputStream();
            os = connection.getOutputStream();
        }
        catch (final Exception e) {
            final var message = "Failed to obtain InputStream/OutputStream from the socket connection";
            recorder.error(message);
            throw new RuntimeException(message, e);
        }

        return fromStream(is, os, handlers, recorder);
    }

    /**
     * Create {@link ProcessServerConnection} based on the specified {@link InputStream} and {@link OutputStream}.
     *
     * @param inputStream the InputStream
     * @param outputStream the OutputStream
     * @param handlers the protocol handlers
     * @param recorder the recorder
     *
     * @return an instance of {@link ProcessServerConnection}
     */
    static ProcessServerConnection fromStream(final InputStream inputStream,
        final OutputStream outputStream,
        final Set<ProtocolHandler> handlers,
        final TelemetryRecorder recorder) {

        return new ProcessServerConnection(inputStream, outputStream, handlers, recorder);
    }

    /**
     *  LSP server connection that provide Language Server Protocol message processing.
     *
     */
    static class ProcessServerConnection {

        private final InputStream input;
        private final OutputStream output;
        private final Set<ProtocolHandler> handlers;
        private final TaskExecutor<Integer> clientTaskExecutor;
        private final TaskExecutor<String> serverTaskExecutor;
        private final TelemetryRecorder recorder;

      /**
       * The constructor.
       *
       * @param input the InputStream
       * @param output the OutputStream
       * @param handlers the handlers for message requests
       * @param recorder the recorder
       */
      ProcessServerConnection(final InputStream input, final OutputStream output, final Set<ProtocolHandler> handlers,
                              final TelemetryRecorder recorder) {
          try {
              this.input = input;
              this.output = output;
              this.handlers = handlers;
              this.recorder = recorder;
              this.serverTaskExecutor = new CancellableTaskExecutor<>(
                  Executors.newSingleThreadExecutor(), recorder);
              this.clientTaskExecutor = new CancellableTaskExecutor<>(
                  Executors.newFixedThreadPool(5), recorder);
          }
          catch (final Exception e) {
              throw new RuntimeException("Failed to initialize server connection processing ", e);
          }
      }

        /**
         * Listen for general LSP-over-JSONRPC messages, converting them into commands or delegating them to the
         * LSPHandlers.
         */
        public void listenAndServe() throws Exception {

            // InputStream may return -1 before client's first message
            while (this.input.available() == 0) {}

            while (true) {
                // Retrieve the next message on the input stream, trying again on deserialization failure.
                final Request request;
                try {
                    request = readRequest(this.input);
                }
                catch (final JsonProcessingException e) {
                    this.recorder.error(e, "Failed to parse request");
                    continue;
                }

                // A null request shouldn't be possible, but won't be usable. Try again.
                if (request == null) {
                    this.recorder.warn("Invalid request: null");
                    continue;
                }

                // If a stop-request is received, stop listening and exit.
                if (isStopRequest(request)) {
                    this.recorder.info("Stop request received.");
                    break;
                }

                // If an invalid request is received, try again.
                if (!isParseableRequest(request)) {
                    this.recorder.warn("Skipping invalid request.");
                    continue;
                }

                // If a cancel client request is made, attempt to find and cancel the identified request.
                if (isCancelClientRequest(request)) {
                    this.recorder.info("Cancel client request received.");
                    cancelClientRequest(request);
                    continue;
                }

                // If a cancel server request is made, attempt to find and cancel the identified request.
                if (isCancelServerRequest(request)) {
                    this.recorder.info("Cancel server request received.");
                    cancelServerRequest(request);
                    continue;
                }

                // Handle the request
                scheduleClientTask(request);
            }

            stop();
        }

        private void scheduleClientTask(final Request request){
            final var task = handleRequest(request);
            this.clientTaskExecutor.scheduleTask(request.id, task);
        }

        /*
         * Handle a LSP request.
         */
        private Runnable handleRequest(final Request request){
            this.recorder.warn("Handling request %s: %s", request.id, request.method);
            try {
                switch (request.method) {
                case Methods.INITIALIZE -> {
                    return onInitialize(request);
                }
                case Methods.INITIALIZED -> {
                    return onInitialized();
                }
                case Methods.SHUTDOWN -> {
                    return onShutdown(request);
                }
                case Methods.EXIT -> {
                    return onExit(request);
                }
                case Methods.TEXT_DOCUMENT_DOCUMENT_SYMBOL -> {
                    return onTextDocumentDocumentSymbol(request);
                }
                case Methods.TEXT_DOCUMENT_COMPLETION -> {
                    return onTextDocumentCompletion(request);
                }
                case Methods.TEXT_DOCUMENT_DID_OPEN -> {
                    return onTextDocumentDidOpen(request);
                }
                case Methods.TEXT_DOCUMENT_DID_CHANGE -> {
                    return onTextDocumentDidChange(request);
                }
                case Methods.TEXT_DOCUMENT_DID_SAVE -> {
                    return onTextDocumentDidSave(request);
                }
                case Methods.TEXT_DOCUMENT_DID_CLOSE -> {
                    return onTextDocumentDidClose(request);
                }
                default -> {
                    this.recorder.warn("Unknown method: %s", request.method);
                    return () -> {
                        // no-op
                    };
                }
                }
            }
            catch (final Exception e) {
                return onException(e, request);
            }
        }

        /*
         * Handler for the 'initialize' request.
         *
         * See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#initialize
         */
        private Runnable onInitialize(final Request request){
            return () -> {
                final var serverCapabilities = new ServerCapabilities();

                final var completionTriggerCharacters = List.of(".");
                final var completionOptions = new CompletionOptions(true,
                    completionTriggerCharacters,true);

                serverCapabilities.setCompletionProvider(completionOptions);
                serverCapabilities.setTextDocumentSyncOptions(
                    new TextDocumentSyncOptions(true, TextDocumentSyncKind.Full, new
                        SaveOptions(true)));
                serverCapabilities.setExecuteCommandOptions(
                    new ExecuteCommandOptions(new String[] {}, true
                    ));
                serverCapabilities.setDiagnosticOptions(
                    new DiagnosticOptions(true));
                serverCapabilities.setDocumentSymbolProvider(
                    new DocumentSymbolOptions(null, true));
                serverCapabilities.setWorkspaceSymbolProvider(new WorkspaceSymbolOptions(null, true));
                serverCapabilities.setReferencesProvider(new ReferenceOptions(true));
                serverCapabilities.setDefinitionProvider(
                    new DefinitionOptions(true));

                final var response = new InitializeResult(serverCapabilities);

                this.recorder.warn("writing: %s", response);
                WriteUtils.writeResult(request.id, response, this.output);
            };
        }

        /*
         * Handler for the 'initialized' request on behalf of all LSPHandlers.
         *
         * See: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#initialized
         */
        private Runnable onInitialized() {
            return () -> {
                final var optionsBuilder = DidChangeWatchedFilesRegistrationOptions.builder();
                for (final var handler : this.handlers) {
                    for (final var pattern : handler.getWatchedFilesGlobPatterns()) {
                        optionsBuilder.fileSystemWatch(pattern);
                    }
                }

                final var client = new ClientProxy(this.output);
                final var options = optionsBuilder.build();
                client.registerCapability(Methods.WORKSPACE_DID_CHANGE_WATCHED_FILES, options);
                client.showMessage(new ShowMessageParams(MessageType.Info, "Hello from spin!"));

                for (final var handler : this.handlers) {
                    handler.initialized(client, this.serverTaskExecutor);
                }
            };
        }

        private Runnable onShutdown(final Request request){
            return () -> {
                WriteUtils.writeResult(request.id, null, this.output);
                for (final var handler : this.handlers) {
                    handler.shutdown();
                }
            };
        }

        private Runnable onExit(final Request request) {
            return () -> {
            };
        }

        private Runnable onTextDocumentDocumentSymbol(final Request request){
            return () -> {
                final var params = JsonUtils.fromJson(request.params, DocumentSymbolParams.class);
                final var maybeHandler = getHandlerForRequest(params.textDocumentIdentifier);
                if (maybeHandler.isPresent()) {
                    final var response = maybeHandler.get().documentSymbol(params);
                    WriteUtils.writeResult(request.id, response, this.output);
                }
                else {
                    WriteUtils.writeResult(request.id, List.of(), this.output);
                }
            };
        }

        private Runnable onTextDocumentCompletion(final Request request){
            return () -> {
                final var params = JsonUtils.fromJson(request.params, CompletionParams.class);
                final var maybeHandler = getHandlerForRequest(params.textDocument);
                if (maybeHandler.isPresent()) {
                    final var response = maybeHandler.get().completion(params);
                    WriteUtils.writeResult(request.id, response, this.output);
                }
            };
        }

        private Runnable onTextDocumentDidOpen(final Request request){
            return () -> {
                final var params = JsonUtils.fromJson(request.params, DidOpenTextDocumentParams.class);
                final var maybeHandler = getHandlerForRequest(params.textDocument);
                if (maybeHandler.isPresent()) {
                    maybeHandler.get().didOpenTextDocument(params);
                }
            };
        }

        private Runnable onTextDocumentDidChange(final Request request){
            return () -> {
                final var params = JsonUtils.fromJson(request.params, DidChangeTextDocumentParams.class);
                final var maybeHandler = getHandlerForRequest(params.textDocument);
                if (maybeHandler.isPresent()) {
                    maybeHandler.get().didChangeTextDocument(params);
                }
            };
        }

        private Runnable onTextDocumentDidSave(final Request request){
            return () -> {
                final var params = JsonUtils.fromJson(request.params, DidSaveTextDocumentParams.class);
                final var maybeHandler = getHandlerForRequest(params.textDocument);
                if (maybeHandler.isPresent()) {
                    maybeHandler.get().didSaveTextDocument(params);
                }
            };
        }

        private Runnable onTextDocumentDidClose(final Request request) {
            return () -> {
                final var params = JsonUtils.fromJson(request.params, DidCloseTextDocumentParams.class);
                final var maybeHandler = getHandlerForRequest(params.textDocument);
                if (maybeHandler.isPresent()) {
                    maybeHandler.get().didCloseTextDocument(params);
                }
            };
        }

        private Optional<ProtocolHandler> getHandlerForRequest(final TextDocumentIdentifier textDocumentIdentifier) {
            final var maybeExtension = textDocumentIdentifier.getFileExtension();
            if (maybeExtension.isEmpty()) {
                return Optional.empty();
            }
            final var extension = maybeExtension.get();

            for (final var handler : this.handlers) {
                for (final var pattern : handler.getWatchedFilesGlobPatterns()) {
                    if (pattern.substring(pattern.lastIndexOf(".") + 1).equals(extension)) {
                        return Optional.of(handler);
                    }
                }
            }
            return Optional.empty();
        }

        private Runnable onException(final Exception e,
            final Request request) {
            return () -> {
                this.recorder.error(e, e.getMessage());

                final var error = ErrorResponse.Error.internalError(e.getMessage());
                WriteUtils.writeError(request.id, error, this.output);
            };
        }

        private Request readRequest(final InputStream input)
            throws JsonProcessingException {
            Request request;
            try {
                final var data = readData(input);
                request = toRequestMessage(data);
            }
            catch (final EndOfStreamException e) {
                request = StopRequest.REQUEST;
            }

            return request;
        }

        private boolean isParseableRequest(final Request request){
            if (request.method == null || request.method.isEmpty()) {
                this.recorder.warn("Invalid request method: %s", request.method);
                return false;
            }
            return true;
        }

        private boolean isStopRequest(final Request request){
            return request == StopRequest.REQUEST;
        }

        private boolean isCancelClientRequest(final Request request){
            return request.method.equals(Methods.CANCEL_REQUEST);
        }

        private boolean isCancelServerRequest(final Request request){
            return request.method.equals(Methods.WINDOW_WORK_DONE_PROGRESS_CANCEL);
        }

        private void cancelClientRequest(final Request request){
            final var cancelRequest = JsonUtils.fromJson(request.params, CancelParams.class);
            this.clientTaskExecutor.cancelTask(cancelRequest.id);
        }

        private void cancelServerRequest(final Request request){
            final var cancelRequest = JsonUtils.fromJson(request.params, WorkDoneProgressCancelParams.class);
            this.serverTaskExecutor.cancelTask(cancelRequest.token);
        }

        private void stop() {
            this.clientTaskExecutor.shutdown();
            this.serverTaskExecutor.shutdown();
        }

        private String readData(final InputStream input){
            try {
                final var headerData = readHeader(input);
                final var contentLength = parseHeaderContentLength(headerData);
                final var data = readNBytes(input, contentLength);
                return data;
            }
            catch (final IOException e) {
                throw new RuntimeException("Failed to read data for content length", e);
            }
        }

        private Request toRequestMessage(final String data)
            throws JsonProcessingException {
            return JsonUtils.fromJson(data, Request.class);
        }

        private String readHeader(final InputStream input)
            throws IOException {
            final var header = new StringBuilder();

            while (true) {
                final var b = readByte(input);

                // headers are terminated by "\r\n\r\n"
                if (b == '\r') {
                    final var remainder = readNBytes(input, 3);
                    final var terminalMessage = b + remainder;

                    if (terminalMessage.equals(END_OF_HEADER)) {
                        break;
                    }
                    else {
                        this.recorder.error("Invalid terminal message: %s", terminalMessage);
                        throw new EndOfStreamException();
                    }
                }

                header.append(b);
            }

            return header.toString();
        }

        private int parseHeaderContentLength(final String data){
            if (!data.startsWith(HEADER_CONTENT_LENGTH)) {
                // unknown header
                return -1;
            }
            return Integer.parseInt(data.substring(HEADER_CONTENT_LENGTH.length()));
        }

        private String readNBytes(final InputStream input, final int n)
            throws IOException {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                sb.append(readByte(input));
            }
            return sb.toString();
        }

        private char readByte(final InputStream input)
            throws IOException {
            final var b = input.read();
            if (b == END_OF_STREAM) {
                throw new EndOfStreamException();
            }
            return (char) b;
        }
    }

  /**
   * For language server unit tests.
   */
  public static void startTestingServer(final InputStream input,
      final OutputStream output,
      final Set<ProtocolHandler> handlers,
      final TelemetryRecorder recorder)
      throws Exception {

          Server.fromStream(input, output, handlers, recorder).listenAndServe();
      }
}
