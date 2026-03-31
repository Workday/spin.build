package build.spin.module.languageserver.protocol.methods;

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
 * LSP Methods.
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class Methods {

    /*
     * Notification and Requests
     */
    public static final String CANCEL_REQUEST = "$/cancelRequest";
    public static final String PROGRESS_REQUEST = "$/progress";

    /*
     * Lifecycle methods
     */
    public static final String INITIALIZE = "initialize";
    public static final String INITIALIZED = "initialized";
    public static final String SHUTDOWN = "shutdown";
    public static final String EXIT = "exit";

    /*
     * Client methods
     */
    public static final String CLIENT_REGISTER_CAPABILITY = "client/registerCapability";

    /*
     * Text Document methods
     */
    public static final String TEXT_DOCUMENT_DOCUMENT_SYMBOL = "textDocument/documentSymbol";
    public static final String TEXT_DOCUMENT_COMPLETION = "textDocument/completion";
    public static final String TEXT_DOCUMENT_DID_OPEN = "textDocument/didOpen";
    public static final String TEXT_DOCUMENT_DID_CHANGE = "textDocument/didChange";
    public static final String TEXT_DOCUMENT_DID_SAVE = "textDocument/didSave";
    public static final String TEXT_DOCUMENT_DID_CLOSE = "textDocument/didClose";
    public static final String TEXT_DOCUMENT_PUBLISH_DIAGNOSTICS = "textDocument/publishDiagnostics";

    /*
     * Window methods
     */
    public static final String WINDOW_LOG_MESSAGE = "window/logMessage";
    public static final String WINDOW_SHOW_MESSAGE = "window/showMessage";
    public static final String WINDOW_WORK_DONE_PROGRESS_CREATE = "window/workDoneProgress/create";
    public static final String WINDOW_WORK_DONE_PROGRESS_CANCEL = "window/workDoneProgress/cancel";

    /*
     * Workspace methods
     */
    public static final String WORKSPACE_DID_CHANGE_WATCHED_FILES = "workspace/didChangeWatchedFiles";
    public static final String WORKSPACE_DID_CHANGE_WORKSPACE_FOLDERS = "workspace/didChangeWorkspaceFolders";
    public static final String WORKSPACE_DID_CHANGE_CONFIGURATION = "workspace/didChangeConfiguration";

    private Methods() {
    }

}
