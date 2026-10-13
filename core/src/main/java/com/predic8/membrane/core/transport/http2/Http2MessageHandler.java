/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.Message;

import java.io.IOException;

/**
 * Receives the messages that {@link Http2Logic} reads from an HTTP/2 connection. The server side
 * ({@link Http2ServerHandler}) receives requests, the client side ({@link Http2Client}) responses.
 * <p>
 * All methods are called on the connection's reader thread. While a method runs, no frames are read for any
 * stream of the connection, so implementations must return quickly and hand longer work to another thread.
 */
public interface Http2MessageHandler {

    /**
     * Creates the message for a new stream when its first HEADERS frame arrives: a {@code Request} on the
     * server side, a {@code Response} on the client side. {@link Http2Logic} fills in its header and body.
     */
    Message createMessage();

    /**
     * Called once per stream when its header block has been decoded. The body may not have arrived yet: if the
     * stream is still open, the message's body streams in as DATA frames are read. Trailers are added to that
     * body later and do not trigger another call.
     *
     * @param streamInfo the stream the message was received on
     * @param message the message created by {@link #createMessage()}
     * @param showSSLExceptions whether TLS errors should be logged with details
     * @param remoteAddr the peer's address
     */
    void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr);

    /**
     * Called when the peer resets a stream with RST_STREAM, after the stream has been closed. This can happen
     * before or after {@link #handleExchange} was called for that stream. Not called for resets of unknown
     * streams or stream 0; those are connection errors.
     *
     * @param streamId the reset stream
     * @param errorCode the peer's error code, see {@link com.predic8.membrane.core.transport.http2.frame.Error}
     */
    default void handleStreamReset(int streamId, int errorCode) {
    }

    /**
     * Called instead of {@link #handleExchange} when a stream's header block exceeds the header size limits.
     * The block has been decoded and discarded; no message is handed over for the stream.
     *
     * @param streamInfo the stream the header block was received on
     * @param endStream whether the peer ended the stream with the header block
     */
    void handleOversizedHeaders(StreamInfo streamInfo, boolean endStream) throws IOException;

}
