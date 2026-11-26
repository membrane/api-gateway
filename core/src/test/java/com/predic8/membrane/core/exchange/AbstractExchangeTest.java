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
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.Target;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractExchangeTest {

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
    void serverOfAUrlTargetIsTheHostOfTheDestination() {
        final var target = new Target();
        target.setUrl("http://backend.example.com:3001");
        final var exc = exchangeTo(target);
        exc.getDestinations().add("http://backend.example.com:3001/orders?id=7");

        assertEquals("backend.example.com", exc.getServer());
    }

    @Test
    void serverOfAHostTargetIsTheTargetHost() {
        final var exc = exchangeTo(new Target("backend.example.com", 8080));
        exc.getDestinations().add("http://other.example.com/");

        assertEquals("backend.example.com", exc.getServer());
    }

    @Test
    void serverIsUnknownWithoutTargetOrDestination() {
        assertNull(exchangeTo(new Target()).getServer());
    }

    private static Exchange exchangeTo(Target target) {
        final var api = new APIProxy();
        api.setTarget(target);
        final var exc = new Exchange(null);
        exc.setProxy(api);
        return exc;
    }

    @Test
    void setRequest_readsOldBodyOnReplace() {
        assertOldBodyDrainedOnReplace(
                b -> new Request() {{setBody(b);}},
                Exchange::setRequest
        );
    }


}