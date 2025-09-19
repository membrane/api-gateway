package com.predic8.membrane.core.openapi.oas31.parameters;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleNullTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/parameters/simple-null.yaml")), new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    @Nested
    class Explode {

        @Nested
        class Array {

            @Test
            void number() {
                ValidationErrors err = validator.validate(get().path("/array?number=1&number=2.2&number=null&number=3e4&number=-1&number=0"));
                assertEquals(0, err.size());
            }

            @Test
            void emptyValueIsInvalid() {
                ValidationErrors err = validator.validate(get().path("/array?number="));
                assertTrue(err.size() > 0, "Empty value should not satisfy number|null");
            }
        }
    }
}
