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

public class ExplodedArrayQueryParameterTest {

    private static final String OPENAPI_PATH = "/openapi/specs/oas31/parameters/simple.yaml";
    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(getApi(this, OPENAPI_PATH), new OpenAPISpec()));
    }

    @Nested
    class Valid {

        @Nested
        @DisplayName("Not allowing null values")
        class NotNull {

            static Stream<Arguments> valid() {
                return Stream.of(
                        arguments("no value", "/array"),
                        arguments("single", "/array?number=8"),
                        arguments("single negative", "/array?number=-1"),
                        arguments("single exp", "/array?number=3e7"),
                        arguments("numbers", "/array?number=1&number=2.2&number=3e4&number=-1&number=0"),
                        arguments("strings", "/array?string=a&string=bb&string=foo"),
                        arguments("booleans", "/array?bool=true&bool=false")
                );
            }

            @ParameterizedTest(name = "{index}: {0}")
            @MethodSource("valid")
            void zeroErrors(String name, String path) {
                assertTrue(validator.validate(get().path(path)).isEmpty(), () -> name + " should have 0 errors");
            }
        }

        @Nested
        @DisplayName("Allowing null values. type: [number,null]")
        class NullAllowed {
            static Stream<Arguments> valid() {
                return Stream.of(
                        arguments("single", "/array?number-and-null=8"),
                        arguments("single negative", "/array?number-and-null=-1"),
                        arguments("single exp", "/array?number-and-null=3e7"),

                        // Null and co
                        arguments("no=", "/array?number-and-null"),
                        arguments("empty value", "/array?number-and-null="),
                        arguments("single null", "/array?number-and-null=null"),
                        arguments("array of nulls", "/array?number-and-null=null&number-and-null=null&number-and-null=null")
                );
            }

            @ParameterizedTest(name = "{index}: {0}")
            @MethodSource("valid")
            void zeroErrors(String name, String path) {
                assertTrue(validator.validate(get().path(path)).isEmpty(), () -> name + " should have 0 errors");
            }
        }

        static Stream<Arguments> strings() {
            return Stream.of(

                    // List of size one
                    arguments("single", "/array?string=foo"),

                    // Null ...
                    arguments("empty number", "/array?string="),
                    arguments("null value number", "/array?string=null"),
                    arguments("array of nulls", "/array?string=null,null,null"),
                    arguments("mixed", "/array?string=foo,null,baz,,bak"),
                    arguments("no =", "/array?string"),

                    // Lists
                    arguments("multiple strings", "/array?string=blue,black,brown"),

                    // Booleans
                    arguments("multiple booleans", "/array?bool=true&bool=false&bool=true"),
                    arguments("const", "/array?const=baz&const=foo&const=bar")
            );
        }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("strings")
        void zeroErrorsForValidStrings(String caseName, String path) {
            assertEquals(0, validator.validate(get().path(path)).size(), () -> caseName + " should have 0 errors");
        }

    }

    @Test
    void valuesUTF8() {
        assertEquals(0, validator.validate(get().path("/array?const=foo&const=äöü&const=baz")).size());
    }

    @Test
    void valuesAreDecoded() {
        ValidationErrors err = validator.validate(get().path("/array?const=foo&const=%C3%A4%3D%23&const=baz"));
        assertEquals(0, err.size());
    }

    @Nested
    class Invalid {

        @Test
        void notNumber() {
            ValidationErrors err = validator.validate(get().path("/array?number=1&number=foo&number=3"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("does not match any of [number]"));
        }

        @Test
        void emptyStringNotNumber() {
            ValidationErrors err = validator.validate(get().path("/array?number=1&number=&number=3"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("does not match any of [number]"));
        }

        @Test
        void boolInvalid() {
            ValidationErrors err = validator.validate(get().path("/array?bool=foo"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("does not match any of"));
        }
    }

}
