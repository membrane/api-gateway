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

package com.predic8.membrane.core.exchange;

import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractExchangeTest {

    private static final byte[] PAYLOAD = "0123456789abcdefghij".getBytes();

    private static Body body(String s) {
        return new Body(new ByteArrayInputStream(s.getBytes()), s.getBytes().length);
    }

    private static <M extends Message> void assertOldBodyDrainedOnReplace(Function<Body, M> msgFactory, BiConsumer<Exchange, M> setter) {
        Exchange exc = new Exchange(null);

        Body oldBody = body("old");
        M m1 = msgFactory.apply(oldBody);
        setter.accept(exc, m1);

        assertFalse(oldBody.isRead());

        Body newBody = body("new");
        M m2 = msgFactory.apply(newBody);
        setter.accept(exc, m2);

        assertTrue(oldBody.isRead(), "old body must be read/drained when message is replaced");
    }

    @Test
    void setResponse_readsOldBodyOnReplace() {
        assertOldBodyDrainedOnReplace(
                b -> {
                    Response r = Response.ok().build();
                    r.setBody(b);
                    return r;
                },
                Exchange::setResponse
        );
    }

    @Test
    void setRequest_readsOldBodyOnReplace() {
        assertOldBodyDrainedOnReplace(
                b -> new Request() {{setBody(b);}},
                Exchange::setRequest
        );
    }

    @Test
    void setRequest_keepsBodyWhenSameMessageIsSetAgain() {
        assertSameMessageKeepsBody(
                b -> {
                    final var request = new Request() {{setBody(b);}};
                    request.getHeader().setContentLength(PAYLOAD.length);
                    return request;
                },
                Exchange::setRequest
        );
    }

    @Test
    void setResponse_keepsBodyWhenSameMessageIsSetAgain() {
        assertSameMessageKeepsBody(
                b -> {
                    final var response = Response.ok().build();
                    response.setBody(b);
                    response.getHeader().setContentLength(PAYLOAD.length);
                    return response;
                },
                Exchange::setResponse
        );
    }

    @Test
    void setRequest_discardsBodyWhenCleared() {
        assertBodyDiscardedWhenCleared(
                b -> new Request() {{setBody(b);}},
                Exchange::setRequest
        );
    }

    @Test
    void setResponse_discardsBodyWhenCleared() {
        assertBodyDiscardedWhenCleared(
                b -> {
                    final var response = Response.ok().build();
                    response.setBody(b);
                    return response;
                },
                Exchange::setResponse
        );
    }

    private static <M extends Message> void assertSameMessageKeepsBody(Function<Body, M> msgFactory, BiConsumer<Exchange, M> setter) {
        final var exc = new Exchange(null);
        final var body = new Body(new ByteArrayInputStream(PAYLOAD), PAYLOAD.length);
        final var message = msgFactory.apply(body);
        setter.accept(exc, message);

        assertFalse(body.isRead());

        setter.accept(exc, message);

        assertFalse(body.isRead(), "same message must keep its unread body");
        assertEquals(String.valueOf(PAYLOAD.length), message.getHeader().getFirstValue("Content-Length"));
        assertArrayEquals(PAYLOAD, body.getContent());
    }

    private static <M extends Message> void assertBodyDiscardedWhenCleared(Function<Body, M> msgFactory, BiConsumer<Exchange, M> setter) {
        final var exc = new Exchange(null);
        final var body = body("old");
        setter.accept(exc, msgFactory.apply(body));

        assertFalse(body.isRead());

        setter.accept(exc, null);

        assertTrue(body.isRead(), "previous body must be discarded when the message is cleared");
    }


}