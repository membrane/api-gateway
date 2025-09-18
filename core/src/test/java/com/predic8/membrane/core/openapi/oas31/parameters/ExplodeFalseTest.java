package com.predic8.membrane.core.openapi.oas31.parameters;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExplodeFalseTest {

    OpenAPIValidator validator;

//    @BeforeEach
//    void setUp() {
//        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/parameters/explode-false.yaml")), new OpenAPISpec());
//        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
//    }

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
        class Array {

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
