package com.predic8.membrane.core.exchange;

import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void setRequest_readsOldBodyOnReplace() {
        assertOldBodyDrainedOnReplace(
                b -> new Request() {{setBody(b);}},
                Exchange::setRequest
        );
    }


}