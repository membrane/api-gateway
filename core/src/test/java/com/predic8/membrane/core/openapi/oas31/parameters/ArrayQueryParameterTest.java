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

package com.predic8.membrane.core.openapi.oas31.parameters;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

public class ArrayQueryParameterTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp(TestInfo info) {
        OpenAPIRecord apiRecord = new OpenAPIRecord(
                parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/parameters/explode-false.yaml")),
                new OpenAPISpec()
        );
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    @Nested
    class Explode {

        @Nested
        class Valid {

            static Stream<Arguments> valid() {
                return Stream.of(
                        arguments("no query", "/array"),
                        arguments("empty number", "/array?number="),
                        arguments("null value number", "/array?number=null"),
                        arguments("single negative number", "/array?number=-10"),
                        arguments("multiple numbers", "/array?number=1,2,3"),
                        arguments("multiple strings", "/array?string=blue,black,brown"),
                        arguments("multiple strings with null", "/array?string=blue,black,brown"),
                        arguments("multiple booleans", "/array?bool=true,false,true")
                );
            }

            @ParameterizedTest(name = "{index}: {0}")
            @MethodSource("valid")
            void zeroErrorsForValidInputs(String caseName, String path) {
                assertEquals(0, validator.validate(get().path(path)).size(), () -> caseName + " should have 0 errors");
            }

            @Nested
            class Invalid {

                @Test
                void number() {
                    ValidationErrors err = validator.validate(get().path("/array?number=1,foo,3"));
                    assertEquals(1, err.size());
                    assertTrue(err.get(0).getMessage().contains("\"foo\" is of type string"));
                }

                @Test
                void nullValue() {
                    ValidationErrors err = validator.validate(get().path("/array?string=blue,null,brown"));
                    assertEquals(1, err.size());
                    assertTrue(err.get(0).getMessage().contains("null is of type"));
                }
            }
        }
    }
}
