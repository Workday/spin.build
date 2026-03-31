package build.spin.module.languageserver.protocol;

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

import build.spin.module.languageserver.protocol.data.WorkDoneProgressBegin;
import build.spin.module.languageserver.protocol.data.WorkDoneProgressEnd;
import build.spin.module.languageserver.protocol.data.WorkDoneProgressReport;
import build.spin.module.languageserver.protocol.methods.Methods;
import build.spin.module.languageserver.protocol.params.LogMessageParams;
import build.spin.module.languageserver.protocol.params.ProgressParams;
import build.spin.module.languageserver.protocol.params.PublishDiagnosticsParams;
import build.spin.module.languageserver.protocol.params.ShowMessageParams;
import build.spin.module.languageserver.protocol.params.WorkDoneProgressCreateParams;
import build.spin.module.languageserver.protocol.registrations.Registration;
import build.spin.module.languageserver.utils.WriteUtils;

import java.io.OutputStream;
import java.util.UUID;

/**
 * A proxy representing the client that has established communication with the server. The {@link ClientProxy} is meant
 * to be used for two primary purposes:
 * 1. Register capabilities with the client.
 * 2. Publish information (notifications, diagnostics, messages) to the client from the server.
 *
 * @author drew.malin
 * @since Feb-2023
 */
public class ClientProxy {

    private final OutputStream output;

    public ClientProxy(final OutputStream output) {
        this.output = output;
    }

    /**
     * Register a capability with the client.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#client_registerCapability">Register Capability</a>}
     *
     * @param capability
     * @param options
     */
    public void registerCapability(final String capability, final Object options) {
        final var message = new Registration(capability, options);
        WriteUtils.writeNotification(Methods.CLIENT_REGISTER_CAPABILITY, message, this.output);
    }

    /**
     * Log a message to the client. Logged messages are accumulated in the message log.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#window_logMessage">LogMessage</a>}
     *
     * @param message
     */
    public void logMessage(final LogMessageParams message) {
        WriteUtils.writeNotification(Methods.WINDOW_LOG_MESSAGE, message, this.output);
    }

    /**
     * Show a message in the client. Shown messages are displayed as a popup/toast-style message.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#window_showMessage">ShowMessage</a>}
     *
     * @param message
     */
    public void showMessage(final ShowMessageParams message) {
        WriteUtils.writeNotification(Methods.WINDOW_SHOW_MESSAGE, message, this.output);
    }

    /**
     * Publish diagnostics to the client.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_publishDiagnostics">PublishDiagnostics</a>}
     *
     * @param message
     */
    public void publishDiagnostics(final PublishDiagnosticsParams message) {
        WriteUtils.writeNotification(Methods.TEXT_DOCUMENT_PUBLISH_DIAGNOSTICS, message, this.output);
    }

    /**
     * Create a progress item in the client. Progress items are shown in the bottom bar of the client.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#window_workDoneProgress_create">window_workDoneProgress_create</a>}
     *
     * @param message the {@link WorkDoneProgressBegin} message to send to the client
     * @return a {@link String} token that can be used to update the progress in the client
     */
    public String progressBegin(final WorkDoneProgressBegin message) {
        final var token = UUID.randomUUID().toString();

        final var createProgressMessage = new WorkDoneProgressCreateParams(token);
        WriteUtils.writeRequest(Methods.WINDOW_WORK_DONE_PROGRESS_CREATE, createProgressMessage, this.output);

        final var beginProgressMessage = new ProgressParams(token, message);
        WriteUtils.writeNotification(Methods.PROGRESS_REQUEST, beginProgressMessage, this.output);

        return token;
    }

    /**
     * Update the progress item in the client identified by the {@link String} token parameter.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workDoneProgressReport">workDoneProgressReport</a>}
     *
     * @param token   the {@link String} token corresponding to the progress item to update
     * @param message the {@link WorkDoneProgressReport} message to send to the client
     */
    public void progressUpdate(final String token, final WorkDoneProgressReport message) {
        WriteUtils.writeNotification(Methods.PROGRESS_REQUEST, new ProgressParams(token, message), this.output);
    }

    /**
     * Close the progress item in the client identified by the {@link String} token parameter.
     * <p>
     * {@see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workDoneProgressEnd">workDoneProgressEnd</a>}
     *
     * @param token   the {@link String} token corresponding to the progress item to close
     * @param message the {@link WorkDoneProgressEnd} message to send to the client
     */
    public void progressEnd(final String token, final WorkDoneProgressEnd message) {
        WriteUtils.writeNotification(Methods.PROGRESS_REQUEST, new ProgressParams(token, message), this.output);
    }
}
