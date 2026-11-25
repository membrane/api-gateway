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
package com.predic8.membrane.core.interceptor.sse;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.*;

class ServerSentEventsDemoStreamInterceptorTest {

    /**
     * The events are created while the body is written, so a plugin that reads the body, such as
     * <code>limit</code>, must fail with a hint instead of passing an empty body.
     */
    @Test
    void streamCannotBeCheckedForItsLength() throws Exception {
        final var response = streamingResponse();

        final var e = assertThrows(ReadingBodyException.class, () -> response.getResponse().checkBodyLength(1000));
        assertTrue(e.getMessage().contains("sseDemoStream"), e.getMessage());
    }

    @Test
    void streamCannotBeRead() throws Exception {
        final var response = streamingResponse();

        final var e = assertThrows(ReadingBodyException.class, () -> response.getResponse().getBody().getContent());
        assertTrue(e.getMessage().contains("sseDemoStream"), e.getMessage());
    }

    private static Exchange streamingResponse() throws Exception {
        final var exc = get("/stream").buildExchange();
        new ServerSentEventsDemoStreamInterceptor().handleRequest(exc);
        return exc;
    }
}
