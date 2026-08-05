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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenapiInterceptor.buildPathPattern;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenapiInterceptor.extractParamNames;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Wsdl2OpenapiInterceptorTest {

    private static Wsdl2OpenapiInterceptor interceptorWith(String basePath, String segment, String method, String opName) {
        return new Wsdl2OpenapiInterceptor(basePath,
                List.of(new Wsdl2OpenapiInterceptor.RouteEntry(
                        buildPathPattern(segment),
                        extractParamNames(segment),
                        method, opName)));
    }

    @Test
    void matchRouteStripsQueryString() {
        var interceptor = interceptorWith("/purchasing", "get-city", "POST", "getCity");
        var result = interceptor.matchRoute("/purchasing/get-city?format=json", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchRouteWithoutQueryString() {
        var interceptor = interceptorWith("/purchasing", "get-city", "POST", "getCity");
        var result = interceptor.matchRoute("/purchasing/get-city", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchRoutePreservesOperationName() {
        var interceptor = interceptorWith("/api", "get-city", "GET", "GetCity");
        var result = interceptor.matchRoute("/api/get-city", "GET");
        assertTrue(result.isPresent());
        assertEquals("GetCity", result.get().operationName());
    }

    @Test
    void matchRouteHandlesRegexMetacharsInBasePath() {
        var interceptor = interceptorWith("/api/v1.0", "get-city", "POST", "getCity");
        var result = interceptor.matchRoute("/api/v1.0/get-city", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchRouteDistinguishesByMethod() {
        var interceptor = new Wsdl2OpenapiInterceptor("/", List.of(
                new Wsdl2OpenapiInterceptor.RouteEntry(buildPathPattern("articles"), extractParamNames("articles"), "GET", "getAll"),
                new Wsdl2OpenapiInterceptor.RouteEntry(buildPathPattern("articles"), extractParamNames("articles"), "POST", "create")
        ));
        assertEquals("getAll", interceptor.matchRoute("/articles", "GET").map(Wsdl2OpenapiInterceptor.RouteMatch::operationName).orElse(null));
        assertEquals("create", interceptor.matchRoute("/articles", "POST").map(Wsdl2OpenapiInterceptor.RouteMatch::operationName).orElse(null));
        assertTrue(interceptor.matchRoute("/articles", "DELETE").isEmpty());
    }

    @Test
    void matchRouteExtractsPathParam() {
        var interceptor = interceptorWith("/api", "partners/{id}", "GET", "getPartner");
        var result = interceptor.matchRoute("/api/partners/42", "GET");
        assertTrue(result.isPresent());
        assertEquals("getPartner", result.get().operationName());
        assertEquals(Map.of("id", "42"), result.get().pathParams());
    }

    @Test
    void matchRouteExtractsMultiplePathParams() {
        var interceptor = interceptorWith("/", "foo/{a}/bar/{b}", "GET", "fooBar");
        var result = interceptor.matchRoute("/foo/hello/bar/world", "GET");
        assertTrue(result.isPresent());
        assertEquals("fooBar", result.get().operationName());
        assertEquals("hello", result.get().pathParams().get("a"));
        assertEquals("world", result.get().pathParams().get("b"));
    }

    @Test
    void chainedInstancesDoNotShareOperationProperty() throws Exception {
        var first = new Wsdl2OpenapiInterceptor();
        var second = new Wsdl2OpenapiInterceptor();

        Field kf = Wsdl2OpenapiInterceptor.class.getDeclaredField("operationPropertyKey");
        kf.setAccessible(true);
        String firstKey = (String) kf.get(first);
        String secondKey = (String) kf.get(second);
        assertNotEquals(firstKey, secondKey);

        var exc = new Exchange(null);
        exc.setProperty(firstKey, "someOperationOnlyFirstDefines");

        // second's handleResponse must not see first's matched operation and must not
        // attempt to transform the (SOAP) response body a second time.
        assertEquals(Outcome.CONTINUE, second.handleResponse(exc));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void camelToKebabConv(String input, String expected) {
        assertEquals(expected, camelToKebab(input));
    }

    static Stream<Arguments> camelToKebabConv() {
        return Stream.of(
                arguments("getCity",                   "get-city"),
                arguments("getBank",                   "get-bank"),
                arguments("changeOtherIiDs",           "change-other-ii-ds"),       // pure camelCase
                arguments("change_OtherIiDs",          "change-other-ii-ds"),       // underscore + camelCase
                arguments("Get_Budget_Structures",     "get-budget-structures"),    // PascalCase + underscores
                arguments("Get_Allocation_Group_Sets", "get-allocation-group-sets"), // user-reported
                arguments("getURLs",                   "get-urls"),                 // acronym + plural suffix
                arguments("getUserID",                 "get-user-id"),              // acronym at end
                arguments("ID",                        "id"),                       // pure acronym
                arguments("change_other_ii",           "change-other-ii"),          // snake_case
                arguments("simple",                    "simple"),
                arguments("A",                         "a"),
                arguments("_leading",                  "leading"),
                arguments("double__under",             "double-under")
        );
    }
}
