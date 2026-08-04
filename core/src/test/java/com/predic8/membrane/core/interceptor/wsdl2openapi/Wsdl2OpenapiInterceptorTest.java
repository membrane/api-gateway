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
import java.util.Map;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Wsdl2OpenapiInterceptorTest {

    @SuppressWarnings("unchecked")
    private static void setFields(Wsdl2OpenapiInterceptor interceptor, String basePath,
                                  Map<String, String> pathMethodToOperation) throws Exception {
        Field bf = Wsdl2OpenapiInterceptor.class.getDeclaredField("basePath");
        bf.setAccessible(true);
        bf.set(interceptor, basePath);

        Field kf = Wsdl2OpenapiInterceptor.class.getDeclaredField("pathMethodToOperation");
        kf.setAccessible(true);
        ((Map<String, String>) kf.get(interceptor)).putAll(pathMethodToOperation);
    }

    @Test
    void extractOperationNameStripsQueryString() throws Exception {
        var interceptor = new Wsdl2OpenapiInterceptor();
        setFields(interceptor, "/purchasing", Map.of("get-city:POST", "getCity"));

        assertEquals("getCity", interceptor.extractOperationName("/purchasing/get-city?format=json", "POST"));
    }

    @Test
    void extractOperationNameWithoutQueryString() throws Exception {
        var interceptor = new Wsdl2OpenapiInterceptor();
        setFields(interceptor, "/purchasing", Map.of("get-city:POST", "getCity"));

        assertEquals("getCity", interceptor.extractOperationName("/purchasing/get-city", "POST"));
    }

    @Test
    void extractOperationNamePreservesPascalCase() throws Exception {
        var interceptor = new Wsdl2OpenapiInterceptor();
        setFields(interceptor, "/api", Map.of("get-city:GET", "GetCity"));

        assertEquals("GetCity", interceptor.extractOperationName("/api/get-city", "GET"));
    }

    @Test
    void extractOperationNameHandlesRegexMetacharactersInBasePath() throws Exception {
        var interceptor = new Wsdl2OpenapiInterceptor();
        setFields(interceptor, "/api/v1.0", Map.of("get-city:POST", "getCity"));

        assertEquals("getCity", interceptor.extractOperationName("/api/v1.0/get-city", "POST"));
    }

    @Test
    void extractOperationNameDistinguishesByMethod() throws Exception {
        var interceptor = new Wsdl2OpenapiInterceptor();
        setFields(interceptor, "/", Map.of("articles:GET", "getAll", "articles:POST", "create"));

        assertEquals("getAll", interceptor.extractOperationName("/articles", "GET"));
        assertEquals("create", interceptor.extractOperationName("/articles", "POST"));
        assertNull(interceptor.extractOperationName("/articles", "DELETE"));
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
