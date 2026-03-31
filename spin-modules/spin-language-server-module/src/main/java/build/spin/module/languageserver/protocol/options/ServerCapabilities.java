package build.spin.module.languageserver.protocol.options;

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

import build.spin.module.languageserver.protocol.ProtocolHandler;
import build.spin.module.languageserver.protocol.params.DidChangeTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidCloseTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidOpenTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidSaveTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DocumentSymbolParams;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server Capabilities
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#serverCapabilities">Server Capabilities</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerCapabilities {

    @JsonProperty("completionProvider")
    public CompletionOptions completionProvider;

    @JsonProperty("documentSymbolProvider")
    public DocumentSymbolOptions documentSymbolProvider;

    @JsonProperty("workspaceSymbolProvider")
    public WorkspaceSymbolOptions workspaceSymbolProvider;

    @JsonProperty("referencesProvider")
    public ReferenceOptions referencesProvider;

    @JsonProperty("definitionProvider")
    public DefinitionOptions definitionProvider;

    @JsonProperty("textDocumentSync")
    public TextDocumentSyncOptions textDocumentSync;

    @JsonProperty("executeCommandProvider")
    public ExecuteCommandOptions executeCommandOptions;

    @JsonProperty("diagnosticProvider")
    public DiagnosticOptions diagnosticOptions;

    /**
     * Informs the client that this language server should handle completions.
     *
     * @param completionProvider
     */
    public void setCompletionProvider(final CompletionOptions completionProvider) {
        this.completionProvider = completionProvider;
    }

    /**
     * Informs the client that this language server provides document symbol support.
     * <p>
     * When specified, clients should begin sending `textDocument/documentSymbol` events to the server. These events
     * should be handled by {@link ProtocolHandler#documentSymbol(DocumentSymbolParams)}.
     *
     * @param documentSymbolProvider
     */
    public void setDocumentSymbolProvider(final DocumentSymbolOptions documentSymbolProvider) {
        this.documentSymbolProvider = documentSymbolProvider;
    }

    /**
     * Informs the client that this language server provides workspace symbol support.
     * <p>
     * When specified, clients should begin sending `workspace/symbol` events to the server. These events should be
     * handled by (TODO!)
     *
     * @param workspaceSymbolProvider
     */
    public void setWorkspaceSymbolProvider(final WorkspaceSymbolOptions workspaceSymbolProvider) {
        this.workspaceSymbolProvider = workspaceSymbolProvider;
    }

    /**
     * Informs the client that this language server provides references support.
     *
     * @param referencesProvider
     */
    public void setReferencesProvider(final ReferenceOptions referencesProvider) {
        this.referencesProvider = referencesProvider;
    }

    /**
     * Informs the client that this language server provides goto definition support.
     *
     * @param definitionProvider
     */
    public void setDefinitionProvider(final DefinitionOptions definitionProvider) {
        this.definitionProvider = definitionProvider;
    }

    /**
     * Informs the client that this language server provides text document synchronization support.
     * <p>
     * When specified, clients should begin sending the following events to the server:
     * <ul>
     *   <li>`textDocument/didOpen`</li>
     *   <li>`textDocument/didChange`</li>
     *   <li>`textDocument/didClose`</li>
     *   <li>`textDocument/didSave`</li>
     * </ul>
     * depending on the configuration of the provided {@link TextDocumentSyncOptions} parameter.
     * <p>
     * These events should be handled by, respectively:
     * <ul>
     *   <li>{@link ProtocolHandler#didOpenTextDocument(DidOpenTextDocumentParams)}</li>
     *   <li>{@link ProtocolHandler#didChangeTextDocument(DidChangeTextDocumentParams)}</li>
     *   <li>{@link ProtocolHandler#didCloseTextDocument(DidCloseTextDocumentParams)}</li>
     *   <li>{@link ProtocolHandler#didSaveTextDocument(DidSaveTextDocumentParams)}</li>
     * </ul>
     *
     * @param textDocumentSync
     */
    public void setTextDocumentSyncOptions(final TextDocumentSyncOptions textDocumentSync) {
        this.textDocumentSync = textDocumentSync;
    }

    /**
     * Informs the client that this language server provides command execution support.
     *
     * @param executeCommandOptions
     */
    public void setExecuteCommandOptions(final ExecuteCommandOptions executeCommandOptions) {
        this.executeCommandOptions = executeCommandOptions;
    }

    /**
     * Informs the client that this language server provides diagnostic options support.
     *
     * @param diagnosticOptions
     */
    public void setDiagnosticOptions(final DiagnosticOptions diagnosticOptions) {
        this.diagnosticOptions = diagnosticOptions;
    }
}
