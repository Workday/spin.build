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

import build.spin.module.languageserver.protocol.data.CompletionList;
import build.spin.module.languageserver.protocol.data.SymbolInformation;
import build.spin.module.languageserver.protocol.params.CompletionParams;
import build.spin.module.languageserver.protocol.params.DidChangeTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidCloseTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidOpenTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DidSaveTextDocumentParams;
import build.spin.module.languageserver.protocol.params.DocumentSymbolParams;
import build.spin.module.languageserver.tasks.TaskExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A handler for Language Server Protocol v3 (LSP) behavior.
 *
 * @author drew.malin
 * @since Feb-2023
 */
public interface ProtocolHandler {

    /**
     * Handler for the 'initialized' request.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#initialized">initialized</a>}
     */
    default void initialized(final ClientProxy client, final TaskExecutor<String> serverTaskExecutor) {
    }

    /**
     * Returns the {@link Set<String>} of glob patterns specifying the types of files this {@link ProtocolHandler} should be
     * notified about.
     *
     * @return the {@link Set<String>} of glob patterns specifying the types of files this {@link ProtocolHandler} should be
     * notified about
     */
    default Set<String> getWatchedFilesGlobPatterns() {
        return Collections.emptySet();
    }

    /**
     * Handler for the 'shutdown' request.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#shutdown">shutdown</a>}
     */
    default void shutdown() {
    }

    /**
     * Handler for the 'textDocument/documentSymbol' request.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_documentSymbol">documentSymbol</a>}
     *
     * @param params the {@link DocumentSymbolParams} message parameters
     * @return a {@link List<SymbolInformation>}
     */
    default List<SymbolInformation> documentSymbol(final DocumentSymbolParams params) {
        return Collections.emptyList();
    }

    /**
     * Handler for the 'textDocument/completion' request.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_completion">completion</a>}
     *
     * @param params the {@link CompletionParams} message parameters
     * @return a {@link Optional<SymbolInformation>}
     */
    default Optional<CompletionList> completion(final CompletionParams params) {
        return Optional.empty();
    }

    /**
     * Handler for the 'textDocument/didOpenTextDocument' notification.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_didOpen">textDocument_didOpen</a>}
     *
     * @param params the {@link DidOpenTextDocumentParams} notification parameter
     */
    default void didOpenTextDocument(final DidOpenTextDocumentParams params) {
    }

    /**
     * Handler for the 'textDocument/didChangeTextDocument' notification.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_didChange">textDocument_didChange</a>}
     *
     * @param params the {@link DidChangeTextDocumentParams} notification parameters
     */
    default void didChangeTextDocument(final DidChangeTextDocumentParams params) {
    }

    /**
     * Handler for the 'textDocument/didSaveTextDocument' notification.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_didSave">textDocument_didSave</a>}
     *
     * @param params the {@link DidSaveTextDocumentParams} notification parameters
     */
    default void didSaveTextDocument(final DidSaveTextDocumentParams params) {
    }

    /**
     * Handler for the 'textDocument/didCloseTextDocument' notification.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_didClose">textDocument_didClose</a>}
     *
     * @param params the {@link DidCloseTextDocumentParams} notification parameters
     */
    default void didCloseTextDocument(final DidCloseTextDocumentParams params) {
    }
}
