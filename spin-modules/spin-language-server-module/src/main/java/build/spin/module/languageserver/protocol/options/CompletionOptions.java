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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Completion Options
 * <p>
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#completionOptions">Completion Options</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompletionOptions
    extends WorkDoneProgressOptions {

    @JsonProperty("resolveProvider")
    public Boolean resolveProvider;

    @JsonProperty("triggerCharacters")
    public List<String> triggerCharacters;

    public CompletionOptions(final Boolean resolveProvider,
                             final List<String> triggerCharacters,
                             final Boolean workDoneProgress) {
        super(workDoneProgress);
        this.resolveProvider = resolveProvider;
        this.triggerCharacters = triggerCharacters;
    }
}
