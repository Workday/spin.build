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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workDoneProgress">workDoneProgress</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class WorkDoneProgressBegin {

    @JsonProperty("kind")
    public String kind = "begin";

    @JsonProperty("title")
    public String title;

    @JsonProperty("cancellable")
    public Boolean cancellable;

    @JsonProperty("message")
    public String message;

    @JsonProperty("percentage")
    public Integer percentage;

    public WorkDoneProgressBegin() {

    }

    public WorkDoneProgressBegin(final Builder builder) {
        this.title = builder.title;
        this.cancellable = builder.cancellable;
        this.message = builder.message;
        this.percentage = builder.percentage;
    }

    public static Builder builder(final String title) {
        return new Builder(title);
    }

    public static class Builder {

        public final String title;
        private boolean cancellable;
        private String message;
        private int percentage;

        public Builder(final String title) {
            this.title = title;
        }

        public Builder cancellable(final boolean cancellable) {
            this.cancellable = cancellable;
            return this;
        }

        public Builder message(final String message) {
            this.message = message;
            return this;
        }

        public Builder percentage(final int percentage) {
            this.percentage = percentage;
            return this;
        }

        public WorkDoneProgressBegin build() {
            return new WorkDoneProgressBegin(this);
        }
    }
}
