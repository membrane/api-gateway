package com.predic8.membrane.core.interceptor.shadowing;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

class ShadowingInterceptorTest {

    @Test
    void buildExchangeTest() throws Exception {
        Header header = new Header(){{
            add(CONTENT_TYPE, APPLICATION_JSON);
        }};
        Exchange exc = ShadowingInterceptor.buildExchange(
                new Body("foo".getBytes()),
                new Request.Builder()
                        .post("https://www.google.com")
                        .header(header)
                        .buildExchange(),
                new Target() {{
                    setUrl("https://www.predic8.com:9000/foo");
                }}
        );

        assertNotNull(exc);
        assertNotSame(header, exc.getRequest().getHeader());
        assertEquals("POST", exc.getRequest().getMethod());
        assertEquals("/foo", exc.getRequest().getUri());
        assertEquals("https://www.predic8.com:9000/foo", exc.getDestinations().get(0));
        assertEquals(APPLICATION_JSON, exc.getRequest().getHeader().getContentType());
    }
}