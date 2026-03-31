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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocumentSyncOptions">textDocumentSyncOptions</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class TextDocumentSyncOptions {

    @JsonProperty("openClose")
    public boolean openClose;

    @JsonProperty("change")
    public TextDocumentSyncKind change;

    @JsonProperty("save")
    public SaveOptions save;

    public TextDocumentSyncOptions(final boolean openClose, final TextDocumentSyncKind change, final SaveOptions save) {
        this.openClose = openClose;
        this.change = change;
        this.save = save;
    }
}
