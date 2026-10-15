/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UriTemplateTest {

    @Test
    void noMatchIsEmpty() {
        final var template = UriTemplate.of("/foo/{id}");
        assertTrue(template.match("/bar/7").isEmpty());
        assertFalse(template.matches("/bar/7"));
    }

    @Test
    void parameters() {
        assertEquals(Map.of("cid", "7", "oid", "8"), UriTemplate.of("/customers/{cid}/orders/{oid}").match("/customers/7/orders/8").orElseThrow());
    }

    @Test
    void parametersAreUrlDecoded() {
        assertEquals(Map.of("name", "a b/c"), UriTemplate.of("/users/{name}").match("/users/a%20b%2Fc").orElseThrow());
    }

    @Test
    void queryStringAndTrailingSlashAreIgnored() {
        final var template = UriTemplate.of("/foo/{id}");
        assertEquals(Map.of("id", "7"), template.match("/foo/7?a=b").orElseThrow());
        assertEquals(Map.of("id", "7"), template.match("/foo/7/").orElseThrow());
        assertTrue(UriTemplate.of("/foo/").matches("/foo"));
    }

    @Test
    void literalPartsAreRegularExpressions() {
        assertEquals(Map.of("name", "orders"), UriTemplate.of(".*/api/{name}").match("/admin/api/orders").orElseThrow());
    }

    @Test
    void capturingGroupsInLiteralPartsDoNotShiftParameters() {
        assertEquals(Map.of("id", "7"), UriTemplate.of("/(v1|v2)/users/{id}").match("/v1/users/7").orElseThrow());
    }

    @Test
    void isReusable() {
        final var template = UriTemplate.of("/foo/{id}");
        assertEquals(Map.of("id", "1"), template.match("/foo/1").orElseThrow());
        assertTrue(template.match("/foo").isEmpty());
        assertEquals(Map.of("id", "2"), template.match("/foo/2").orElseThrow());
    }
}
