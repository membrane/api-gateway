package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.apache.http.client.methods.*;
import org.junit.jupiter.api.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;

import java.net.*;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeEvaluationContextTest {

    Exchange exc;

    @BeforeEach
    void setup() throws URISyntaxException {
        exc = new Request.Builder().header("Authentication","foo").buildExchange();


    }

    @Test
    void getMethod() {

        Expression expression = new SpelExpressionParser().parseExpression("headers.Authentication");

        expression.getValue(new ExchangeEvaluationContext(exc, exc.getRequest()).getStandardEvaluationContext(), String.class);
    }
}