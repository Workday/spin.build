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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Text Document Synchronization
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_synchronization">Text Document Synchronization</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum TextDocumentSyncKind {

    /**
     * Documents should not be synced at all.
     */
    None(0),

    /**
     * Documents are synced by always sending the full content
     * of the document.
     */
    Full(1),

    /**
     * Documents are synced by sending the full content on open.
     * After that only incremental updates to the document are
     * sent.
     */
    Incremental(2),
    ;

    private final int value;

    TextDocumentSyncKind(final int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return this.value;
    }
}
