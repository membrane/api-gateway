/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;

import java.net.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.*;

public class ExchangeEvaluationContextTest {

    Exchange exc;

    @BeforeEach
    void setup() throws URISyntaxException {
        exc = new Request.Builder()
                .method("POST")
                .url(new URIFactory(), "/products?limit=10&offset=0")
                .contentType(APPLICATION_JSON.toString())
                .header("Authentication","foo")
                .header("shadow-ing", "nothappening")
                .header("shadowIng", "test")
                .buildExchange();

        exc.setProperty("foo","1234");

        exc.setResponse(Response.accepted().build());
    }

    String keyExpression(String spel) {
        Expression expression = new SpelExpressionParser().parseExpression(spel);
        return expression.getValue(new ExchangeEvaluationContext(exc, exc.getRequest()), String.class);
    }

    @Test
    void paramsSubscription() {
        assertEquals("10", keyExpression("params['limit']"));
    }

    @Test
    void paramsDot() {
        assertEquals("10", keyExpression("params.limit"));
    }

    @Test
    void paramsDotOffset() {
        assertEquals("0", keyExpression("params.offset"));
    }

    @Test
    void propertySubscription() {
        assertEquals("1234", keyExpression("properties['foo']"));
    }

    @Test
    void propertyDot() {
        assertEquals("1234", keyExpression("properties.foo"));
    }

    @Test
    void hyphen() {
        assertEquals(APPLICATION_JSON.toString(), keyExpression("request.headers['content-type']"));
    }

    @Test
    void conversion() {
        assertEquals(APPLICATION_JSON.toString(), keyExpression("request.headers.contentType"));
    }

    @Test
    void headerCasing() {
        assertEquals(APPLICATION_JSON.toString(), keyExpression("request.headers['CoNtent-TyPE']"));
    }

    @Test
    void dontShadowConversion() {
        assertEquals("test", keyExpression("request.headers.shadowIng"));
    }

    @Test
    void getMethod() {
        assertEquals("foo", keyExpression("headers.authentication"));
    }

    @Test
    void getStatusCode() {
        assertEquals("202", keyExpression("statusCode"));
    }

    @Test
    void getMethodIgnoreCase() {
        assertEquals("foo", keyExpression("headers.AUTHenticatioN"));
    }
}
