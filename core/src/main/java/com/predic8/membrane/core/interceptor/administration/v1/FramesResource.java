/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.tunnel.*;
import com.predic8.membrane.core.transport.ws.interceptors.*;
import com.predic8.membrane.core.transport.ws.interceptors.WebSocketRecordInterceptor.*;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * {@code /exchanges/{instance}/{id}/frames}: WebSocket messages recorded by {@code wsRecord}.
 */
final class FramesResource {

    record Frame(long seq, String time, String direction, String opcode, long length, String encoding, String payload,
                 boolean truncated) {
    }

    record FramePage(long total, int offset, int limit, long dropped, List<Frame> frames) {
    }

    private final ConsoleContext ctx;

    FramesResource(ConsoleContext ctx) {
        this.ctx = ctx;
    }

    Response frames(ApiRequest req) {
        ctx.requireSelf(req.path(0));
        final var id = req.pathLong(1);
        final var offset = req.intParam("offset", 0, 0, Integer.MAX_VALUE);
        final var limit = req.intParam("limit", 50, 1, 1000);

        final var recorders = recorders();
        if (recorders.isEmpty())
            throw notImplemented("No WebSocket messages are recorded. Add wsRecord to the flow of webSocket.");

        final var connection = recorders.stream()
                .map(r -> r.connection(id))
                .flatMap(Optional::stream)
                .findFirst();
        if (connection.isEmpty())
            return ok(new FramePage(0, offset, limit, 0, List.of()));

        final var messages = connection.get().messages();
        final var dropped = connection.get().count() - messages.size();
        final var page = messages.stream().skip(offset).limit(limit).map(FramesResource::frame).toList();
        return ok(new FramePage(messages.size(), offset, limit, Math.max(0, dropped), page));
    }

    private List<WebSocketRecordInterceptor> recorders() {
        return ctx.router().getRuleManager().getRules().stream()
                .map(WebSocketInterceptor::findIn)
                .flatMap(Optional::stream)
                .flatMap(ws -> ws.getFlow().stream())
                .filter(WebSocketRecordInterceptor.class::isInstance)
                .map(WebSocketRecordInterceptor.class::cast)
                .toList();
    }

    static Frame frame(RecordedMessage m) {
        final var text = m.opcode() == 1 ? utf8(m.payload()) : null;
        return new Frame(m.seq(), isoTime(m.time()), m.clientToServer() ? "clientToServer" : "serverToClient",
                opcode(m.opcode()), m.length(), text != null ? "text" : "base64",
                text != null ? text : Base64.getEncoder().encodeToString(m.payload()), m.truncated());
    }

    static String opcode(int opcode) {
        return switch (opcode) {
            case 0 -> "continuation";
            case 1 -> "text";
            case 2 -> "binary";
            case 8 -> "close";
            case 9 -> "ping";
            case 10 -> "pong";
            default -> "other";
        };
    }

    /**
     * @return the payload as text, or null if it is not valid UTF-8 (a truncated payload may end inside a character)
     */
    private static String utf8(byte[] payload) {
        try {
            return UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
