package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request.Builder;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.flow.util.MockInterceptor;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.junit.jupiter.api.*;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalInterceptorGroovyTest {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(performEval("true", new Builder()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertTrue(performEval("true", new ResponseBuilder()));
    }

    @Test
    void invalidGroovy() {
        assertThrows(MultipleCompilationErrorsException.class, () -> performEval("foobar;()", new Builder()));
        assertThrows(MultipleCompilationErrorsException.class, () -> performEval("foobar;()", new ResponseBuilder()));
    }

    @Test
    void hasHeader() throws Exception {
        assertTrue(performEval("message.getHeader().getFirstValue(\"X-Foo-Bar\").equals(\"Baz\")", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void isFlowResponse() throws Exception {
        assertTrue(performEval("flow.equals(com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE)", new ResponseBuilder()));
    }

    private static boolean performEval(String condition, Object builder) throws Exception {
        var exc = new Exchange(null);
        var mockInt = new MockInterceptor();
        var condInt = new ConditionalInterceptor();

        condInt.setInterceptors(of(mockInt));
        condInt.setTest(condition);
        condInt.init(new HttpRouter());

        switch (builder) {
            case Builder b -> {
                exc.setRequest(b.build());
                condInt.handleRequest(exc);
            }
            case ResponseBuilder b ->  {
                exc.setResponse(b.build());
                condInt.handleResponse(exc);
            }
            default -> throw new IllegalStateException("Unexpected value: " + builder);
        }

        return mockInt.isCalled();
    }
}
