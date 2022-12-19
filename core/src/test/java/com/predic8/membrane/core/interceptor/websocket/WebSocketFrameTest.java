/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.websocket;

import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class WebSocketFrameTest {
    static int numberOfFrames = 100000;

    @Test
    public void testDynamicBuffer() throws Exception {


        InputStream is = new InputStream() {
            int sentFrames = 0;
            boolean first = true;

            byte[] frame = new WebSocketFrame(false, false, false, false, 2, true, new byte[4], new byte[8192 - 8 + 1]).toBytes();
            int counter = 0;

            @Override
            public int read() throws IOException {
                int my = counter++ % frame.length;
                if(counter == frame.length)
                    counter = 0;
                if (!first && sentFrames % numberOfFrames == 0)
                    return -1;
                if (counter == 0) {
                    first = false;
                    sentFrames++;
                }
                return frame[my];

            }
        };

        final int[] counter = {0};
        WebSocketFrameAssembler wsfa = new WebSocketFrameAssembler(is, null);
        wsfa.readFrames(webSocketFrame -> {
            try {
                counter[0]++;
                assertEquals(8193, webSocketFrame.toBytes().length);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
        });
        assertEquals(numberOfFrames, counter[0]);

    }
}
