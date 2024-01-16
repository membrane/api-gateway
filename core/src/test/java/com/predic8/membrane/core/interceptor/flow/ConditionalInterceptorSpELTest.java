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
    void testBuiltInMethod() throws Exception {
        assertTrue(eval("hasScope('test')", new Builder()));
    }

    @Test
    void testBuiltInMethodOverload() throws Exception {
        assertTrue(eval("hasScopes()", new Builder()));
    }

    @Test
    void testBuiltInMethodTypeHierarchy() throws Exception {
        //Inline List initializer in SpEL produces a RandomAccessList which should be assignable to List.
        assertTrue(eval("hasScopes({'test'})", new Builder()));
    }

    private static boolean eval(String condition, Object builder) throws Exception {
        return performEval(condition, builder, SPEL);
    }
}

