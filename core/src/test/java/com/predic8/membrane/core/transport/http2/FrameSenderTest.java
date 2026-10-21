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

import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.predic8.membrane.core.transport.http2.frame.RstStreamFrame;
import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_CANCEL;
import static com.predic8.membrane.core.transport.http2.frame.Frame.*;
import static com.predic8.membrane.core.transport.http2.frame.DataFrame.FLAG_END_STREAM;
import static com.predic8.membrane.core.transport.http2.frame.HeadersFrame.FLAG_END_HEADERS;
import static java.lang.System.nanoTime;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

class FrameSenderTest {

    @Test
    void stopFailsDataFrameWaitingForBufferSpace() throws Exception {
        final var settings = new Settings();
        final var streams = new ConcurrentHashMap<Integer, StreamInfo>();
        final var sender = new FrameSender(new ByteArrayOutputStream(), new Encoder(4096), settings, streams, "test");
        final var streamInfo = new StreamInfo(1, sender, settings, settings);
        streams.put(1, streamInfo);

        // The sender thread is not running, so the stream's buffer fills up and the next frame has to wait.
        while (streamInfo.getBufferedDataFrames().availablePermits() > 0)
            sender.sendData(dataFrame());
        final var blocked = CompletableFuture.runAsync(() -> {
            try {
                sender.sendData(dataFrame());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrows(TimeoutException.class, () -> blocked.get(200, MILLISECONDS));

        sender.stop();

        final var e = assertThrows(ExecutionException.class, () -> blocked.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, e.getCause().getCause());
    }

    /**
     * Closing the stream drains its buffer, which wakes a writer waiting for buffer space. Its frame must not be
     * left in the buffer of the removed stream: nothing would send it, and a trailer waiting for the buffer to
     * empty would wait forever.
     */
    @Test
    void dataFrameWaitingForBufferSpaceIsDroppedWhenStreamCloses() throws Exception {
        final var settings = new Settings();
        final var streams = new ConcurrentHashMap<Integer, StreamInfo>();
        final var sender = new FrameSender(new ByteArrayOutputStream(), new Encoder(4096), settings, streams, "test");
        final var streamInfo = new StreamInfo(1, sender, settings, settings);
        streams.put(1, streamInfo);
        streamInfo.sendHeaders();

        while (streamInfo.getBufferedDataFrames().availablePermits() > 0)
            sender.sendData(dataFrame());
        final var blocked = CompletableFuture.runAsync(() -> {
            try {
                sender.sendData(dataFrame());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrows(TimeoutException.class, () -> blocked.get(200, MILLISECONDS));

        streamInfo.receivedRstStream(ERROR_CANCEL);

        blocked.get(2, TimeUnit.SECONDS);
        assertTrue(streamInfo.getDataFramesToBeSent().isEmpty());
    }

    /**
     * The writer reserves the connection's flow-control window before queuing DATA. The peer only grants window
     * again for DATA it received, so dropping a frame must give its window back, or other streams starve.
     */
    @Test
    void droppedDataGivesBackConnectionWindow() throws Exception {
        final var settings = new Settings();
        final var streams = new ConcurrentHashMap<Integer, StreamInfo>();
        final var sender = new FrameSender(new ByteArrayOutputStream(), new Encoder(4096), settings, streams, "test");
        final var connectionFlowControl = sender.getConnectionFlowControl();
        final var initialWindow = availableWindow(connectionFlowControl);
        final var streamInfo = new StreamInfo(1, sender, settings, settings);
        streams.put(1, streamInfo);
        streamInfo.sendHeaders();

        // Buffered frames are dropped by closing the stream, the waiting one after it wakes up.
        while (streamInfo.getBufferedDataFrames().availablePermits() > 0)
            reserveAndSendData(sender);
        final var blocked = CompletableFuture.runAsync(() -> {
            try {
                reserveAndSendData(sender);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrows(TimeoutException.class, () -> blocked.get(200, MILLISECONDS));

        streamInfo.receivedRstStream(ERROR_CANCEL);
        blocked.get(2, TimeUnit.SECONDS);
        // Dropped right away, as the stream is gone.
        reserveAndSendData(sender);

        assertEquals(initialWindow, availableWindow(connectionFlowControl));
    }

    @Test
    void sendDataAfterStopFails() {
        final var settings = new Settings();
        final var streams = new ConcurrentHashMap<Integer, StreamInfo>();
        final var sender = new FrameSender(new ByteArrayOutputStream(), new Encoder(4096), settings, streams, "test");
        streams.put(1, new StreamInfo(1, sender, settings, settings));

        sender.stop();

        assertThrows(IOException.class, () -> sender.sendData(dataFrame()));
    }

    @Test
    void dataQueuedBeforeResetIsNotSentAfterRstStream() throws Exception {
        final var settings = new Settings();
        final var streams = new ConcurrentHashMap<Integer, StreamInfo>();
        final var out = new ByteArrayOutputStream();
        final var sender = new FrameSender(out, new Encoder(4096), settings, streams, "test");
        final var streamInfo = new StreamInfo(1, sender, settings, settings);
        streams.put(1, streamInfo);
        streamInfo.sendHeaders();

        // DATA is still buffered when the stream is cancelled, e.g. because reading the request body failed.
        sender.sendData(dataFrame());
        sender.send(RstStreamFrame.construct(1, ERROR_CANCEL));

        final var thread = new Thread(sender);
        thread.start();
        while (!streamInfo.getDataFramesToBeSent().isEmpty())
            Thread.sleep(10);
        sender.stop();
        thread.join(2000);

        assertEquals(List.of(TYPE_RST_STREAM), frameTypes(out.toByteArray()));
    }

    @Test
    void headersQueuedBeforeResetAreStillSentToKeepHpackInSync() throws Exception {
        final var settings = new Settings();
        final var streams = new ConcurrentHashMap<Integer, StreamInfo>();
        final var out = new ByteArrayOutputStream();
        final var sender = new FrameSender(out, new Encoder(4096), settings, streams, "test");
        final var stream1 = new StreamInfo(1, sender, settings, settings);
        final var stream3 = new StreamInfo(3, sender, settings, settings);
        streams.put(1, stream1);
        streams.put(3, stream3);
        stream1.receivedHeaders();
        stream3.receivedHeaders();

        // Encoding adds the field to the encoder's dynamic table. The block is split into HEADERS and CONTINUATION.
        sender.send(1, (encoder, peerSettings) -> {
            final var block = encodeHeader(encoder, "x-test", "value");
            final var split = block.length / 2;
            return List.of(
                    headerFrame(TYPE_HEADERS, 0, 1, Arrays.copyOfRange(block, 0, split)),
                    headerFrame(TYPE_CONTINUATION, FLAG_END_HEADERS, 1, Arrays.copyOfRange(block, split, block.length)));
        });
        // The peer resets stream 1 while its HEADERS are still queued.
        stream1.receivedRstStream(ERROR_CANCEL);
        // Refers to the dynamic table entry added by stream 1's block.
        sender.send(3, (encoder, peerSettings) ->
                List.of(headerFrame(TYPE_HEADERS, FLAG_END_HEADERS, 3, encodeHeader(encoder, "x-test", "value"))));

        final var thread = new Thread(sender);
        thread.start();
        sender.stop();
        thread.join(2000);

        assertEquals(Map.of(1, Map.of("x-test", "value"), 3, Map.of("x-test", "value")), decodeHeaderBlocks(out.toByteArray()));
    }

    @Test
    void dataDoesNotOvertakeHeadersOfItsStream() throws Exception {
        final var settings = new Settings();
        final var streams = new ConcurrentHashMap<Integer, StreamInfo>();
        final var out = new ByteArrayOutputStream();
        final var sender = new FrameSender(out, new Encoder(4096), settings, streams, "test");
        final var streamInfo = new StreamInfo(1, sender, settings, settings);
        streams.put(1, streamInfo);

        sender.sendData(endStreamDataFrame());
        final var thread = new Thread(sender);
        thread.start();
        final var deadline = nanoTime() + MILLISECONDS.toNanos(200);
        while (!streamInfo.getDataFramesToBeSent().isEmpty() && nanoTime() < deadline)
            Thread.sleep(10);
        sender.send(1, (encoder, peerSettings) ->
                List.of(headerFrame(TYPE_HEADERS, FLAG_END_HEADERS, 1, encodeHeader(encoder, "x-test", "value"))));
        while (!streamInfo.getDataFramesToBeSent().isEmpty())
            Thread.sleep(10);
        sender.stop();
        thread.join(2000);

        assertEquals(List.of(TYPE_HEADERS, TYPE_DATA), frameTypes(out.toByteArray()));
        assertEquals(StreamState.HALF_CLOSED_LOCAL, streamInfo.getState());
    }

    private static byte[] encodeHeader(Encoder encoder, String name, String value) throws IOException {
        final var block = new ByteArrayOutputStream();
        encoder.encodeHeader(block, name.getBytes(UTF_8), value.getBytes(UTF_8), false);
        return block.toByteArray();
    }

    private static Frame headerFrame(int type, int flags, int streamId, byte[] fragment) {
        final var frame = new Frame();
        frame.fill(type, flags, streamId, fragment, 0, fragment.length);
        return frame;
    }

    /**
     * Decodes the header blocks in the written frames with a single decoder, as the peer would.
     */
    private static Map<Integer, Map<String, String>> decodeHeaderBlocks(byte[] bytes) throws IOException {
        final var decoder = new Decoder(65536, 4096);
        final var headersByStream = new HashMap<Integer, Map<String, String>>();
        final var block = new ByteArrayOutputStream();
        var offset = 0;
        while (offset < bytes.length) {
            final var length = ((bytes[offset] & 0xff) << 16) | ((bytes[offset + 1] & 0xff) << 8) | (bytes[offset + 2] & 0xff);
            final var type = bytes[offset + 3];
            final var flags = bytes[offset + 4];
            final var streamId = ((bytes[offset + 5] & 0x7f) << 24) | ((bytes[offset + 6] & 0xff) << 16) | ((bytes[offset + 7] & 0xff) << 8) | (bytes[offset + 8] & 0xff);
            if (type == TYPE_HEADERS || type == TYPE_CONTINUATION) {
                block.write(bytes, offset + 9, length);
                if ((flags & FLAG_END_HEADERS) != 0) {
                    final var headers = new HashMap<String, String>();
                    decoder.decode(new ByteArrayInputStream(block.toByteArray()),
                            (name, value, sensitive) -> headers.put(new String(name, UTF_8), new String(value, UTF_8)));
                    decoder.endHeaderBlock();
                    headersByStream.put(streamId, headers);
                    block.reset();
                }
            }
            offset += 9 + length;
        }
        return headersByStream;
    }

    private static List<Integer> frameTypes(byte[] bytes) {
        final var types = new ArrayList<Integer>();
        var offset = 0;
        while (offset < bytes.length) {
            types.add((int) bytes[offset + 3]);
            offset += 9 + (((bytes[offset] & 0xff) << 16) | ((bytes[offset + 1] & 0xff) << 8) | (bytes[offset + 2] & 0xff));
        }
        return types;
    }

    /**
     * Reserves the connection window for a DATA frame and queues it, like the body writer does.
     */
    private static void reserveAndSendData(FrameSender sender) throws IOException {
        final var frame = dataFrame();
        sender.getConnectionFlowControl().reserve(frame.getLength(), frame.getStreamId());
        sender.sendData(frame);
    }

    private static long availableWindow(PeerFlowControl flowControl) {
        synchronized (flowControl) {
            return flowControl.peerWindowSize - flowControl.peerWindowPosition;
        }
    }

    private static Frame dataFrame() {
        final var frame = new Frame();
        frame.fill(TYPE_DATA, 0, 1, new byte[1], 0, 1);
        return frame;
    }

    private static Frame endStreamDataFrame() {
        final var frame = new Frame();
        frame.fill(TYPE_DATA, FLAG_END_STREAM, 1, null, 0, 0);
        return frame;
    }
}
