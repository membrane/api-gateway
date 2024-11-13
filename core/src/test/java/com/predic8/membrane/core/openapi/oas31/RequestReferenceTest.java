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

public class RequestReferenceTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/request-reference.yaml")), null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> createUserRequestProvider() {
        return Stream.of(
                Arguments.of("{\"email\": \"max@example.com\", \"createdAt\": \"2024-01-01T12:00:00Z\"}", 0),
                Arguments.of("{\"email\": \"max@example.com\", \"id\": 123, \"createdAt\": \"2024-01-01T12:00:00Z\"}", 0),
                Arguments.of("{}", 1),
                Arguments.of("{\"email\": \"invalid-email\"}", 1),
                Arguments.of("{\"email\": \"max@example.com\", \"createdAt\": \"not-a-datetime\"}", 1),
                Arguments.of("{\"id\": 123}", 1),
                Arguments.of("{\"email\": \"max@example.com\", \"id\": \"not-a-number\"}", 1)
        );
    }

    @ParameterizedTest
    @MethodSource("createUserRequestProvider")
    void testUserCreationRequestValidation(String requestBody, int expectedErrorSize) throws ParseException {
         ValidationErrors errors = validator.validate(
                Request.post().path("/users").body(requestBody).mediaType(APPLICATION_JSON)
        );

        System.out.println("errors = " + errors);

        assertEquals(expectedErrorSize, errors.size());
    }
}