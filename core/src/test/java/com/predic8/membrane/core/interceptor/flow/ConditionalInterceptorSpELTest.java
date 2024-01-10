package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Request.Builder;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelParseException;

import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.GROOVY;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionalInterceptorSpELTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(evalSpEL("true", new Builder()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertTrue(evalSpEL("true", new ResponseBuilder()));
    }

    @Test
    void invalidSpEL() {
        assertThrows(SpelParseException.class, () -> evalSpEL("foobar][", new Builder()));
        assertThrows(SpelParseException.class, () -> evalSpEL("foobar][", new ResponseBuilder()));
    }

    @Test
    void hasHeader() throws Exception {
        assertTrue(evalSpEL("headers['X-Foo-Bar'] == 'Baz'", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void isResponse() throws Exception {
        assertTrue(evalSpEL("response != null", new ResponseBuilder()));
    }
}

