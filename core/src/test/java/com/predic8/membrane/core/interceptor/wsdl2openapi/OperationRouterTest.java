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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.OperationRouter.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationRouterTest {

    private static RouteEntry route(String segment, String method, String operationName) {
        return new RouteEntry(buildPathPattern(segment), extractParamNames(segment), method, operationName);
    }

    private static OperationRouter routerWith(String basePath, String segment, String method, String operationName) {
        return new OperationRouter(basePath, List.of(route(segment, method, operationName)));
    }

    @Test
    void matchStripsQueryString() {
        var result = routerWith("/purchasing", "get-city", "POST", "getCity")
                .match("/purchasing/get-city?format=json", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchWithoutQueryString() {
        var result = routerWith("/purchasing", "get-city", "POST", "getCity")
                .match("/purchasing/get-city", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchPreservesOperationName() {
        var result = routerWith("/api", "get-city", "GET", "GetCity").match("/api/get-city", "GET");
        assertTrue(result.isPresent());
        assertEquals("GetCity", result.get().operationName());
    }

    @Test
    void matchHandlesRegexMetacharsInBasePath() {
        var result = routerWith("/api/v1.0", "get-city", "POST", "getCity").match("/api/v1.0/get-city", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchDistinguishesByMethod() {
        var router = new OperationRouter("/", List.of(
                route("articles", "GET", "getAll"),
                route("articles", "POST", "create")
        ));
        assertEquals("getAll", router.match("/articles", "GET").map(RouteMatch::operationName).orElse(null));
        assertEquals("create", router.match("/articles", "POST").map(RouteMatch::operationName).orElse(null));
        assertTrue(router.match("/articles", "DELETE").isEmpty());
    }

    @Test
    void matchExtractsPathParam() {
        var result = routerWith("/api", "partners/{id}", "GET", "getPartner").match("/api/partners/42", "GET");
        assertTrue(result.isPresent());
        assertEquals("getPartner", result.get().operationName());
        assertEquals(Map.of("id", "42"), result.get().pathParams());
    }

    @Test
    void matchExtractsMultiplePathParams() {
        var result = routerWith("/", "foo/{a}/bar/{b}", "GET", "fooBar").match("/foo/hello/bar/world", "GET");
        assertTrue(result.isPresent());
        assertEquals("fooBar", result.get().operationName());
        assertEquals("hello", result.get().pathParams().get("a"));
        assertEquals("world", result.get().pathParams().get("b"));
    }

    @Test
    void allowedMethodsListsEveryMethodRegisteredForThePath() {
        var router = new OperationRouter("/", List.of(
                route("partners/{id}", "GET", "getPartner"),
                route("partners/{id}", "PUT", "updatePartner"),
                route("partners", "POST", "createPartner")
        ));

        // A path mapped for other methods must report them all, so the 405 carries a correct
        // Allow header instead of the single method of whichever route happened to be found.
        assertEquals(List.of("GET", "PUT"), router.allowedMethods("/partners/42"));
        assertEquals(List.of("POST"), router.allowedMethods("/partners"));
        // An unmapped path has no allowed methods at all — the request must fall through, not 405.
        assertEquals(List.of(), router.allowedMethods("/unmapped"));
    }

    @Test
    void extractParamNamesInOrderOfAppearance() {
        assertEquals(List.of("a", "b"), extractParamNames("foo/{a}/bar/{b}"));
        assertEquals(List.of(), extractParamNames("articles"));
    }

    @Test
    void pathPatternQuotesLiteralsAndCapturesPlaceholders() {
        var pattern = buildPathPattern("v1.0/partners/{id}");

        assertTrue(pattern.matcher("v1.0/partners/42").matches());
        // The dot is a literal, not "any character".
        assertFalse(pattern.matcher("v1x0/partners/42").matches());
        // A placeholder never spans a path separator.
        assertFalse(pattern.matcher("v1.0/partners/42/extra").matches());
    }

    @Test
    void routesAreCopiedDefensively() {
        var mutable = new java.util.ArrayList<>(List.of(route("articles", "GET", "getAll")));
        var router = new OperationRouter("/", mutable);

        mutable.clear();

        assertEquals(1, router.getRoutes().size(), "the router must not be emptied by its caller");
    }
}
