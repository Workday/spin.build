package build.spin.module.languageserver.protocol.data;

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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.Optional;

/**
 * Text Document Identifier
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocumentIdentifier">Text Document Identifier</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextDocumentIdentifier {

    private static final String EXTENSION_DELIMITER = ".";

    @JsonProperty("uri")
    public URI uri;

    public Optional<String> getFileExtension() {
        // LSP URIs are represented as Strings
        final var uriString = this.uri.toString();

        return Optional.of(uriString)
            .filter(u -> u.contains(EXTENSION_DELIMITER))
            .map(s -> s.substring(uriString.lastIndexOf(EXTENSION_DELIMITER) + 1));
    }
}
