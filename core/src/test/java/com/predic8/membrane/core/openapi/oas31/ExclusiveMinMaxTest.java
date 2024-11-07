package com.predic8.membrane.core.openapi.oas31;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExclusiveMinMaxTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/exclusive-min-max.yaml")), null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> requestBodyProvider() {
        return Stream.of(
                Arguments.of("{\"value\": 9.99}", 1),
                Arguments.of("{\"value\": 10.0}", 1),
                Arguments.of("{\"value\": 10.01}", 0),
                Arguments.of("{\"value\": 99.99}", 0),
                Arguments.of("{\"value\": 100.0}", 1),
                Arguments.of("{\"value\": 100.01}", 1)
        );
    }

    @ParameterizedTest
    @MethodSource("requestBodyProvider")
    void testExclusiveMinMax(String requestBody, int expectedErrorSize) throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/range-check").body(requestBody).mediaType(APPLICATION_JSON)
        );

        System.out.println("errors = " + errors);
        assertEquals(expectedErrorSize, errors.size());
    }
}
