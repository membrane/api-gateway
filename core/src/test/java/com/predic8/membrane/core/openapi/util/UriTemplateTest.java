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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void plusIsNoSpace() {
        assertEquals(Map.of("name", "a+b"), UriTemplate.of("/users/{name}").match("/users/a+b").orElseThrow());
        assertEquals(Map.of("name", "a+b"), UriTemplate.of("/users/{name}").match("/users/a%2Bb").orElseThrow());
    }

    @Test
    void literalPartsAreMatchedAsTheyAre() {
        assertTrue(UriTemplate.of("/files/{name}.json").matches("/files/a.json"));
        assertFalse(UriTemplate.of("/files/{name}.json").matches("/files/aXjson"));
        assertTrue(UriTemplate.of("/a+b/{id}").matches("/a+b/1"));
        assertFalse(UriTemplate.of("/a+b/{id}").matches("/aab/1"));
        assertTrue(UriTemplate.of("/price$/{id}").matches("/price$/1"));
        assertTrue(UriTemplate.of("/x(y/{id}").matches("/x(y/1"));
        assertTrue(UriTemplate.of("/x\\E.*/{id}").matches("/x\\E.*/1"));
    }

    /**
     * OpenAPI path-literal = 1*pchar, pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
     */
    @ParameterizedTest
    @ValueSource(strings = {"-", ".", "_", "~", "%20", "!", "$", "&", "'", "(", ")", "*", "+", ",", ";", "=", ":", "@"})
    void everyPcharInLiteralPartIsMatchedAsItIs(String pchar) {
        final var template = UriTemplate.of("/a%sb/{id}".formatted(pchar));
        assertEquals(Map.of("id", "7"), template.match("/a%sb/7".formatted(pchar)).orElseThrow());
        assertFalse(template.matches("/aQb/7"));
        assertFalse(template.matches("/ab/7"));
        assertFalse(template.matches("/a%s%sb/7".formatted(pchar, pchar)));
    }

    /**
     * OpenAPI template-expression-param-name = 1*( %x00-7A / %x7C / %x7E-10FFFF ), every character but { and }
     */
    @ParameterizedTest
    @ValueSource(strings = {"id", "i_d-1", "a.b", "id%3", "id<>4", "a b", "a*b", "$x", "(x)", "a|b", "a\\b", "ü", "a:b", "a@b", "a=b,c;d", "id/x", "id?x", "id#x", "a/b?c#d"})
    void parameterNames(String name) {
        assertEquals(Map.of(name, "7"), UriTemplate.of("/x/{%s}".formatted(name)).match("/x/7").orElseThrow());
    }

    @Test
    void slashAndQuestionMarkInParameterNameDoNotSplitThePath() {
        final var template = UriTemplate.of("/items/{id?x}/{a/b}");
        assertEquals(Map.of("id?x", "7", "a/b", "8"), template.match("/items/7/8?id=9").orElseThrow());
        assertFalse(template.matches("/items/7"));
    }

    @Test
    void severalParametersInOneSegment() {
        final var template = UriTemplate.of("/{name}.{ext}");
        assertEquals(Map.of("name", "a.b", "ext", "c"), template.match("/a.b.c").orElseThrow());
        assertFalse(template.matches("/abc"));
    }

    @Test
    void curlyBraceOutsideOfTemplateExpression() {
        assertThrows(IllegalArgumentException.class, () -> UriTemplate.of("/a/{}"));
        assertThrows(IllegalArgumentException.class, () -> UriTemplate.of("/a/{b}}"));
        assertThrows(IllegalArgumentException.class, () -> UriTemplate.of("/a/{b{c}"));
    }

    @Test
    void parameterOccursMoreThanOnce() {
        final var e = assertThrows(IllegalArgumentException.class, () -> UriTemplate.of("/{id}/{id}"));
        assertTrue(e.getMessage().contains("{id}"));
        assertThrows(IllegalArgumentException.class, () -> UriTemplate.of("/{id}/{id}", true));
    }

    @Test
    void literalPartsAreRegularExpressions() {
        assertEquals(Map.of("name", "orders"), UriTemplate.of(".*/api/{name}", true).match("/admin/api/orders").orElseThrow());
        assertFalse(UriTemplate.of(".*/api/{name}").matches("/admin/api/orders"));
    }

    @Test
    void capturingGroupsInLiteralPartsDoNotShiftParameters() {
        assertEquals(Map.of("id", "7"), UriTemplate.of("/(v1|v2)/users/{id}", true).match("/v1/users/7").orElseThrow());
    }

    @Test
    void isReusable() {
        final var template = UriTemplate.of("/foo/{id}");
        assertEquals(Map.of("id", "1"), template.match("/foo/1").orElseThrow());
        assertTrue(template.match("/foo").isEmpty());
        assertEquals(Map.of("id", "2"), template.match("/foo/2").orElseThrow());
    }
}
