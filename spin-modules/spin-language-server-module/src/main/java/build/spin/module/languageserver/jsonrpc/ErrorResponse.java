package build.spin.module.languageserver.jsonrpc;

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

/**
 * A JSON-RPC 2.0 Response Object of type Error
 * <p>
 * {@see <a href="https://www.jsonrpc.org/specification#response_object">Response Object</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse
    extends Message {

    @JsonProperty("id")
    public int id;

    @JsonProperty("error")
    public Error error;

    public ErrorResponse(final int id, final Error error) {
        this.id = id;
        this.error = error;
    }

    /**
     * {@see <a href="https://www.jsonrpc.org/specification#error_object">Error Object</a>}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {

        public static final int INTERNAL_ERROR = -32603;

        @JsonProperty("code")
        public int code;

        @JsonProperty("message")
        public String message;

        @JsonProperty("data")
        public Object data;

        public Error(final int code, final String message, final Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public static Error internalError(final String message) {
            return new Error(INTERNAL_ERROR, message, null);
        }
    }
}
