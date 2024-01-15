package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.http.Request.Builder;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelParseException;

import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionalInterceptorSpELTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(eval("true", new Builder()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertTrue(eval("true", new ResponseBuilder()));
    }

    @Test
    void invalidSpEL() {
        assertThrows(SpelParseException.class, () -> eval("foobar][", new Builder()));
        assertThrows(SpelParseException.class, () -> eval("foobar][", new ResponseBuilder()));
    }

    @Test
    void hasHeader() throws Exception {
        assertTrue(eval("headers['X-Foo-Bar'] == 'Baz'", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void isResponse() throws Exception {
        assertTrue(eval("response != null", new ResponseBuilder()));
    }

    @Test
    void testMethodResolver() throws Exception {
        assertTrue(eval("test() == 'Hello World!'", new Builder()));
    }

    @Test
    void testMethodArgs() throws Exception {
        assertTrue(eval("hello(\"World\") == 'Hello World'", new Builder()));
    }

    @Test
    void testMultipleMethodArgs() throws Exception {
        assertTrue(eval("add(2, 5) == 7", new Builder()));
    }


    private static boolean eval(String condition, Object builder) throws Exception {
        return performEval(condition, builder, SPEL);
    }
}

