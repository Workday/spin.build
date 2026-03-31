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

import build.spin.module.languageserver.protocol.data.Diagnostic;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#publishDiagnosticsParams">publishDiagnosticsParams</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublishDiagnosticsParams {

    @JsonProperty("uri")
    public URI uri;

    @JsonProperty("diagnostics")
    public List<Diagnostic> diagnostics;

    private PublishDiagnosticsParams(final Builder builder) {
        this.uri = builder.uri;
        this.diagnostics = builder.diagnostics;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final ArrayList<Diagnostic> diagnostics;
        private URI uri;

        private Builder() {
            this.diagnostics = new ArrayList<>();
        }

        public Builder uri(final URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder diagnostics(final Diagnostic... diagnostics) {
            this.diagnostics.addAll(Arrays.stream(diagnostics).toList());
            return this;
        }

        public PublishDiagnosticsParams build() {
            return new PublishDiagnosticsParams(this);
        }
    }
}
