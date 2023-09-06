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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

class OpenAPIProxyServiceKeyTest {

    OpenAPIProxyServiceKey k1;

    @BeforeEach
    void setup() {
        k1 = new OpenAPIProxyServiceKey("","", 80);
    }

    @DisplayName("Access old path /api-doc")
    @ParameterizedTest
    @MethodSource("urls")
    void checkAcceptsPath(String url, boolean expected) {
        assertEquals(expected, k1.complexMatch("predic8.de","GET",url, "", 80, "192.168.0.1"));
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
