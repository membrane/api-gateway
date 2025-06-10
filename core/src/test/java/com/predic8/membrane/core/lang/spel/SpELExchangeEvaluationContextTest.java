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

import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.springframework.expression.spel.standard.*;

import java.net.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.*;

public class SpELExchangeEvaluationContextTest {

    Exchange exc;

    @BeforeEach
    void setup() throws URISyntaxException {
        exc = new Request.Builder()
                .method("POST")
                .url(new URIFactory(), "/products/new%20and%20fresh?limit=10&offset=0")
                .contentType(APPLICATION_JSON.toString())
                .header("Authentication","foo")
                .header("shadow-ing", "nothappening")
                .header("shadowIng", "test")
                .header("Cookie","foo=bar")
                .body("""
                        {
                          "product": "Snake oil"
                        }
                        """)
                .buildExchange();

        exc.setProperty("foo","1234");
        exc.setResponse(Response.accepted().build());
        exc.setProxy(getApiProxy());
    }

    private static @NotNull APIProxy getApiProxy() {
        APIProxy ap = new APIProxy();
        Path p = new Path();
        p.setValue("/products/{category}");
        ap.setPath(p);
        return ap;
    }

    String keyExpression(String spel) {
        return new SpelExpressionParser().parseExpression(spel).getValue(new SpELExchangeEvaluationContext(exc, REQUEST), String.class);
    }

    @Test
    void pathParameters() {
        assertEquals("new and fresh", keyExpression("pathParam.category"));
    }

    @Test
    void flow() {
        assertEquals(REQUEST.name(), keyExpression("flow"));
    }

    @Test
    void paramsSubscription() {
        assertEquals("10", keyExpression("param['limit']"));
    }

    @Test
    void paramsDot() {
        assertEquals("10", keyExpression("param.limit"));
    }

    @Test
    void paramsDotOffset() {
        assertEquals("0", keyExpression("param.offset"));
    }

    @Test
    void propertySubscription() {
        assertEquals("1234", keyExpression("properties['foo']"));
    }

    @Test
    void propertiesDot() {
        assertEquals("1234", keyExpression("properties.foo"));
    }

    @Test
    void propertyWithY() {
        assertEquals("1234", keyExpression("property.foo"));
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
    void headers() {
        assertEquals(APPLICATION_JSON.toString(), keyExpression("headers.contentType"));
    }

    @Test
    void headerWithOutS() {
        assertEquals(APPLICATION_JSON.toString(), keyExpression("header.contentType"));
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

    @Test
    void json() {
        assertEquals("Snake oil", keyExpression("json['product']"));
    }

    @Test
    void jsonUnknown() {
        assertEquals("", keyExpression("json['unknown']"));
    }

    @Test
    void body() {
        assertTrue(keyExpression("body").contains("Snake oil"));
    }

    @Test
    void cookie() {
        assertTrue(keyExpression("cookie.foo").contains("bar"));
    }

    @Test
    void cookies() {
        assertTrue(keyExpression("cookies.foo").contains("bar"));
    }
}
