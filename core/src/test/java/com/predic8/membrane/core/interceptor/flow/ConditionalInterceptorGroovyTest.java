package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.http.Request.*;
import com.predic8.membrane.core.http.Response.*;
import org.codehaus.groovy.control.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.GROOVY;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalInterceptorGroovyTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(evalGroovy("true", new Builder()));
    }

    @Test
    void simpleRequestFalse() throws Exception {
        assertFalse(evalGroovy("false", new Builder()));
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
        assertTrue(evalGroovy("flow.isResponse()", new ResponseBuilder()));
    }

    private static boolean evalGroovy(String condition, Object builder) throws Exception {
        return performEval(condition, builder, GROOVY);
    }
}
