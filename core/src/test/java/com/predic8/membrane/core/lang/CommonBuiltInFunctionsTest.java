/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.security.ApiKeySecurityScheme;
import com.predic8.membrane.core.security.BasicHttpSecurityScheme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.lang.CommonBuiltInFunctions.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.HEADER;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

class CommonBuiltInFunctionsTest {

    static Exchange exc;

    @BeforeAll
    static void init() throws URISyntaxException {
        exc = get("foo").buildExchange();
        exc.setProperty(SECURITY_SCHEMES, List.of(
                new ApiKeySecurityScheme(HEADER, "X-Api-Key").scopes("demo", "test"),
                new BasicHttpSecurityScheme().scopes("foo", "bar")
        ));
        exc.getRequest().setBodyContent("""
                {"name":"John"}""".getBytes());
    }

    @Test
    void jsonPathReturnsValueOrNull() {
        assertEquals("John", jsonPath("$.name", exc.getRequest()));
        assertNull(jsonPath("$.foo", exc.getRequest()));
    }

    @Test
    void hasScopeSingle() {
        assertTrue(hasScope("test", exc));
    }

    @Test
    void hasScopeAnyFromList() {
        assertTrue(hasScope(of("demo", "test"), exc));
        assertFalse(hasScope(of("quux"), exc));
    }

    @Test
    void hasScopeNoSchemes() throws URISyntaxException {
        var exc2 = get("foo").buildExchange();
        assertFalse(hasScope(exc2));
        assertFalse(hasScope(of("foo"), exc2));
    }

    @Test
    void scopesAll() {
        assertEquals(List.of("test", "demo", "bar", "foo"), scopes(exc));
    }

    @Test
    void scopesBySchemeType() {
        assertEquals(List.of("bar", "foo"), scopes("http", exc));
    }

    @Test
    void isBearerAuthorizationVariants() throws URISyntaxException {
        assertTrue(isBearerAuthorization(get("foo")
                .header(AUTHORIZATION, "Bearer 8w458934pj5u9843")
                .buildExchange()));

        assertFalse(isBearerAuthorization(get("foo")
                .header(AUTHORIZATION, "Other 8w458934pj5u9843")
                .buildExchange()));

        assertFalse(isBearerAuthorization(get("foo").buildExchange()));
    }

    @Test
    void encode() {
        assertEquals("YWxpc2U6Zmxvd2VyMjU=", base64Encode("alise:flower25"));
    }

    @Test
    void weightProducesRoughRate() {
        assertEquals(0.01, calculateRate(1), 0.05);
        assertEquals(0.5, calculateRate(50), 0.05);
        assertEquals(0.001, calculateRate(0.1), 0.005);
    }

    private double calculateRate(double weightInPercent) {
        int executedCount = 0;
        for (int i = 0; i < 1_000_000; i++) {
            if (weight(weightInPercent)) {
                executedCount++;
            }
        }
        return ((double) executedCount / 1_000_000);
    }
}
