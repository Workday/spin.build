package build.spin.module.languageserver.utils;

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

import build.spin.module.languageserver.jsonrpc.ErrorResponse;
import build.spin.module.languageserver.jsonrpc.Notification;
import build.spin.module.languageserver.jsonrpc.Request;
import build.spin.module.languageserver.jsonrpc.ResultResponse;
import build.spin.module.languageserver.protocol.data.Header;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Utilities for writing messages using the LSP v3 protocol.
 *
 * @author drew.malin
 * @since Jan-2023
 */
public class WriteUtils {

    private WriteUtils() {

    }

    /**
     * Write a {@link ResultResponse} to the {@link OutputStream}.
     *
     * @param id           the int id of the message
     * @param params       the {@link Object} representing the {@link ResultResponse#result} data
     * @param outputStream the {@link OutputStream} over which to write the message
     */
    @SuppressWarnings("unchecked")
    public static void writeResult(final int id,
                                   final Object params,
                                   final OutputStream outputStream) {
        final Object result;
        if (params instanceof Optional) {
            //noinspection rawtypes
            result = ((Optional) params).orElse(null);
        }
        else {
            result = new ResultResponse(id, params);
        }

        final String resultJson = JsonUtils.toJson(result);
        write(resultJson, outputStream);
    }

    /**
     * Write a {@link ErrorResponse} to the {@link OutputStream}.
     *
     * @param id           the int id of the message
     * @param error        the {@link ErrorResponse.Error} representing the {@link ErrorResponse#error} data
     * @param outputStream the {@link OutputStream} over which to write the message
     */
    public static void writeError(final int id, final ErrorResponse.Error error, final OutputStream outputStream) {
        final var result = new ErrorResponse(id, error);
        final var resultJson = JsonUtils.toJson(result);

        write(resultJson, outputStream);
    }

    public static void writeRequest(final String method, final Object params, final OutputStream outputStream) {
        final var request = new Request(1, method, params);
        final var requestJson = JsonUtils.toJson(request);

        write(requestJson, outputStream);
    }

    /**
     * Write a {@link Notification} to the {@link OutputStream}
     *
     * @param method       the {@link String} method (see {@link build.spin.module.languageserver.protocol.methods.Methods}) to send
     * @param params       the {@link Object} representing the {@link Notification#params} data
     * @param outputStream the {@link OutputStream} over which to write the message
     */
    public static void writeNotification(final String method, final Object params, final OutputStream outputStream) {
        final var result = new Notification(method, params);
        final var resultJson = JsonUtils.toJson(result);

        write(resultJson, outputStream);
    }

    /**
     * Writes the provided {@link String} as a LSP v3 message. LSP messages consist of:
     * - A {@link Header}
     * - An intermediary character sequence (see {@link Header#END_OF_HEADER}
     * - A JSON-RPC 2.0 message
     * <p>
     * Example:
     * <p>
     * Content-Length: ...\r\n\r\n
     * {
     * "jsonrpc": "2.0",
     * "id": ...
     * "method": ...
     * "params": {
     * ...
     * }
     * }
     *
     * @param message      the {@link String} message to encode and send
     * @param outputStream the {@link OutputStream} over which to send the message
     */
    private static void write(final String message, final OutputStream outputStream) {
        final var header = new Header(message);
        try {
            outputStream.write(header.getText().getBytes(StandardCharsets.UTF_8));
            outputStream.write(message.getBytes(StandardCharsets.UTF_8));
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
