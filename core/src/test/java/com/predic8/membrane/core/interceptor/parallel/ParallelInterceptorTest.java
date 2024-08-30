package com.predic8.membrane.core.interceptor.parallel;

import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.interceptor.parallel.ParallelInterceptor.cloneRequest;
import static org.junit.jupiter.api.Assertions.*;

class ParallelInterceptorTest {

    @Test
    void duplicateExchangeByTargets() {
        String body = "Demo Body";
        String url = "https://www.predic8.de";
        List<Exchange> exc = ParallelInterceptor.duplicateExchangeByTargets(
            new Request.Builder().body(body).buildExchange(),
            body,
            List.of(
                new Target() {{
                    setUrl(url);
                }},
                new Target() {{
                    setUrl(url);
                }}
            )
        );
        assertEquals(2, exc.size());
        assertEquals(url, exc.get(0).getDestinations().get(0));
        assertEquals(url, exc.get(1).getDestinations().get(0));
        assertEquals(body, exc.get(0).getRequest().getBodyAsStringDecoded());
        assertEquals(body, exc.get(1).getRequest().getBodyAsStringDecoded());
    }

    @Test
    void getUriFromTarget() {
        Target target1 = new Target("example.com", 80);
        Target target2 = new Target("secure.example.com", 443);
        target2.setSslParser(new SSLParser());
        Target target3 = new Target();
        target3.setUrl("https://this-is.secure");
        assertEquals("http://example.com:80", ParallelInterceptor.getUrlFromTarget(target1));
        assertEquals("https://secure.example.com:443", ParallelInterceptor.getUrlFromTarget(target2));
        assertEquals("https://this-is.secure", ParallelInterceptor.getUrlFromTarget(target3));
    }

    @Test
    void testCloneRequestNoSharedReferences() {
        Request original = new Request();
        original.setMethod("GET");
        original.setUri("/test");
        original.getHeader().add(new HeaderField("Content-Type", "application/json"));
        String body = "{\"Test\": \"Body\"";
        original.setBodyContent(body.getBytes());

        Request cloned = cloneRequest(original, body);

        assertNotSame(original, cloned);
        assertEquals(original.getMethod(), cloned.getMethod());
        assertEquals(original.getUri(), cloned.getUri());
        assertNotSame(original.getHeader(), cloned.getHeader());
        assertNotSame(original.getBody(), cloned.getBody());

        for(HeaderField headerField : original.getHeader().getAllHeaderFields()) {
            assertTrue(cloned.getHeader().contains(headerField.getHeaderName()));
        }
        assertNotSame(original.getBodyAsStreamDecoded(), cloned.getBodyAsStreamDecoded());
        assertEquals(original.getBodyAsStringDecoded(), cloned.getBodyAsStringDecoded());
    }
}