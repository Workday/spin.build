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

/**
 * The "Header Part" of a LSP message.
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#headerPart">Header Part</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class Header {

    public static final String HEADER_CONTENT_LENGTH = "Content-Length: ";
    public static final String END_OF_HEADER = "\r\n\r\n";

    private final String text;

    public Header(final String message) {
        final var len = message.length();
        this.text = String.format("%s%d%s", HEADER_CONTENT_LENGTH, len, END_OF_HEADER);
    }

    public String getText() {
        return this.text;
    }
}
