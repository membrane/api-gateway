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

package com.predic8.membrane.core.http;

import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.BodyCollectingMessageObserver.Strategy.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyCollectingMessageObserverTest {

    private static final String CONTENT = "{\"name\":\"Ice cream\"}";

    @Test
    void readBody() {
        final var body = body();
        final var observer = observe(body);
        body.read();
        assertEquals(CONTENT, observer.collected);
    }

    @Test
    void streamedBody() {
        final var body = body();
        final var observer = observe(body);
        body.write(new PlainBodyTransferer(new ByteArrayOutputStream()), false);
        assertEquals(CONTENT, observer.collected);
    }

    /**
     * A body nobody read, e.g. of a request the gateway answered itself, is discarded at the end of
     * the exchange. The observer still gets the content.
     */
    @Test
    void discardedBody() {
        final var body = body();
        final var observer = observe(body);
        body.discard();
        assertEquals(CONTENT, observer.collected);
    }

    @Test
    void discardedChunkedBody() {
        final var body = new ChunkedBody(new ByteArrayInputStream("%x\r\n%s\r\n0\r\n\r\n".formatted(CONTENT.length(), CONTENT).getBytes(UTF_8)));
        final var observer = observe(body);
        body.discard();
        assertEquals(CONTENT, observer.collected);
    }

    private static Body body() {
        return new Body(new ByteArrayInputStream(CONTENT.getBytes(UTF_8)), CONTENT.length());
    }

    private static CollectingObserver observe(AbstractBody body) {
        final var observer = new CollectingObserver();
        body.addObserver(observer);
        return observer;
    }

    private static class CollectingObserver extends BodyCollectingMessageObserver {

        String collected;

        CollectingObserver() {
            super(ERROR, -1);
        }

        @Override
        public void bodyRequested(AbstractBody body) {
        }

        @Override
        public void bodyComplete(AbstractBody body) {
            collected = new String(getBody(body).getContent(), UTF_8);
        }
    }
}
