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

public class SimpleReferenceTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/simple-reference.yaml")), null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> requestBodyProvider() {
        return Stream.of(
                Arguments.of("{\"id\": 1, \"email\": \"max@example.com\", \"createdAt\": \"2023-01-01T12:00:00Z\"}", 0),

                Arguments.of("{\"id\": 1}", 1),

                Arguments.of("{\"id\": 2, \"email\": \"invalid-email\", \"createdAt\": \"2023-01-01T12:00:00Z\"}", 1),

                Arguments.of("{\"id\": 3, \"email\": \"anna@example.com\", \"createdAt\": \"not-a-date-time\"}", 1),

                Arguments.of("{\"id\": \"foo\", \"email\": \"bar\", \"createdAt\": \"baz\"}", 3)
        );
    }

    @ParameterizedTest
    @MethodSource("requestBodyProvider")
    void testUserSchemaValidation(String requestBody, int expectedErrorSize) throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/users").body(requestBody).mediaType(APPLICATION_JSON)
        );

        System.out.println("errors = " + errors);
        assertEquals(expectedErrorSize, errors.size());
    }
}
