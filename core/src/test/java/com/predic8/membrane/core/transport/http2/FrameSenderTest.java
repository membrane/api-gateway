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
import com.twitter.hpack.Encoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_CANCEL;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_DATA;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_RST_STREAM;
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
        assertThrows(TimeoutException.class, () -> blocked.get(200, TimeUnit.MILLISECONDS));

        sender.stop();

        final var e = assertThrows(ExecutionException.class, () -> blocked.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, e.getCause().getCause());
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

    private static List<Integer> frameTypes(byte[] bytes) {
        final var types = new ArrayList<Integer>();
        var offset = 0;
        while (offset < bytes.length) {
            types.add((int) bytes[offset + 3]);
            offset += 9 + (((bytes[offset] & 0xff) << 16) | ((bytes[offset + 1] & 0xff) << 8) | (bytes[offset + 2] & 0xff));
        }
        return types;
    }

    private static Frame dataFrame() {
        final var frame = new Frame();
        frame.fill(TYPE_DATA, 0, 1, new byte[1], 0, 1);
        return frame;
    }
}
