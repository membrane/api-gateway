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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import static org.junit.jupiter.api.Assertions.*;

class Wsdl2OpenApiInterceptorTest {

    @SuppressWarnings("unchecked")
    private static void setFields(Wsdl2OpenApiInterceptor interceptor, String basePath,
                                   Map<String, String> kebabToOperation) throws Exception {
        Field bf = Wsdl2OpenApiInterceptor.class.getDeclaredField("basePath");
        bf.setAccessible(true);
        bf.set(interceptor, basePath);

        Field kf = Wsdl2OpenApiInterceptor.class.getDeclaredField("kebabToOperation");
        kf.setAccessible(true);
        ((Map<String, String>) kf.get(interceptor)).putAll(kebabToOperation);
    }

    @Test
    void extractOperationNameStripsQueryString() throws Exception {
        var interceptor = new Wsdl2OpenApiInterceptor();
        setFields(interceptor, "/purchasing", Map.of("get-city", "getCity"));

        assertEquals("getCity", interceptor.extractOperationName("/purchasing/get-city?format=json"));
    }

    @Test
    void extractOperationNameWithoutQueryString() throws Exception {
        var interceptor = new Wsdl2OpenApiInterceptor();
        setFields(interceptor, "/purchasing", Map.of("get-city", "getCity"));

        assertEquals("getCity", interceptor.extractOperationName("/purchasing/get-city"));
    }

    @Test
    void extractOperationNamePreservesPascalCase() throws Exception {
        var interceptor = new Wsdl2OpenApiInterceptor();
        setFields(interceptor, "/api", Map.of("get-city", "GetCity"));

        assertEquals("GetCity", interceptor.extractOperationName("/api/get-city"));
    }

    @Test
    void extractOperationNameHandlesRegexMetacharactersInBasePath() throws Exception {
        var interceptor = new Wsdl2OpenApiInterceptor();
        setFields(interceptor, "/api/v1.0", Map.of("get-city", "getCity"));

        assertEquals("getCity", interceptor.extractOperationName("/api/v1.0/get-city"));
    }

    static final Wsdl2OpenApiInterceptor interceptor = new Wsdl2OpenApiInterceptor();

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void camelToKebab(String input, String expected) {
        assertEquals(expected, interceptor.camelToKebab(input));
    }

    static Stream<Arguments> camelToKebab() {
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
