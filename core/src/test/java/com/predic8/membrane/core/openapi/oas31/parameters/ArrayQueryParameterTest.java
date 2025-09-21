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

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ArrayQueryParameterTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp(TestInfo info) {
        OpenAPIRecord apiRecord = new OpenAPIRecord(
                parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/parameters/explode-false.yaml")),
                new OpenAPISpec()
        );
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
        System.out.println(info.getDisplayName() +
                           " validator@" + System.identityHashCode(validator));
    }

    @Nested
    class Explode {


        @Nested
        class Valid {

            @Test
            void empty() {
                assertEquals(0, validator.validate(get().path("/array?number=")).size());
            }

            @Test
            void nullValue() {
                ValidationErrors err = validator.validate(get().path("/array?number=null"));
                System.out.println("err = " + err);
                assertEquals(0, err.size());
            }

            @Test
            void one() {
                assertEquals(0, validator.validate(get().path("/array?number=-10")).size());
            }

            @Test
            void number() {
                ValidationErrors err = validator.validate(get().path("/array?number=1,2,3"));
                System.out.println("err = " + err);
                assertEquals(0, err.size());
            }

            @Test
            void string() {
                assertEquals(0, validator.validate(get().path("/array?string=blue,black,brown")).size());
            }

            @Test
            void bool() {
                assertEquals(0, validator.validate(get().path("/array?bool=true,false,true")).size());
            }

            @Nested
            class Invalid {

                @Test
                void number() {
                    ValidationErrors err = validator.validate(get().path("/array?number=1,foo,3"));
                    System.out.println("err = " + err);
                    assertEquals(1, err.size());

                    assertTrue(err.get(0).getMessage().contains("\"foo\" is of type string"));
                }

            }

        }
    }
}
