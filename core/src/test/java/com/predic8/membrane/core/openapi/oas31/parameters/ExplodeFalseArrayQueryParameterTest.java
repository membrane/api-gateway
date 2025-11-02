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

public class ExplodeFalseArrayQueryParameterTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        var apiRecord = new OpenAPIRecord(
                parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/parameters/explode-false.yaml")),
                new OpenAPISpec()
        );
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    @Nested
    class Valid {

        static Stream<Arguments> numbers() {
            return Stream.of(

                    // List of size one
                    arguments("single number", "/array?number=7"),
                    arguments("single negative number", "/array?number=-10"),

                    // Null ...
                    arguments("no query", "/array"),
                    arguments("empty number", "/array?number="),
                    arguments("array of nulls", "/array?number=null,null,null"),
                    arguments("null value number", "/array?number=null"),
                    arguments("no =", "/array?number"),

                    // Lists
                    arguments("multiple numbers", "/array?number=1,2,3")
            );
        }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("numbers")
        void zeroErrorsForValidNumbers(String caseName, String path) {
            assertEquals(0, validator.validate(get().path(path)).size(), () -> caseName + " should have 0 errors");
        }


        static Stream<Arguments> strings() {
            return Stream.of(

                    // List of size one
                    arguments("single", "/array?string=foo"),

                    // Null ...
                    arguments("no =", "/array?string"),
                    arguments("empty number", "/array?string="),
                    arguments("null value number", "/array?string=null"),
                    arguments("array of nulls", "/array?string=null&string=null&string=null"),
                    arguments("mixed", "/array?string=foo&string=&string&string=baz"),

                    // Lists
                    arguments("multiple strings", "/array?string=blue&string=black&string=brown"),

                    // Booleans
                    arguments("multiple booleans", "/array?bool=true,false,true"),

                    arguments("const", "/array?const=baz,foo,bar")
            );
        }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("strings")
        void zeroErrorsForValidStrings(String caseName, String path) {
            assertEquals(0, validator.validate(get().path(path)).size(), () -> caseName + " should have 0 errors");
        }


        @Test
        void rawQueryIsUsedToSplitParameters() {
            var err = validator.validate(get().path("/array?const=foo%2Cbar,baz"));
            assertEquals(1, err.size());
            var msg = err.get(0).getMessage();
            assertTrue(msg.contains("is not part of the enum"));
            assertTrue(msg.contains("'foo,bar'"));
        }

        @Test
        void valuesUTF8() {
            assertEquals(0, validator.validate(get().path("/array?const=foo,äöü,baz")).size());
        }

        @Test
        void valuesAreDecoded() {
            assertEquals(0, validator.validate(get().path("/array?const=foo,%C3%A4%3D%23,baz")).size());
        }

        @Test
        void numberArrayWithNullValue() {
            assertEquals(0, validator.validate(get().path("/array?number=1,null,3")).size());
        }
    }

    @Nested
    class Invalid {

        @Test
        void stringNotNumber() {
            var err = validator.validate(get().path("/array?number=1,foo,3"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("\"foo\" is of type string"));
        }

        @Test
        void emptyElement() {
            var err = validator.validate(get().path("/array?number=1,,3")); // ,, between is interpreted as empty string
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("\"\" is of type string"));
        }

        @Test
        void numberNotNull() {
            var err = validator.validate(get().path("/array?not-null=1,null,3"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("not match"));
            assertTrue(err.get(0).getMessage().contains("number"));
        }

        @Test
        void wrongCharacter() {
            var err = validator.validate(get().path("/array?string=blue,%GG"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("Invalid query string"));
        }

        @Test
        void wrongBoolean() {
            var err = validator.validate(get().path("/array?bool=true,wrong,false"));
            assertEquals(1, err.size());
            String msg = err.get(0).getMessage();
            assertTrue(msg.contains("wrong"));
            assertTrue(msg.contains("which does not match"));
            assertTrue(msg.contains("boolean"));
        }

        @Test
        void wrongConstant() {
            var err = validator.validate(get().path("/array?const=baz,wrong,bar"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("'wrong' is not part"));
        }
    }

}
