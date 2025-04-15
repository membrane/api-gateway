/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.lang.spel.*;
import com.predic8.membrane.core.security.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.lang.spel.functions.BuiltInFunctions.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static java.util.List.*;
import static org.junit.jupiter.api.Assertions.*;

public class BuiltInFunctionsTest {

    static SpELExchangeEvaluationContext ctx;

    @BeforeAll
    static void init() throws URISyntaxException {
        var exc = get("foo").buildExchange();
        exc.setProperty(SECURITY_SCHEMES, List.of(
                new ApiKeySecurityScheme(HEADER, "X-Api-Key").scopes("demo", "test"),
                new BasicHttpSecurityScheme().scopes("foo", "bar")
        ));
        exc.getRequest().setBodyContent("""
                {"name":"John"}""".getBytes());
        ctx = new SpELExchangeEvaluationContext(exc, REQUEST);
    }

    @Test
    void testRate() {
        assertEquals(0.01, calculateRate(1), 0.05);
        assertEquals(0.5, calculateRate(50), 0.05);
        assertEquals(0.001, calculateRate(0.1), 0.005);
    }

    @Test
    public void testHasScope() {
        assertTrue(BuiltInFunctions.hasScope("test", ctx));
    }

    @Test
    public void testHasScopes() {
        assertTrue(BuiltInFunctions.hasScope(ctx));
    }

    @Test
    public void testContainsScopes() {
        assertTrue(BuiltInFunctions.hasScope(of("demo", "test"), ctx));
    }

    @Test
    public void testNotContainsScopes() {
        assertFalse(BuiltInFunctions.hasScope(of("quux"), ctx));
    }

    @Test
    public void testNullScopes() throws URISyntaxException {
        var exc2 = get("foo").buildExchange();
        SpELExchangeEvaluationContext ctxWithoutScopes = new SpELExchangeEvaluationContext(exc2, REQUEST);
        assertFalse(BuiltInFunctions.hasScope(ctxWithoutScopes));
        assertFalse(BuiltInFunctions.hasScope(of("foo"), ctxWithoutScopes));
    }

    @Test
    public void testGetAllScopes() {
        assertEquals(List.of("test", "demo", "bar", "foo"), BuiltInFunctions.scopes(ctx));
    }

    @Test
    public void testGetSchemeSpecificScopes() {
        assertEquals(List.of("bar", "foo"), BuiltInFunctions.scopes("http", ctx));
    }

    @Test
    public void testHasBearerAuth() throws URISyntaxException {
        var exc2 = get("foo").header(AUTHORIZATION, "Bearer 8w458934pj5u9843").buildExchange();
        SpELExchangeEvaluationContext ctxWithoutScopes = new SpELExchangeEvaluationContext(exc2,REQUEST);
        assertTrue(BuiltInFunctions.isBearerAuthorization(ctxWithoutScopes));
    }

    @Test
    public void testHasOtherAuth() throws URISyntaxException {
        var exc2 = get("foo").header(AUTHORIZATION, "Other 8w458934pj5u9843").buildExchange();
        SpELExchangeEvaluationContext ctxWithoutScopes = new SpELExchangeEvaluationContext(exc2, REQUEST);
        assertFalse(BuiltInFunctions.isBearerAuthorization(ctxWithoutScopes));
    }

    @Test
    public void testHasNoAuth() throws URISyntaxException {
        var exc2 = get("foo").buildExchange();
        SpELExchangeEvaluationContext ctxWithoutScopes = new SpELExchangeEvaluationContext(exc2, REQUEST);
        assertFalse(BuiltInFunctions.isBearerAuthorization(ctxWithoutScopes));
    }

    public double calculateRate(double weightInPercent) {
        int executedCount = 0;
        for (int i = 0; i < 1000000; i++) {
            if (BuiltInFunctions.weight(weightInPercent, null)) {
                executedCount++;
            }
        }
        return ((double) executedCount / 1000000);
    }

    @Test
    void testBase64Encode() {
        assertEquals("YWxpc2U6Zmxvd2VyMjU=", base64Encode("alise:flower25",ctx));
    }
}