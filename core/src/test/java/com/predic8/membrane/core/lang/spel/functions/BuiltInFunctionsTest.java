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

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;
import com.predic8.membrane.core.security.ApiKeySecurityScheme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.HEADER;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

public class BuiltInFunctionsTest {

    static ExchangeEvaluationContext ctx;

    @BeforeAll
    static void init() throws URISyntaxException {
        var exc = Request.get("foo").buildExchange();
        exc.setProperty(SECURITY_SCHEMES, List.of(new ApiKeySecurityScheme(HEADER, "X-Api-Key").scopes("demo", "test")));
        ctx = new ExchangeEvaluationContext(exc);
    }

    @Test
    void testRate() throws Exception {
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
        assertFalse(BuiltInFunctions.hasScope(of("foo"), ctx));
    }

    @Test
    public void testNullScopes() throws URISyntaxException {
        var exc2 = Request.get("foo").buildExchange();
        ExchangeEvaluationContext ctxWithoutScopes = new ExchangeEvaluationContext(exc2);
        assertFalse(BuiltInFunctions.hasScope(ctxWithoutScopes));
        assertFalse(BuiltInFunctions.hasScope(of("foo"), ctxWithoutScopes));
    }

    @Test
    public void testHasBearerAuth() throws URISyntaxException {
        var exc2 = Request.get("foo").header(AUTHORIZATION, "Bearer 8w458934pj5u9843").buildExchange();
        ExchangeEvaluationContext ctxWithoutScopes = new ExchangeEvaluationContext(exc2);
        assertTrue(BuiltInFunctions.isBearerAuthorization(ctxWithoutScopes));
    }

    @Test
    public void testHasOtherAuth() throws URISyntaxException {
        var exc2 = Request.get("foo").header(AUTHORIZATION, "Other 8w458934pj5u9843").buildExchange();
        ExchangeEvaluationContext ctxWithoutScopes = new ExchangeEvaluationContext(exc2);
        assertFalse(BuiltInFunctions.isBearerAuthorization(ctxWithoutScopes));
    }

    @Test
    public void testHasNoAuth() throws URISyntaxException {
        var exc2 = Request.get("foo").buildExchange();
        ExchangeEvaluationContext ctxWithoutScopes = new ExchangeEvaluationContext(exc2);
        assertFalse(BuiltInFunctions.isBearerAuthorization(ctxWithoutScopes));
    }

    public double calculateRate(double weightInPercent) throws Exception {
        int executedCount = 0;
        for (int i = 0; i < 1000000; i++) {
            if (BuiltInFunctions.weight(weightInPercent, null)) {
                executedCount++;
            }
        }
        return ((double) executedCount / 1000000);
    }
}