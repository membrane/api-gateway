package com.predic8.membrane.core.openapi.oas31.parameters;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExplodeTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/parameters/explode.yaml")), new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    @Test
    void noValue() {
        ValidationErrors err = validator.validate(get().path("/search?filter="));
        assertEquals(0,err.size());
    }

    @Test
    void twoValues() {
        ValidationErrors err = validator.validate(get().path("/search?a=b&a=c"));
        assertEquals(0,err.size());
    }
}
