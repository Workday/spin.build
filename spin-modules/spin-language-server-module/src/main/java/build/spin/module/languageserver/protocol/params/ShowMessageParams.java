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

import build.spin.module.languageserver.protocol.data.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Show Message
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#window_showMessage">Show Message</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShowMessageParams {

    @JsonProperty("type")
    public MessageType type;

    @JsonProperty("message")
    public String message;

    public ShowMessageParams(final MessageType type, final String message) {
        this.type = type;
        this.message = message;
    }
}
