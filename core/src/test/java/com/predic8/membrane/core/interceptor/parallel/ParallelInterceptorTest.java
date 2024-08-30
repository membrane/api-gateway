package com.predic8.membrane.core.interceptor.parallel;

import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.interceptor.parallel.ParallelInterceptor.cloneRequest;
import static org.junit.jupiter.api.Assertions.*;

class ParallelInterceptorTest {

    @Test
    void getUriFromTarget() {
        Target target1 = new Target("example.com", 80);
        Target target2 = new Target("secure.example.com", 443);
        target2.setUrl("/secure-path");
        Target target3 = new Target("secure.example.com", 443);
        target3.setSslParser(new SSLParser());
        assertEquals("http://example.com:80", ParallelInterceptor.getUrlFromTarget(target1));
        assertEquals("http://secure.example.com:443/secure-path", ParallelInterceptor.getUrlFromTarget(target2));
        assertEquals("https://secure.example.com:443", ParallelInterceptor.getUrlFromTarget(target3));
    }

    @Test
    void testCloneRequestNoSharedReferences() throws IOException {
        Request original = new Request();
        original.setMethod("GET");
        original.setUri("/test");
        original.getHeader().add(new HeaderField("Content-Type", "application/json"));
        original.setBodyContent("{\"Test\": \"Body\"".getBytes());

        Request cloned = cloneRequest(original);

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