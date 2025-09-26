package com.predic8.membrane.core.openapi.oas31.parameters;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/parameters/simple.yaml")), new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    @Test
    void string() {
//        ValidationErrors errors = validator.validate(Request.get().path("/simple?empty=&string=blue&array=blue,black,brown&object=R=100,G=200,B=150"));
        //   ValidationErrors errors = validator.validate(Request.get().path("/simple?defau="));
//        System.out.println(errors);
//        assertEquals(0,errors.size());
    }

    @Test
    void twoValues() {
        ValidationErrors errors = validator.validate(get().path("/array?number=1&number=2"));
        System.out.println("errors = " + errors);
        assertEquals(0, errors.size());
    }

    @Test
    void parameterIsNotDescribed() {
        assertTrue(true); //TODO
    }


    @Nested
    class Explode {

        @Nested
        class Array {

            @Test
            void number() {
                assertEquals(0, validator.validate(get().path("/array?number=1&number=2.2&number=3e4&number=-1&number=0")).size());
            }

            @Test
            void string() {
                assertEquals(0, validator.validate(get().path("/array?string=a&string=bb&string=foo")).size());
            }

            @Test
            void bool() {
                assertEquals(0, validator.validate(get().path("/array?bool=true&bool=false")).size());
            }

            @Test
            void noQueryString() {
                assertEquals(0, validator.validate(get().path("/array")).size());
            }

            @Test
            void array() {}

            @Nested
            class Invalid {

                @Test
                void notNumber() {
                    ValidationErrors err = validator.validate(get().path("/array?number=1&number=foo&number=3"));
                    assertEquals(1, err.size());
                    assertTrue(err.get(0).getMessage().contains("does not match any of [number]"));
                }
            }


        }
    }
}
