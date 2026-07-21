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

import java.lang.reflect.Field;
import java.util.Map;

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
}
