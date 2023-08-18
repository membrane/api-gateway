package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.apache.http.client.methods.*;
import org.junit.jupiter.api.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;

import java.net.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExchangeEvaluationContextTest {

    Exchange exc;

    @BeforeEach
    void setup() {
        exc = new Request.Builder()
                .header("Authentication","foo")
                .buildExchange();
    }

    String keyExpression(String spel) {
        Expression expression = new SpelExpressionParser().parseExpression(spel);
        return expression.getValue(new ExchangeEvaluationContext(exc, exc.getRequest()).getStandardEvaluationContext(), String.class);
    }

    @Test
    void getMethod() {
        assertEquals("foo", keyExpression("headers[authentication]"));
    }

    @Test
    void getMethodIgnoreCase() {
        assertEquals("foo", keyExpression("headers[AUTHenticatioN]"));
    }
}