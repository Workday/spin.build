package build.spin.module.languageserver.protocol.params;

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

import build.spin.module.languageserver.protocol.data.WorkspaceFolder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;

/**
 * Initialize Request parameters.
 * <p>
 * Method: `initialize`
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#initialize">Initialize Request</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class InitializeParams
    extends WorkDoneProgressParams {

    @JsonProperty("processId")
    public int processId;

    @JsonProperty("rootPath")
    public String rootPath;

    @JsonProperty("rootUri")
    public URI rootUri;

    @JsonProperty("initializationOptions")
    public JsonNode initializationOptions;

    @JsonProperty("trace")
    public String trace;

    @JsonProperty("workspaceFolders")
    public List<WorkspaceFolder> workspaceFolders;
}
