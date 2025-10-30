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
package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.http.Request.Builder;
import com.predic8.membrane.core.lang.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;
import static com.predic8.membrane.test.TestUtil.assembleExchange;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.of;

class APIProxyKeyComplexMatchTest {

    APIProxyKey k1;

    @BeforeEach
    void setup() {
        k1 = new APIProxyKey("","", 80, null, "*",null, true) {{
            addBasePaths(new ArrayList<>(List.of("/bar")));
        }};
    }

    @Test
    void complexMatchExpressionTrueTest() throws URISyntaxException {
        var key = new APIProxyKey("", "", 80, null,"*", null,false);
        var exc = new Builder().get("").buildExchange();
        assertTrue(key.complexMatch(exc));
    }

    @Test
    void complexMatchExpressionFalse() throws URISyntaxException {
        var key = new APIProxyKey("", "", 80, null,"*",
                expression(null, SPEL,"1 == 2"), false);
        assertFalse(key.complexMatch(new Builder().get("").buildExchange()));
    }

    @Test
    void complexMatchExpressionHeader() throws URISyntaxException {
        var key = new APIProxyKey("", "", 80, null,"*", null,false);
        var exc = new Builder().get("").header("X-Custom-Header", "foo").buildExchange();
        assertTrue(key.complexMatch(exc));
    }

    @Test
    void complexMatchExpressionQueryParam() throws URISyntaxException {
        var key = new APIProxyKey("", "", 80, null,"*",
                expression(null, SPEL,"param.foo == 'bar'"),false);
        assertTrue(key.complexMatch(new Builder().get("/baz?foo=bar").buildExchange()));

        key = new APIProxyKey("", "", 80, null,"*",
                expression(null, SPEL,"param.foo == 'wrong'"),false);
        assertFalse(key.complexMatch(new Builder().get("/baz?foo=bar").buildExchange()));
    }

    @DisplayName("Access old path /api-doc")
    @ParameterizedTest
    @MethodSource("urls")
    void checkAcceptsPath(String url, boolean expected) throws UnknownHostException {
        assertEquals(expected, k1.complexMatch(assembleExchange("predic8.de","GET",url, "", 80, "192.168.0.1")));
    }

    private static Stream<Arguments> urls() {
        return Stream.of(
                of("/api-docs",true),
                of("/api-docs/ui",true),
                of("/api-doc",true),
                of("/api-doc/ui",true),
                of("/apidoc",false)
                );
    }
}
