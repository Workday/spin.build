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
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Completion Item component of a Completion Request.
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_completion">Completion Request</a>}
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompletionItem {

    @JsonProperty("label")
    public String label;

    @JsonProperty("kind")
    public int kind;

    @JsonProperty("detail")
    public String detail;

    @JsonProperty("documentation")
    public MarkupContent documentation;

    @JsonProperty("deprecated")
    public Boolean deprecated;

    @JsonProperty("preselect")
    public Boolean preselect;

    @JsonProperty("sortText")
    public String sortText;

    @JsonProperty("filterText")
    public String filterText;

    @JsonProperty("insertText")
    public String insertText;

    @JsonProperty("insertTextFormat")
    public Integer insertTextFormat;

    @JsonProperty("textEdit")
    public TextEdit textEdit;

    @JsonProperty("additionalTextEdits")
    public List<TextEdit> additionalTextEdits;

    @JsonProperty("commitCharacters")
    public List<Character> commitCharacters;

    @JsonProperty("command")
    public Command command;

    @JsonProperty("data")
    public JsonNode data;
}
