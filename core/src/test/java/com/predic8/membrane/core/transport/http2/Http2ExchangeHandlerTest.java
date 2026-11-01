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

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.createHeadersFrames;
import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.writeMessageBody;
import static com.predic8.membrane.core.transport.http2.frame.DataFrame.FLAG_END_STREAM;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class Http2ExchangeHandlerTest {

    private static final String BODY = "0123456789abcdefghijklmnopqrstuv";

    private final Settings peerSettings = new Settings();
    private final ConcurrentHashMap<Integer, StreamInfo> streams = new ConcurrentHashMap<>();
    private final FrameSender sender = new FrameSender(new ByteArrayOutputStream(), new Encoder(4096), peerSettings, streams, "test");

    @AfterEach
    void stopSender() {
        // Wakes a writer still waiting for window, so it does not outlive a failed test.
        sender.stop();
        sender.getConnectionFlowControl().senderStopped();
    }

    @Test
    void requestWithHostHeaderSendsAuthority() throws Exception {
        final var headers = encodeRequestHeaders(get("/foo").header("Host", "example.com").build());
        assertEquals("example.com", headers.get(":authority"));
        assertFalse(headers.containsKey("host"));
    }

    @Test
    void requestWithoutHostHeaderOmitsAuthority() throws Exception {
        final var headers = encodeRequestHeaders(get("/foo").build());
        assertEquals("/foo", headers.get(":path"));
        assertFalse(headers.containsKey(":authority"));
    }

    /**
     * The peer grants more window only for DATA it received (h2spec http2/6.9.1/1). Waiting until a whole chunk
     * fits into the window waits forever, so the writer has to send what the window allows.
     */
    @Test
    void smallStreamWindowSendsPartialDataFrame() throws Exception {
        final var streamInfo = openStream(1);
        writeBodyAsync(streamInfo);
        assertEquals(1, nextDataFrame(streamInfo).getLength());
        assertEquals(0, availableWindow(streamInfo.getPeerFlowControl()));
    }

    /**
     * The stream grants the whole body, the connection only 1 byte. The stream window reserved beyond that must
     * be given back correctly, or later frames would not fit or would exceed it.
     */
    @Test
    void bodyIsSentThroughASmallConnectionWindow() throws Exception {
        final var streamInfo = openStream(peerSettings.getInitialWindowSize());
        sender.getConnectionFlowControl().reserve(peerSettings.getInitialWindowSize() - 1, 3);
        final var write = writeBodyAsync(streamInfo);

        assertEquals(BODY, receiveBody(streamInfo, length -> {
            // The writer reserves window for each frame before it queues it: the 1 byte granted is used up.
            assertEquals(0, availableWindow(sender.getConnectionFlowControl()));
            sender.getConnectionFlowControl().increment(length);
        }));
        write.get(2, SECONDS);
        assertEquals(peerSettings.getInitialWindowSize() - BODY.length(), availableWindow(streamInfo.getPeerFlowControl()));
    }

    @Test
    void windowOpenedWhileWaitingSendsWhatFits() throws Exception {
        final var streamInfo = openStream(0);
        writeBodyAsync(streamInfo);
        assertNull(streamInfo.getDataFramesToBeSent().poll(200, MILLISECONDS));

        streamInfo.getPeerFlowControl().increment(1);

        assertEquals(1, nextDataFrame(streamInfo).getLength());
    }

    @Test
    void bodyIsSentWhileThePeerGrantsWindowOnlyForReceivedData() throws Exception {
        final var streamInfo = openStream(1);
        final var write = writeBodyAsync(streamInfo);

        assertEquals(BODY, receiveBody(streamInfo, length -> windowUpdate(streamInfo, length)));
        write.get(2, SECONDS);
    }

    /**
     * Receives DATA frames until END_STREAM, granting window only for what arrived, as the peer would. The window
     * the tests start with is 1 byte, so no frame may be longer.
     */
    private static String receiveBody(StreamInfo streamInfo, IntConsumer grantWindow) throws InterruptedException {
        final var received = new ByteArrayOutputStream();
        Frame frame;
        do {
            frame = nextDataFrame(streamInfo);
            assertTrue(frame.getLength() <= 1, "DATA frame exceeds the peer's window: " + frame.getLength());
            if (frame.getLength() > 0) {
                received.write(frame.getContent(), 0, frame.getLength());
                grantWindow.accept(frame.getLength());
            }
        } while ((frame.getFlags() & FLAG_END_STREAM) == 0);
        return received.toString(US_ASCII);
    }

    private static long availableWindow(PeerFlowControl flowControl) {
        synchronized (flowControl) {
            return flowControl.peerWindowSize - flowControl.peerWindowPosition;
        }
    }

    /**
     * The connection window keeps its default size: it is created with the FrameSender, before the peer's
     * SETTINGS change the initial window size for streams.
     */
    private StreamInfo openStream(int initialStreamWindow) {
        peerSettings.setInitialWindowSize(initialStreamWindow);
        final var streamInfo = new StreamInfo(1, sender, peerSettings, new Settings());
        streams.put(1, streamInfo);
        return streamInfo;
    }

    private CompletableFuture<Void> writeBodyAsync(StreamInfo streamInfo) {
        return CompletableFuture.runAsync(() -> writeMessageBody(1, streamInfo, sender, peerSettings,
                sender.getConnectionFlowControl(), ok(BODY).build(), false));
    }

    /** Takes the next DATA frame off the stream's buffer, as the (not running) FrameSender thread would. */
    private static Frame nextDataFrame(StreamInfo streamInfo) throws InterruptedException {
        final var frame = streamInfo.getDataFramesToBeSent().poll(2, SECONDS);
        assertNotNull(frame, "No DATA frame sent within 2s");
        streamInfo.getBufferedDataFrames().release();
        return frame;
    }

    private void windowUpdate(StreamInfo streamInfo, int increment) {
        streamInfo.getPeerFlowControl().increment(increment);
        sender.getConnectionFlowControl().increment(increment);
    }

    /**
     * Values are written byte for byte, as by the HTTP/1.1 writer, instead of replacing non-ASCII characters.
     */
    @Test
    void nonAsciiValueIsEncodedByteForByte() throws Exception {
        final var headers = encodeRequestHeaders(get("/foo").header("X-Foo", "café").build());
        assertEquals("café", headers.get("x-foo"));
    }

    @Test
    void headerNamesAreLowerCasedIndependentOfDefaultLocale() throws Exception {
        final var defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            final var headers = encodeRequestHeaders(get("/foo").header("TITLE", "x").build());
            assertEquals("x", headers.get("title"));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static Map<String, String> encodeRequestHeaders(Request request) throws IOException {
        final var frames = createHeadersFrames(request, request.getHeader(), 1, new Encoder(4096), new Settings(), false);
        assertEquals(1, frames.size());
        final var headersFrame = frames.getFirst().asHeaders();

        final var headers = new LinkedHashMap<String, String>();
        final var decoder = new Decoder(8192, 4096);
        decoder.decode(new ByteArrayInputStream(headersFrame.getContent(), headersFrame.getHeaderBlockStartIndex(),
                        headersFrame.getHeaderBlockLength()),
                (name, value, sensitive) -> headers.put(new String(name, ISO_8859_1), new String(value, ISO_8859_1)));
        decoder.endHeaderBlock();
        return headers;
    }
}
