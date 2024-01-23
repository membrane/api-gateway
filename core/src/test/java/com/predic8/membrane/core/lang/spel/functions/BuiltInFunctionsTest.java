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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuiltInFunctionsTest {

    static ExchangeEvaluationContext ctx;

    @BeforeAll
    static void init() throws URISyntaxException {
        var exc = Request.get("foo").buildExchange();
        exc.setProperty("scopes", List.of("demo", "test"));
        ctx = new ExchangeEvaluationContext(exc);
    }

    @Test
    public void testHasScope() {
        assertTrue(BuiltInFunctions.hasScope("test", ctx));
    }

    @Test
    public void testHasScopes() {
        assertTrue(BuiltInFunctions.hasScopes(ctx));
    }

    @Test
    public void testContainsScopes() {
        assertTrue(BuiltInFunctions.hasScopes(List.of("demo", "test"), ctx));
    }

    @Test
    public void testNotContainsScopes() {
        assertFalse(BuiltInFunctions.hasScopes(List.of("foo"), ctx));
    }

    @Test
    public void testNullScopes() throws URISyntaxException {
        var exc2 = Request.get("foo").buildExchange();
        ExchangeEvaluationContext ctxWithoutScopes = new ExchangeEvaluationContext(exc2);
        assertFalse(BuiltInFunctions.hasScopes(ctxWithoutScopes));
        assertFalse(BuiltInFunctions.hasScopes(List.of("foo"), ctxWithoutScopes));
    }
}