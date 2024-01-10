package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request.Builder;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.junit.jupiter.api.*;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalInterceptorGroovyTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(evalGroovy("true", new Builder()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertTrue(evalGroovy("true", new ResponseBuilder()));
    }

    @Test
    void invalidGroovy() {
        assertThrows(MultipleCompilationErrorsException.class, () -> evalGroovy("foobar;()", new Builder()));
        assertThrows(MultipleCompilationErrorsException.class, () -> evalGroovy("foobar;()", new ResponseBuilder()));
    }

    @Test
    void hasHeader() throws Exception {
        assertTrue(evalGroovy("header.getFirstValue(\"X-Foo-Bar\").equals(\"Baz\")", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void isFlowResponse() throws Exception {
        assertTrue(evalGroovy("flow.toString().equals(\"RESPONSE\")", new ResponseBuilder()));
    }
}
