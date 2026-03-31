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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A JSON-RPC 2.0 Response Object of type Notification
 * <p>
 * {@see <a href="https://www.jsonrpc.org/specification#notification">Notification</a>}
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class Notification
    extends Message {

    @JsonProperty("method")
    public String method;

    @JsonProperty("params")
    public Object params;

    public Notification(final String method, final Object params) {
        this.method = method;
        this.params = params;
    }
}
